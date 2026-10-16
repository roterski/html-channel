# Plan: `html-channel`, a library for interactive HTML review pages for LLM output

Status: ready to start · New repo: `github.com/roterski/html-channel` · Consumer: https://github.com/roterski/ai-behaviors

This file is self-contained. It records why the work is being done, the design decisions
already made, a working prototype, and the steps to build the library. You don't need the
conversation that produced it.

---

## 1. Goal

Stop using `.md` files as the presentation layer when a human reviews LLM-generated code.
The LLM writes **HTML pages** instead. Those pages are **interactive**: buttons record
decisions, and inputs collect notes. The answers come back to the agent.

The result is **one standalone library repo**, consumed in three ways:

| Consumer | Who triggers it | Where |
|---|---|---|
| **Skill** | The agent decides on its own that it needs your input | Claude Code, Claude desktop app, claude.ai (uploaded skill) |
| **MCP server** (`html-channel mcp`) | The agent calls `ask_human`, which blocks until you click Done | Claude Code, ECA, Claude desktop app |
| **ai-behaviors tag `#html`** | You, explicitly, per prompt | Claude Code, ECA, via the ai-behaviors hook |

Constraints:
- Local use is localhost only, for a single user. There is no auth, and the server binds to `127.0.0.1` only.
- Page scripting is in **squint** (https://github.com/squint-cljs/squint). Server and CLI are in **babashka**.
- Pages contain **no page-specific JavaScript**. The LLM writes only HTML with `data-*` markers.
  One generic runtime handles everything.

## 2. Decisions already made

| Question | Decision | Why |
|---|---|---|
| Where does the code live? | In its own repo. ai-behaviors only *uses* it. | The library serves three consumers, and ai-behaviors is just one of them. |
| Single source of truth | `CONVENTION.md` in the lib: the HTML markers, the banner, and the entry format. The skill, the MCP tool descriptions and the tag all **point to it or derive from it**. None of them restates it. | The three consumers can't drift apart. |
| Convention vs transport | The squint runtime has one event/queue/restore core and **pluggable transports**: `http` (localhost server) and `artifact` (claude.ai artifact storage). | claude.ai can't reach localhost. It runs in Anthropic's cloud, and so do cloud Claude Code sessions. |
| claude.ai support | **Yes, via the artifact transport.** The page is published as a claude.ai artifact with stored state, and the agent reads that state back after you say "done". It is not blocking. | Local stdio MCP is impossible on claude.ai web. Remote MCP needs a public HTTPS URL, and tunnelling breaks the "localhost, no auth" premise. |
| Server needed locally? | Yes, a tiny bb http-kit server. | Decisions must reach disk without manual export. A server also avoids `file://` ES-module restrictions. |
| How does a page know the server is down? | The offline banner is **visible by default** in the HTML. The runtime script is **loaded from the server** and hides the banner. It then polls `/_health` every 3 s. | If the server is down, the script can't load, so the banner stays. No inline JS is needed. |
| Decisions made while the server is down | Queued in memory, marked `data-state="pending"`, and flushed on reconnect | No lost clicks. |
| Storage | Append-only `decisions.jsonl` in the served root. The last entry per `page`+`id` wins. | It gives a history, it's greppable, and it's readable by agents and by the MCP process. |
| How the MCP `ask_human` tool waits | It **polls `decisions.jsonl`** for `{page, id:"status", value:"done"}`. It does not rely on in-memory promises. | It works no matter which process currently owns the HTTP server, for example when several sessions are open. |
| MCP process and HTTP server | `html-channel mcp` speaks MCP over stdio. On every tool call it *ensures* the HTTP server is up, starting it in-process if nobody answers on the port. | The client launches the MCP process automatically, which removes "remember to start the server". |
| Distribution | `bbin install io.github.roterski/html-channel` gives a `html-channel` command. Static runtime files ship as **classpath resources**. | Works from any cwd. Daily use needs only `bb` and no Node. |
| squint core dependency | `bb build` rewrites the `'squint-cljs/core.js'` import to `./squint-core.js` and copies the core file (a single file with no imports) next to it. | No CDN or import map is needed locally. |
| ai-behaviors integration | `behaviors/html/prompt.md` is 2–4 lines that defer to the lib ("use html-channel: call `ask_human`, or follow its skill"). ai-behaviors' `./install` runs `bbin install` for the lib. | "Importing the lib" happens at install time. There's no code dependency, because the tag is just text. |

## 3. Reference implementation: prototype (verified working)

This is the **single-file prototype** that the library starts from. It is the http transport, the server and the runtime,
before the restructuring described in §5. It was built and tested end to end with Playwright and Chromium, using babashka v1.13.226 and squint-cljs 0.15.212.
Scenarios that passed:
- A: page opened via `file://` with the server down → banner visible.
- C: page opened via `file://` with the server up → banner hidden, and a click is saved (CORS works).
- B: page served over http → previous decisions restored on load. Button, checkbox and textarea values are all saved, and `data-state` becomes `saved`.
- D: server killed mid-session → banner reappears within about 3 s. A click while down shows `pending`. After a restart, the banner hides, the queued decision is flushed, and its state becomes `saved`.
- Path traversal (`/../serve.clj` and `/%2e%2e/serve.clj`) returns 404. A second `bb serve.clj` prints "already running" and exits 0.

Recreate these files verbatim in the new repo as the first commit (phase 0), then restructure.

### 3.1 `serve.clj`

```clojure
#!/usr/bin/env bb
;; Tiny local decision server for interactive review pages.
;;
;;   bb serve.clj                       ; serve ./ on 127.0.0.1:8787
;;   bb serve.clj --root docs/reviews   ; serve another folder
;;   bb serve.clj --port 9000 --out my-decisions.jsonl
;;
;; Idempotent: if a decision server is already answering on the port, it
;; says so and exits 0, so an agent can run it blindly.
;;
;; Endpoints
;;   GET  /_health                 -> {"ok":true,"root":...,"out":...}
;;   GET  /_decisions?page=<id>    -> {"<decision-id>": <latest entry>, ...}
;;   POST /_decisions              -> append one JSON entry to the out file
;;   GET  /_kit/<file>             -> runtime files (decide.mjs, css, squint core)
;;   GET  /                        -> list of .html pages under root
;;   GET  /<path>                  -> static files from root

(ns serve
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.http-client :as hc]
            [cheshire.core :as json]
            [clojure.string :as str]
            [org.httpkit.server :as http]))

(def opts
  (cli/parse-opts *command-line-args*
                  {:coerce {:port :long}
                   :alias {:p :port :r :root :o :out}}))

(def port (:port opts 8787))
(def root (fs/canonicalize (:root opts ".")))
(def out-file (fs/path root (:out opts "decisions.jsonl")))
(def kit-dir (fs/canonicalize (fs/path (fs/parent *file*) "kit")))

(def mime
  {"html" "text/html; charset=utf-8" "htm" "text/html; charset=utf-8"
   "js" "text/javascript" "mjs" "text/javascript" "css" "text/css"
   "json" "application/json" "jsonl" "application/x-ndjson"
   "svg" "image/svg+xml" "png" "image/png" "jpg" "image/jpeg"
   "md" "text/markdown; charset=utf-8" "txt" "text/plain; charset=utf-8"})

(def base-headers
  {"Access-Control-Allow-Origin" "*"        ; lets file:// pages talk to us
   "Access-Control-Allow-Methods" "GET, POST, OPTIONS"
   "Access-Control-Allow-Headers" "Content-Type"
   "Access-Control-Allow-Private-Network" "true"
   "Cache-Control" "no-store"})             ; regenerated pages always fresh

(defn respond
  ([status] (respond status nil nil))
  ([status content-type body]
   {:status status
    :headers (cond-> base-headers content-type (assoc "Content-Type" content-type))
    :body body}))

(defn json-resp [data] (respond 200 "application/json" (json/generate-string data)))

(defn query-param [query-string k]
  (some (fn [kv]
          (let [[a b] (str/split kv #"=" 2)]
            (when (= a k) (java.net.URLDecoder/decode (or b "") "UTF-8"))))
        (some-> query-string (str/split #"&"))))

(defn read-entries []
  (when (fs/exists? out-file)
    (keep #(try (json/parse-string % true) (catch Exception _ nil))
          (fs/read-all-lines out-file))))

(defn latest-for-page [page]
  (reduce (fn [m e] (if (= page (:page e)) (assoc m (:id e) e) m))
          {} (read-entries)))

(def write-lock (Object.))

(defn append-entry! [body]
  (let [entry (-> (json/parse-string body true)
                  (update :at #(or % (str (java.time.Instant/now)))))]
    (when-not (and (string? (:page entry)) (string? (:id entry)))
      (throw (ex-info "entry needs string :page and :id" {})))
    (locking write-lock
      (spit (fs/file out-file) (str (json/generate-string entry) "\n") :append true))
    entry))

(defn file-under [dir rel]
  (let [f (fs/canonicalize (fs/path dir rel))]
    (when (and (str/starts-with? (str f) (str dir)) (fs/regular-file? f)) f)))

(defn serve-file [f]
  (respond 200 (get mime (fs/extension f) "application/octet-stream") (fs/file f)))

(defn index-page []
  (let [pages (->> (fs/glob root "**.{html,htm}")
                   (map #(str (fs/relativize root %)))
                   (remove #(str/starts-with? % "node_modules"))
                   sort)]
    (respond 200 "text/html; charset=utf-8"
             (str "<!doctype html><meta charset=utf-8><title>Review pages</title>"
                  "<body style='font:15px system-ui;max-width:40rem;margin:3rem auto'>"
                  "<h1>Review pages</h1><p><code>" root "</code></p><ul>"
                  (apply str (for [p pages] (str "<li><a href='/" p "'>" p "</a>")))
                  "</ul>"))))

(defn handler [{:keys [request-method uri query-string body]}]
  (let [uri (java.net.URLDecoder/decode uri "UTF-8")]
    (try
      (cond
        (= request-method :options) (respond 204)

        (= uri "/_health")
        (json-resp {:ok true :root (str root) :out (str out-file)})

        (and (= uri "/_decisions") (= request-method :get))
        (json-resp (latest-for-page (query-param query-string "page")))

        (and (= uri "/_decisions") (= request-method :post))
        (let [e (append-entry! (slurp body))]
          (println "decision" (:page e) (:id e) "=>" (pr-str (:value e)))
          (json-resp {:ok true}))

        (str/starts-with? uri "/_kit/")
        (if-let [f (file-under kit-dir (subs uri 6))] (serve-file f) (respond 404))

        (= uri "/") (index-page)

        :else
        (if-let [f (file-under root (subs uri 1))] (serve-file f) (respond 404)))
      (catch Exception e
        (respond 400 "text/plain" (ex-message e))))))

(defn already-running? []
  (try (= 200 (:status (hc/get (str "http://127.0.0.1:" port "/_health")
                               {:timeout 500 :throw false})))
       (catch Exception _ false)))

(if (already-running?)
  (println (str "Decision server already running on http://127.0.0.1:" port))
  (do
    (http/run-server handler {:ip "127.0.0.1" :port port})
    (println (str "Serving " root " on http://127.0.0.1:" port))
    (println (str "Decisions -> " out-file))
    @(promise)))
```

### 3.2 `src/decide.cljs` (squint runtime)

```clojure
(ns decide
  "Generic runtime for interactive review pages.

  Conventions in the HTML (no page-specific JS needed):
    <body data-page=\"auth-review\">       page id (defaults to file name)
    <button data-decision=\"x\" data-choice=\"a\">   pick one choice per decision id
    <input|textarea|select data-decision=\"y\">     free-form value, saved on change
    <input type=checkbox data-decision=\"z\">       saved as true/false
    <div id=\"decide-offline\">...</div>            shown while the server is down

  Elements get data-state=\"pending\" until the server confirms, then \"saved\".")

(def server
  (let [u (js/URL. (.-url js/import.meta))] (.-origin u)))

(def page-id
  (or (.. js/document -body -dataset -page)
      (-> js/location .-pathname (.split "/") .pop (.replace #"\.html?$" ""))))

(def state (atom {:online nil :queue [] :flushing false}))

(defn els-for [id]
  (js/document.querySelectorAll (str "[data-decision=\"" (js/CSS.escape id) "\"]")))

(defn mark! [id s]
  (doseq [el (els-for id)] (set! (.. el -dataset -state) s)))

(defn set-online! [online?]
  (when (not= online? (:online @state))
    (swap! state assoc :online online?)
    (when-let [b (js/document.getElementById "decide-offline")]
      (set! (.-hidden b) online?))
    (.toggle (.-classList js/document.documentElement) "decide-is-offline" (not online?))))

(defn ^:async post! [entry]
  ;; no Content-Type header => "simple" CORS request, works from file:// too
  (try
    (let [r (js-await (js/fetch (str server "/_decisions")
                                {:method "POST" :body (js/JSON.stringify entry)}))]
      (.-ok r))
    (catch :default _ false)))

(defn ^:async flush! []
  (when-not (:flushing @state)
    (swap! state assoc :flushing true)
    (loop []
      (when-let [entry (first (:queue @state))]
        (if (js-await (post! entry))
          (do (swap! state update :queue #(vec (rest %)))
              (when-not (some #(= (:id %) (:id entry)) (:queue @state))
                (mark! (:id entry) "saved"))
              (recur))
          (set-online! false))))
    (swap! state assoc :flushing false)))

(defn record! [id value]
  (swap! state update :queue conj
         {:page page-id :id id :value value :at (.toISOString (js/Date.))})
  (mark! id "pending")
  (flush!))

;; ---- reading / writing element values ------------------------------------

(defn el-value [el]
  (if (= "checkbox" (.-type el)) (.-checked el) (.-value el)))

(defn restore! [decisions]
  (doseq [[id entry] (js/Object.entries decisions)]
    (let [v (:value entry)]
      (doseq [el (els-for id)]
        (set! (.. el -dataset -state) "saved")
        (cond
          (= "BUTTON" (.-tagName el))
          (.toggle (.-classList el) "chosen" (= v (.. el -dataset -choice)))

          (= "checkbox" (.-type el)) (set! (.-checked el) (boolean v))
          (= "radio" (.-type el)) (set! (.-checked el) (= v (.-value el)))
          :else (set! (.-value el) v))))))

;; ---- events --------------------------------------------------------------

(.addEventListener js/document "click"
  (fn [e]
    (when-let [btn (.closest (.-target e) "button[data-decision][data-choice]")]
      (let [id (.. btn -dataset -decision)]
        (doseq [b (els-for id)] (.remove (.-classList b) "chosen"))
        (.add (.-classList btn) "chosen")
        (record! id (.. btn -dataset -choice))))))

(.addEventListener js/document "change"
  (fn [e]
    (let [el (.-target e)]
      (when (and (.. el -dataset -decision) (not= "BUTTON" (.-tagName el)))
        (record! (.. el -dataset -decision) (el-value el))))))

;; ---- health polling ------------------------------------------------------

(defn ^:async check! []
  (let [ok (try (.-ok (js-await (js/fetch (str server "/_health") {:cache "no-store"})))
                (catch :default _ false))]
    (set-online! ok)
    (when (and ok (seq (:queue @state))) (flush!))
    ok))

(defn ^:async init! []
  (when (js-await (check!))
    (try
      (let [r (js-await (js/fetch (str server "/_decisions?page=" (js/encodeURIComponent page-id))))]
        (restore! (js-await (.json r))))
      (catch :default e (js/console.warn "decide: could not restore" e))))
  (js/setInterval check! 3000))

(init!)
```

### 3.3 `kit/decide.css`

```css
/* Minimal styling for decision controls. Pages can override freely. */
button[data-decision] {
  font: inherit; padding: .4rem .9rem; border-radius: 6px; cursor: pointer;
  border: 1px solid #b9bcc4; background: #fff; color: #1d2026;
}
button[data-decision]:hover { border-color: #5a6170; }
button[data-decision].chosen { background: #1d4ed8; border-color: #1d4ed8; color: #fff; }

[data-decision][data-state="pending"] { outline: 2px dashed #d97706; outline-offset: 2px; }
textarea[data-decision][data-state="saved"],
input[data-decision][data-state="saved"] { outline: 2px solid #16a34a33; outline-offset: 1px; }

html.decide-is-offline [data-decision] { opacity: .75; }

@media (prefers-color-scheme: dark) {
  button[data-decision] { background: #23262d; color: #e6e8ec; border-color: #4a4f5a; }
  button[data-decision].chosen { background: #3b82f6; border-color: #3b82f6; color: #fff; }
}
```

### 3.4 `bb.edn`

```clojure
{:tasks
 {serve {:doc "Start the decision server (args pass through, e.g. --root docs)"
         :task (apply shell "bb serve.clj" *command-line-args*)}

  build {:doc "Compile src/decide.cljs -> kit/decide.mjs (needs `npm i` once)"
         :requires ([babashka.fs :as fs]
                    [clojure.string :as str])
         :task (do
                 (shell "npx squint compile src/decide.cljs --output-dir build")
                 (fs/create-dirs "kit")
                 ;; make the module self-contained: point at a local copy of squint core
                 (spit "kit/decide.mjs"
                       (str/replace (slurp "build/src/decide.mjs")
                                    "'squint-cljs/core.js'" "'./squint-core.js'"))
                 (fs/copy "node_modules/squint-cljs/src/squint/core.js" "kit/squint-core.js"
                          {:replace-existing true})
                 (println "built kit/decide.mjs"))}}}
```

### 3.5 `package.json` (needed only for `bb build`)

```json
{ "private": true, "devDependencies": { "squint-cljs": "^0.15.212" } }
```

`kit/decide.mjs` and `kit/squint-core.js` are **build outputs** of `bb build`. Commit them so users
need only `bb`. `.gitignore`: `node_modules/`, `build/`, `decisions.jsonl`.

### 3.6 `examples/demo.html`

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Review: cache layer</title>
<link rel="stylesheet" href="http://127.0.0.1:8787/_kit/decide.css">
<style>
  body { font: 16px/1.55 system-ui, sans-serif; max-width: 46rem; margin: 2.5rem auto; padding: 0 1rem; }
  section { border-top: 1px solid #ddd; padding: 1rem 0; }
  .choices { display: flex; gap: .5rem; flex-wrap: wrap; margin: .5rem 0; }
  textarea { width: 100%; min-height: 4rem; font: inherit; }
  pre { background: #f4f5f7; padding: .75rem; overflow-x: auto; }
</style>
</head>
<body data-page="cache-layer-review">

<!-- Visible by default; the runtime hides it once the server answers.
     If the server is down, the runtime never loads, so this stays up. -->
<div id="decide-offline" style="position:sticky;top:0;background:#fef3c7;color:#78350f;border:1px solid #f59e0b;padding:.6rem .9rem;border-radius:6px;margin-bottom:1rem;font:14px system-ui">
  <strong>Decision server is not running</strong> — your choices won't be saved.
  Start it with <code>bb serve.clj --root examples</code> (from the html-channel folder), then this banner disappears on its own.
</div>

<h1>Cache layer review</h1>

<section>
  <h2>1. Eviction strategy</h2>
  <p>The agent proposed two options for <code>SessionCache</code>.</p>
  <pre>(defn evict! [cache] ...)</pre>
  <div class="choices">
    <button data-decision="eviction" data-choice="lru">LRU</button>
    <button data-decision="eviction" data-choice="ttl">TTL (15 min)</button>
    <button data-decision="eviction" data-choice="neither">Neither, discuss</button>
  </div>
</section>

<section>
  <h2>2. Keep the metrics hook?</h2>
  <label><input type="checkbox" data-decision="keep-metrics"> Keep it</label>
</section>

<section>
  <h2>3. Notes for the agent</h2>
  <textarea data-decision="notes" placeholder="Anything else…"></textarea>
</section>

<section>
  <div class="choices">
    <button data-decision="status" data-choice="done">Done reviewing</button>
  </div>
</section>

<script type="module" src="http://127.0.0.1:8787/_kit/decide.mjs"></script>
</body>
</html>
```

### 3.7 Decision entry format

```json
{"page":"cache-layer-review","id":"eviction","value":"lru","at":"2026-10-16T13:08:34.844Z"}
```

`value` is the button's `data-choice`, the field's text, or `true`/`false` for a checkbox.

## 4. Target repo layout

```
html-channel/
  CONVENTION.md                 # THE protocol: markers, banner, entry format, page rules
  README.md
  bb.edn                        # :paths, :bbin/bin {html-channel …}, tasks: build, test, dev
  deps.edn                      # (only if bbin/classpath needs it)
  src/html_channel/
    cli.clj                     # `html-channel serve|mcp|decisions|health|version`
    server.clj                  # http-kit handler (from prototype serve.clj), multi-root aware
    store.clj                   # read/append decisions.jsonl, latest-per-id, wait-for-done
    mcp.clj                     # stdio JSON-RPC: initialize, tools/list, tools/call
  runtime/                      # squint sources
    decide/core.cljs            # events, queue, restore, banner (from prototype decide.cljs)
    decide/http.cljs            # transport: fetch to the origin it was loaded from
    decide/artifact.cljs        # transport: claude.ai artifact storage (phase 5)
  resources/html_channel/kit/     # BUILD OUTPUT, committed: decide.mjs, squint-core.js, decide.css
  dist/artifact/                # BUILD OUTPUT: single-file inlined runtime for claude.ai
  skill/html-channel/SKILL.md     # the skill (Claude Code / desktop / claude.ai upload)
  plugin/                       # optional Claude Code plugin: skill + MCP server config in one install
  examples/demo.html
  test/e2e.py                   # Playwright scenarios A–D (+ new ones)
  package.json                  # squint-cljs devDependency, for `bb build` only
```

## 5. Work plan

### Phase 0: new repo from the prototype
1. Create the repo and commit the §3 files unchanged.
   Run `bb build` and the e2e test from §7 to confirm scenarios A–D pass. This is the baseline.

### Phase 1: protocol and CLI as an installable lib
2. Write **`CONVENTION.md`**, the protocol for pages. Every consumer links to it. It covers:
   - file location (`<project>/.ai-reviews/<slug>.html`)
   - `<body data-page>`
   - the `#decide-offline` banner (visible by default, with an inline style and the start command `html-channel serve`)
   - the `<link>` and `<script>` tags
   - `button[data-decision][data-choice]`, `textarea`/`input`/`select[data-decision]`, and checkbox → boolean
   - a mandatory final `status=done` button
   - the entry format `{page,id,value,at}`
   - "last entry per page+id wins"
   - the rule "no other JS on the page"
3. Split `serve.clj` into `server.clj`, `store.clj` and `cli.clj`.
   - Serve `/_kit/*` from the **classpath** (`io/resource "html_channel/kit/…"`) instead of a dir next to `*file*`. This is required once bbin installs from git.
   - Log to **stderr**. Stdout must stay clean for MCP.
4. CLI subcommands:
   - `serve [--root .ai-reviews] [--port 8787]`
   - `mcp`
   - `decisions <page> [--root]`: latest values as JSON, for agents without MCP
   - `health`
5. `bb.edn` `:bbin/bin {html-channel {:main-opts ["-m" "html-channel.cli"]}}`. Verify `bbin install .` locally and `bbin install io.github.roterski/html-channel` from git, run from an unrelated cwd.
6. Move the build task. It now writes into `resources/html_channel/kit/`. Commit the outputs.

### Phase 2: runtime transport split (still http only)
7. Refactor `decide.cljs` into `core` and `http`:
   - `core` takes a transport map: `{:send! (fn [entry] → Promise<bool>) :load (fn [page] → Promise<map>) :health (fn [] → Promise<bool>)}`.
   - The http transport is the current `fetch` code. It derives the server origin from `import.meta.url`.
   - Keep the bundled entry point `decide.mjs` = core + http, so pages are unchanged.
8. Re-run e2e A–D.

### Phase 3: MCP server
9. `html-channel mcp`: a minimal MCP stdio server (JSON-RPC 2.0, newline-delimited) with `initialize`, `tools/list` and `tools/call`.
   The root defaults to `<cwd>/.ai-reviews`, because the client starts the process in the project dir. Tools:
   - `review_setup {}` → `{dir, url_base, convention}`. Returns where to write pages and the full text of `CONVENTION.md`, so the agent doesn't need the skill to know the rules.
   - `ask_human {page, timeout_s=1800}` ensures the server is up, returns immediately if `status=done` already exists, and otherwise
     polls `decisions.jsonl` every 500 ms. It returns `{status, decisions}`, or `{status:"timeout", decisions}`.
     Long blocking calls may hit client tool timeouts. Check Claude Code's and ECA's limits, and if needed make it return
     `{status:"waiting"}` after N minutes so the agent calls it again.
   - `get_decisions {page}` is non-blocking.
10. Multi-project handling. Several MCP processes means several roots and one port. See §6 for the decision. Minimum viable version:
    the server keeps a `roots` map and serves each at `/<mount>/`, with mount = project dir name plus a short hash. `ask_human` and `review_setup`
    register their root through `POST /_roots` (idempotent) when another process owns the server. The runtime takes the mount
    from the page URL path, and the server writes to that root's `decisions.jsonl`. The single-root `html-channel serve` keeps working (mount `/`).
11. Ship the client configs:
    - `.mcp.json` snippet for Claude Code: `{"mcpServers":{"html-channel":{"command":"html-channel","args":["mcp"]}}}`
    - the equivalent for ECA
    - the equivalent for Claude desktop (`claude_desktop_config.json`)
12. Tests: drive the stdio protocol from a script by sending `initialize`, `tools/list` and `ask_human`. While it waits, click Done with
    Playwright and assert the tool result.

### Phase 4: skill and ai-behaviors tag
13. `skill/html-channel/SKILL.md`. Its frontmatter `description` should trigger on "needs a decision/approval/choice from the user,
    reviewing generated code, presenting options". The body branches on environment, in order:
    1. An `ask_human` / `review_setup` tool is available → call `review_setup`, write the page, then call `ask_human`.
    2. Else a shell is available and `html-channel health` succeeds, or `html-channel` is on PATH → `html-channel serve --root .ai-reviews &`,
       write the page, give the user the URL, and read the result with `html-channel decisions <page>` after they say done.
    3. Else an artifact-publishing tool is available (claude.ai) → the artifact transport (phase 5).
    4. Else fall back to markdown and say why.
    
    Bundle `CONVENTION.md` next to it as a reference file so the skill is self-contained when uploaded.
14. Optional: a Claude Code **plugin** (`plugin/`) that packages the skill and the MCP server config as one install.
    Check the current plugin format in the Claude Code docs before building.
15. In the ai-behaviors fork, add `behaviors/html/` in the same shape as `behaviors/file/`:
    - Read `behaviors/file/`, `hooks/inject-behaviors.sh`, `install` and `src/ai_behaviors/core.clj` first, to match conventions.
    - `prompt.md` is about 3 lines: present anything needing my input as a html-channel page; prefer the `ask_human` tool; otherwise follow
      `html-channel` CONVENTION (`html-channel` CLI). Don't act until `status=done`.
    - Have `install` and `eca-install` run `bbin install io.github.roterski/html-channel` when `bbin` exists, else print the command.
      Optionally register the MCP server too.
    - Decide how it interacts with `#file` (probably mutually exclusive). Update the README list and the tests.

### Phase 5: claude.ai artifact transport
16. Inside a claude.ai session, read that environment's artifact-capabilities documentation. The exact API for stored state
    and for the agent reading it back is defined there, so don't guess it now. Then implement `decide/artifact.cljs`
    with the same `{:send! :load :health}` shape. `health` is always true, and there's no banner.
17. Build `dist/artifact/decide.inline.js`, a single file with squint core included.
    - Artifacts load external scripts only from a few CDNs (`cdn.jsdelivr.net/npm/` among them), so either inline
      the runtime into the page or publish it to npm as `html-channel-runtime`.
    - Inlining is simpler. The skill's claude.ai branch tells the agent to paste it into a `<script type="module">`.
18. Skill branch 3:
    - publish the page as an artifact with the state capability
    - tell the user to click through and say "done"
    - read the stored decisions with the environment's artifact data tool
    - same entry format.
19. Upload the skill zip to claude.ai and test with a real review.

## 6. Open questions

- **Multi-root vs one server per project.** §5 step 10 proposes mounts. A simpler alternative is a port per project, derived
  from a hash of the root. Pages would then need the port templated in, which `review_setup` can return. Pick one before phase 3.
  Mounts keep one well-known port; per-project ports keep the server simpler.
- **`file://` pages** work only in single-root mode, because the runtime can't learn the mount. Either drop `file://` support or
  add an optional `<body data-mount>`. Chrome's local-network-access rules for `file://`→localhost are also untested in desktop
  Chrome. The server already sends `Access-Control-Allow-Private-Network: true`.
- **MCP tool-call timeouts** for long `ask_human` waits. See §5 step 9.
- **Name.** `html-channel` is decided. Check that the npm name is free if you publish the runtime (phase 5); fall back to `@roterski/html-channel`.
- **Port.** 8787 is hardcoded in pages. Keep it unless there's a conflict.

## 7. How to test

Manual check:
```sh
bb serve.clj --root examples
# open http://127.0.0.1:8787/demo.html, click, then:
cat examples/decisions.jsonl
```

Automated end-to-end test (Python Playwright with Chromium). Put this in `test/e2e.py`; it covers scenarios A–D from §3:

```python
import subprocess, time, json, pathlib
from playwright.sync_api import sync_playwright
# run from the html-channel folder: python3 e2e.py
KIT = pathlib.Path(".").resolve(); OUT = KIT/"examples/decisions.jsonl"
SRV=[]
def start():
    p = subprocess.Popen(["bb","serve.clj","--root","examples"],cwd=KIT,stdout=subprocess.DEVNULL); time.sleep(1.5); SRV.append(p); return p
def stop():
    while SRV: p=SRV.pop(); p.terminate(); p.wait()
    time.sleep(0.5)
stop(); OUT.unlink(missing_ok=True)
with sync_playwright() as pw:
    b = pw.chromium.launch(); pg = b.new_page()
    errs=[]; pg.on("console", lambda m: m.type=="error" and errs.append(m.text))
    # A: server down, file://
    pg.goto(f"file://{KIT}/examples/demo.html"); time.sleep(1)
    print("A file:// + server down  -> banner visible:", pg.is_visible("#decide-offline"))
    # C: server up, file:// -> works via CORS
    srv = start(); pg.reload(); time.sleep(1.5)
    print("C file:// + server up    -> banner visible:", pg.is_visible("#decide-offline"))
    pg.click("button[data-choice=ttl]"); time.sleep(0.5)
    # B: served over http
    pg.goto("http://127.0.0.1:8787/demo.html"); time.sleep(1)
    print("B http restore           -> ttl chosen:", pg.get_attribute("button[data-choice=ttl]","class"))
    pg.click("button[data-choice=lru]"); pg.check("[data-decision=keep-metrics]")
    pg.fill("[data-decision=notes]","rename evict! to evict"); pg.click("h1"); time.sleep(0.7)
    print("   state after save      ->", pg.get_attribute("[data-decision=notes]","data-state"))
    # D: server dies mid-session
    stop(); time.sleep(3.5)
    print("D server killed          -> banner visible:", pg.is_visible("#decide-offline"))
    pg.click("button[data-choice=done]"); time.sleep(0.5)
    print("   click while down      -> state:", pg.get_attribute("button[data-choice=done]","data-state"))
    srv = start(); time.sleep(3.5)
    print("   server back           -> banner visible:", pg.is_visible("#decide-offline"),
          "| state:", pg.get_attribute("button[data-choice=done]","data-state"))
        print("console errors:", [e for e in errs if "ERR_CONNECTION_REFUSED" not in e and "Failed to load" not in e])
    b.close()
stop()
print(OUT.read_text())
```

## 8. Gotchas already hit

- `squint compile src/decide.cljs --output-dir build` writes to `build/src/decide.mjs`, not `build/decide.mjs`.
- Module scripts need a correct `Content-Type: text/javascript`, which is why the server has a MIME map. Browsers enforce strict MIME checking for modules.
- `import.meta.url` works in squint as `(.-url js/import.meta)`. The runtime derives the server origin from it, so the port lives only in the page's `<script src>`.
- `js-await` inside `loop`/`recur` is fine in squint, because the loop compiles to a JS `while` inside the async function.
- In tests, don't kill the server with `pkill -f '<pattern>'` run from a shell whose own command line contains the pattern. It kills the shell itself. Keep the `Popen` handle and call `terminate()`.
- MCP over stdio: **anything printed to stdout that isn't JSON-RPC breaks the client**. The prototype `println`s request logs to stdout, so move all logging to stderr (`binding [*out* *err*]`) before adding `mcp`.
- With bbin installing from git, the code runs from a gitlib checkout. Paths relative to `*file*` happen to still work there, but classpath resources are the robust choice, and they're what step 3 uses.

## 9. Definition of done

- [ ] Phase 0: the prototype in the new repo passes e2e A–D.
- [ ] `bbin install io.github.roterski/html-channel` gives a working `html-channel serve|mcp|decisions|health` from any cwd, with no Node at runtime.
- [ ] `CONVENTION.md` exists, and the skill, MCP `review_setup` and the `#html` tag reference it without restating it.
- [ ] Claude Code with the MCP server: the agent writes a page, calls `ask_human`, you click Done, and the tool returns the decisions.
- [ ] Two projects open at once both work (§6 multi-root decision implemented).
- [ ] The skill works in Claude Code without MCP, through the CLI branch.
- [ ] ai-behaviors: `#=review #html` produces a working page, and `./install` installs the lib.
- [ ] claude.ai: the uploaded skill publishes an artifact page whose decisions the agent reads back after "done".
