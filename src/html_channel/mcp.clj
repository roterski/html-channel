(ns html-channel.mcp
  "MCP server over stdio: newline-delimited JSON-RPC 2.0.

  Tools: channel_setup, ask_human, get_decisions, check_page. The channel root is --root,
  else $HTML_CHANNEL_ROOT, else <project>/ai/html-channel (`store/root-from`).
  The process stands by for the HTTP port for as long as it runs, so pages stay
  served while any session lives.

  With --channel, the server is also a Claude Code channel: when a reader clicks
  Done or Deeper on a page of a session this process set up, and no ask_human
  call is waiting on that page, it sends notifications/claude/channel, which
  wakes an idle session. Without the flag it never declares or sends one.

  Only JSON-RPC messages go to stdout; everything else is logged to stderr."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [html-channel.check :as check]
            [html-channel.registry :as registry]
            [html-channel.server :as server]
            [html-channel.session :as session]
            [html-channel.store :as store]))

(def version "0.1.0")

(def poll-ms 500)
(def progress-every-ms 30000)
(def default-max-wait-s 1500)

;; ---- output ----------------------------------------------------------------

(def ^:private stdout (java.io.PrintWriter. (java.io.OutputStreamWriter. System/out "UTF-8") true))

(defn- send! [msg]
  (let [line (json/generate-string (assoc msg :jsonrpc "2.0"))]
    (locking stdout (.println stdout line))))

;; ---- channel context -------------------------------------------------------

(defn convention []
  (some-> (io/resource "html_channel/CONVENTION.md") slurp))

(def ^:private default-session (atom nil))

;; ---- channel events (--channel) ----------------------------------------------

;; session -> number of decision entries already looked at
(def ^:private watched (atom {}))
;; [session page] -> number of ask_human calls waiting on it
(def ^:private waiting (atom {}))
;; [session page] -> entries before this index were answered by an ask_human return.
;; ask_human reports only Deeper requests from here on; channel events skip what it answered.
(def ^:private consumed (atom {}))

(defn- watch!
  "Starts watching `session` for reader events, from its current end."
  [root session]
  (when-not (contains? @watched session)
    (swap! watched #(if (contains? % session) % (assoc % session (count (store/entries root session)))))))

(defn- reader-event? [{:keys [id value]}]
  (or (and (= "status" id) (= "done" value))
      (and (= "deeper" id) (string? value))))

(defn- event-notification [session page {:keys [rev done deeper]}]
  {:method "notifications/claude/channel"
   :params {:content (str "The reader "
                          (if done "clicked Done" (str "asked to go deeper on " (str/join ", " deeper)))
                          " on html-channel page " page " (rev " rev "). Call get_decisions with page ""
                          page "" and session "" session "".")
            :meta {:page page :session session :rev (str rev) :status (if done "done" "deeper")}}})

(defn- push-events!
  "One pass over the watched sessions: notifies about new Done/Deeper clicks at
  a page's current revision, unless an ask_human call answered them."
  [root]
  (doseq [[session seen] @watched]
    (let [es (vec (store/entries root session))
          fresh (->> (map-indexed vector es) (drop seen)
                     (filter (fn [[i e]] (and (reader-event? e)
                                              (zero? (get @waiting [session (:page e)] 0))
                                              (>= i (get @consumed [session (:page e)] 0))))))]
      (swap! watched assoc session (count es))
      (doseq [page (distinct (map (comp :page second) fresh))
              :let [r (store/effective root session page)]
              :when (or (:done r) (seq (:deeper r)))]
        (send! (event-notification session page r))))))

(defn- run-event-loop! [root]
  (future
    (loop []
      (try (push-events! root)
           (catch Exception e (server/log "channel events:" (ex-message e))))
      (Thread/sleep 1000)
      (recur))))

(def channel-instructions
  (str "html-channel pages are local HTML pages that the user answers in a browser. "
       "When the user clicks Done or a Deeper button on a page and no ask_human call is waiting on it, "
       "an event arrives as <channel source=\"html-channel\" page=\"…\" session=\"…\" rev=\"…\" status=\"done|deeper\">. "
       "On status=done, call get_decisions and act on the answers. "
       "On status=deeper, call get_decisions, revise the page so the requested targets are expanded, "
       "then call ask_human."))

;; ---- channel context -------------------------------------------------------

(defn- channel
  "Registers the root and resolves the session: argument, else this process's
  default, else the client's id or a fresh one (which becomes the default)."
  [{:keys [root port channel?]} explicit-session]
  (let [mount (registry/register! root)
        s (or explicit-session @default-session (swap! default-session #(or % (session/pick nil))))]
    (store/session-dir root s) ; validates the name
    (when channel? (watch! root s))
    {:root root :session s :mount mount
     :url-base (str "http://127.0.0.1:" port "/" mount "/" s "/")}))

(defn- page-result [{:keys [root session url-base]} page]
  (merge {:page page :url (str url-base page ".html")}
         (store/page-state root session page)))

;; ---- tools -----------------------------------------------------------------

(defn channel-setup [ctx {:keys [session]}]
  (let [{:keys [root session url-base]} (channel ctx session)]
    (reset! default-session session)
    (fs/create-dirs (store/session-dir root session))
    {:dir (str (fs/canonicalize (store/session-dir root session)))
     :url_base url-base
     :session session
     :convention (convention)}))

(defn get-decisions [ctx {:keys [page session]}]
  (page-result (channel ctx session) page))

(defn check-page [ctx {:keys [page session]}]
  (let [{:keys [root session url-base]} (channel ctx session)]
    (check/check-page root session page (str url-base page ".html"))))

(def deeper-hint
  (str "The reader asked to go deeper on the targets in `deeper`. Rewrite the page with those "
       "parts expanded (a new revision), then call ask_human again."))

(defn ask-human
  "Blocks until the page's current revision has status=done, or a Deeper request
  made since this page's last ask_human return, or max-wait passes.
  Post: :deeper holds only the targets requested since that last return, whatever
  the status, so calling again without revising waits for the next click.
  Invariant, per MCP process: every Deeper request is returned by exactly one
  ask_human call; the next call starts from the last decisions entry this one
  read. That position lives in memory, so after a restart the requests at the
  current revision are returned once more."
  [{:keys [max-wait-s] :as ctx} {:keys [page session]} progress!]
  (let [ch (channel ctx session)
        k [(:session ch) page]
        f (store/page-file (:root ch) (:session ch) page)
        seen (volatile! (get @consumed k 0))]
    (when-not (fs/regular-file? f)
      (throw (ex-info (str "no page at " f "; write it first (see channel_setup's convention)") {})))
    (swap! waiting update k (fnil inc 0))
    (try
      (let [deadline (+ (System/currentTimeMillis) (* 1000 max-wait-s))]
        (loop [next-progress (+ (System/currentTimeMillis) progress-every-ms) ticks 0]
          ;; read the page state first: the Deeper read below then covers every
          ;; entry the state saw, including a Done
          (let [r (page-result ch page)
                {:keys [targets upto]} (store/deeper-requests (:root ch) (:session ch) page (:rev r) @seen)
                r (assoc r :deeper targets)
                now (System/currentTimeMillis)]
            (vreset! seen upto)
            (cond
              (:done r) (assoc r :status "done")
              (seq targets) (assoc r :status "deeper" :hint deeper-hint)
              (>= now deadline) (assoc r :status "waiting"
                                       :hint "Not done yet. Call ask_human again to keep waiting.")
              :else
              (let [due? (>= now next-progress)]
                (when due? (progress! (inc ticks) (str "waiting for Done on " (:url r))))
                (Thread/sleep poll-ms)
                (recur (if due? (+ now progress-every-ms) next-progress)
                       (if due? (inc ticks) ticks)))))))
      (finally
        (swap! consumed assoc k @seen)
        (swap! waiting update k dec)))))

(def page-arg {:type "string" :description "Page slug: the file name without .html, matching [a-z0-9-]+."})
(def session-arg {:type "string" :description "Session id from channel_setup. Defaults to this conversation's session."})

(def tools
  [{:name "channel_setup"
    :description (str "Start here before writing an html-channel page. Returns the directory to write "
                      "pages into, their URL base, the session id, and the page CONVENTION (markup rules). "
                      "Then write <dir>/<page>.html and give the user its URL.")
    :inputSchema {:type "object" :properties {:session session-arg}}}
   {:name "ask_human"
    :description (str "Wait until the user clicks the Done button on a decision page, then return their "
                      "answers (status 'done'). Returns status 'deeper' with the targets requested since its "
                      "last return when the user clicks a Deeper button: revise the page, then call again. "
                      "Returns status 'waiting' "
                      "if they take too long; call again to keep waiting. "
                      "Rewriting the page starts a new revision and needs a new Done. "
                      "Report-only pages without a Done button don't need this.")
    :inputSchema {:type "object" :properties {:page page-arg :session session-arg} :required ["page"]}}
   {:name "get_decisions"
    :description "Current state of a page without waiting: {done, rev, deeper, decisions, errors}."
    :inputSchema {:type "object" :properties {:page page-arg :session session-arg} :required ["page"]}}
   {:name "check_page"
    :description (str "Render a page you wrote in headless Chrome, light and dark, before showing it to the "
                      "user. Returns the errors the page reported (failed loads, script errors, diagrams "
                      "that didn't render) and the paths of a light and a dark screenshot: read them to "
                      "check contrast and layout. Without Chrome, returns only errors from earlier page loads.")
    :inputSchema {:type "object" :properties {:page page-arg :session session-arg} :required ["page"]}}])

;; ---- JSON-RPC --------------------------------------------------------------

(defn- tool-result [data]
  {:content [{:type "text" :text (json/generate-string data)}]
   :structuredContent data})

(defn- call-tool [ctx {:keys [name arguments _meta]}]
  (let [token (:progressToken _meta)
        progress! (fn [n message]
                    (when token
                      (send! {:method "notifications/progress"
                              :params {:progressToken token :progress n :message message}})))]
    (try
      (tool-result
       (case name
         "channel_setup" (channel-setup ctx arguments)
         "ask_human" (ask-human ctx arguments progress!)
         "get_decisions" (get-decisions ctx arguments)
         "check_page" (check-page ctx arguments)
         (throw (ex-info (str "unknown tool " name) {}))))
      (catch InterruptedException e (throw e))
      (catch Exception e
        {:content [{:type "text" :text (ex-message e)}] :isError true}))))

(def ^:private in-flight (atom {}))

(defn- handle! [ctx {:keys [id method params] :as _msg}]
  (case method
    "initialize"
    (send! {:id id :result (cond-> {:protocolVersion (or (:protocolVersion params) "2025-06-18")
                                    :capabilities (cond-> {:tools {}}
                                                    (:channel? ctx) (assoc :experimental {"claude/channel" {}}))
                                    :serverInfo {:name "html-channel" :version version}}
                             (:channel? ctx) (assoc :instructions channel-instructions))})
    "ping" (send! {:id id :result {}})
    "tools/list" (send! {:id id :result {:tools tools}})
    "tools/call"
    (swap! in-flight assoc id
           (future
             (try (send! {:id id :result (call-tool ctx params)})
                  (catch InterruptedException _ nil)
                  (finally (swap! in-flight dissoc id)))))
    "notifications/cancelled"
    (some-> (get @in-flight (:requestId params)) future-cancel)
    (when (and id (not (re-find #"^notifications/" (str method))))
      (send! {:id id :error {:code -32601 :message (str "method not found: " method)}}))))

(defn serve-stdio!
  "Serves MCP on stdin/stdout until stdin closes, standing by for the HTTP port meanwhile."
  [{:keys [root port max-wait-s channel?]}]
  (let [port (server/port-from port)
        ctx {:root (store/root-from root) :port port :max-wait-s (or max-wait-s default-max-wait-s)
             :channel? (boolean channel?)}]
    (server/run-standby! port)
    (when channel? (run-event-loop! (:root ctx)))
    (doseq [line (line-seq (io/reader System/in))
            :when (seq line)]
      (let [msg (try (json/parse-string line true) (catch Exception _ nil))]
        (if msg
          (handle! ctx msg)
          (send! {:id nil :error {:code -32700 :message "parse error"}}))))
    (System/exit 0)))
