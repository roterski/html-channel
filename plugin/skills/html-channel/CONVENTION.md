# html-channel page convention

An html-channel page is an HTML file that an agent writes.
A person opens it in a browser, reads it, and answers through buttons and fields.
Their answers are saved for the agent.
The page may run any JavaScript to *render* content.
Answers are saved only through `data-decision` controls, which one shared runtime script handles.

For a worked example of every form and control below, read the source of `/_kit/example.html` on the server
(for example, `curl http://127.0.0.1:8787/_kit/example.html`), or `example.html` next to this file in the skill.

Writing a page that explains a topic in depth, with instruments the reader drives? Also read `/_kit/EXPLORABLE.md`
(`EXPLORABLE.md` next to this file in the skill).

Drawing a statechart, a chart of modes and the events between them that the reader drives? Also read `/_kit/STATECHART.md`
(`STATECHART.md` next to this file in the skill).

## Where pages live

```
<project>/ai/html-channel/<session>/<page>.html
```

- `ai/html-channel` is the default root. A project can move it with `HTML_CHANNEL_ROOT` or `--root`; `dir` from `channel_setup` or `session new` is always where to write.
- `<session>` groups one agent conversation's pages. Get it from `channel_setup` (MCP) or `html-channel session new` (CLI).
- `<page>` is the page id. It must match `[a-z0-9-]+`, for example `cache-layer-review`.
  `@` is reserved: `<page>@<n>.html` files are revision snapshots, so don't write or edit them.
- The URL is `http://127.0.0.1:8787/<mount>/<session>/<page>.html`.
  `channel_setup` and `session new` return the `url_base` to put in front of the file name.
- When the server also has a second address (`HTML_CHANNEL_REMOTE_BASE`, such as a tailnet name a phone reaches it at),
  they also return `remote_url_base`, and page results carry `remote_url` next to `url`.
  Then every time this convention says to give the user the URL, give both, each on its own labelled line:
  `laptop: <url>` and `phone: <remote url>`.

## Required markup

Put these in `<head>`, in this order, before any other script:

```html
<link rel="stylesheet" href="/_kit/decide.css">
<script src="/_kit/boot.js"></script>
<script type="module" src="/_kit/decide.mjs"></script>
```

Put this in `<body>`:

```html
<div id="decide-offline" style="position:sticky;top:0;background:#fef3c7;color:#78350f;border:1px solid #f59e0b;padding:.6rem .9rem;border-radius:6px;margin-bottom:1rem;font:14px system-ui">
  <strong>html-channel server is not running</strong>: your answers stay in this browser and are sent once it's back.
  Start it with <code>html-channel serve</code> in the project; this notice then disappears on its own.
</div>
```

- `boot.js` reports failed loads and script errors to the agent, and keeps answers your page JS gives early, so it must come before any other script.
- The `#decide-offline` banner must be visible by default, with an inline style. The runtime hides it once the server answers, and shows it again if the server goes away.
- The banner may go anywhere in `<body>`. `position:sticky` keeps it on screen.
- Use root-relative `/_kit/…` paths, never a host or port.

## Styling

`decide.css` styles plain HTML: body, headings, tables, `<details>`, code, form fields, in light and dark.
A page with no `<style>` at all is readable.

- Add only layout: grids, widths, spacing. Inline `<style>` is fine, and it wins over the kit's look, buttons included.
  Only the markers for a chosen, pending, saved or carried answer keep priority, so the reader always sees them.
- For any color, use the kit's tokens, which switch with the color scheme:
  `--hc-bg`, `--hc-fg`, `--hc-muted`, `--hc-line`, `--hc-card`, `--hc-accent`, `--hc-on-accent`, `--hc-good`, `--hc-warn`, `--hc-bad`.
- Never set a text color without its dark-scheme counterpart. Inline SVG `<text>` follows the text color unless it sets `fill`.

## Controls

| Markup | Saved value |
|---|---|
| `<button data-decision="ID" data-choice="VALUE">` | `VALUE`. Buttons that share an `ID` act as one choice. |
| `<input data-decision="ID">`, `<textarea data-decision="ID">`, `<select data-decision="ID">` | the field's text, saved on change |
| `<input type="checkbox" data-decision="ID">` | `true` or `false` |
| `<input type="radio" name="…" data-decision="ID" value="VALUE">` | the checked radio's `VALUE` |
| `<button data-deeper="TARGET">` | a request to go deeper on `TARGET` (see Page kinds) |
| `<button data-clear="ID">` | `null` for `ID`: takes back its answer, and leaves `ID-note` alone |

- Each `ID` is unique per question on the page.
- Clicking a chosen button again also takes its answer back (`null`), except Done.
- The ids `status` (the Done button), `deeper`, `sources` (see Code links), `hunks` (see Diffs) and `moot` (see Moot) are reserved.

## JavaScript

- Load a library with `<script src="…">` from a CDN, at an exact version (`mermaid@11.4.1`, never `mermaid@11`), after the required `<head>` scripts.
- Prefer a kit adapter (next section) when one exists: it needs no code, and its errors reach you.
- To record an answer from your own code, call `window.htmlChannel.record("ID", value)`,
  or set a `data-decision` control's value and dispatch `new Event("change", {bubbles: true})` on it.
  Both work from the first inline script: answers given before the runtime starts are saved when it does.
  Never send answers to the server any other way.
- To react when an answer changes, watch its controls' `data-state` with a `MutationObserver`.
  Every change sets `data-state="pending"`: a choice, an edit, Clear, clicking a chosen button again, "Clear carried answers", and `htmlChannel.record`.
  It turns `saved` once the server has the answer, or `rejected` if the server refused it; it stays `pending` while the server is unreachable.
  Restoring answers sets only `saved`, or `carried` for an answer from an earlier revision, never `pending`.
  It runs once, when the server first answers, independent of the `load` event (usually just after it; later if the server was unreachable): read restored answers when `saved` or `carried` appears, not at `load`.
  Read the control's value in the observer's callback: the kit sets the value before `pending`.
  A `change` event is no substitute: buttons, Clear and `htmlChannel.record` don't fire one.

Bring your own library when the reader needs to *explore*, not just read.
Examples are a tree or graph the reader opens node by node (D3), a map (Leaflet), or a table they sort and filter.
For a static picture, the kit's forms are simpler (see Choosing a representation).
- Check that the library loaded (`if (!window.d3) …`) and put a short message in place of the drawing if it didn't. The failed load is already reported to you.
- Take colors from the `--hc-*` tokens. In SVG, set them with `style`, because presentation attributes like `fill="var(…)"` don't resolve.
- When the reader answers through the drawing (picking a node or a bar, ranking items), save it with `htmlChannel.record`.
  Also give that `ID` a visible `data-decision` field, such as a `readonly` input. The reader then sees their answer, and after a reload the runtime puts the saved value back into it, where your code can read it.

## Visualizations

The kit renders these blocks itself. List the libraries a page uses in `<head>`:

```html
<meta name="hc-libs" content="mermaid vega-lite katex">
```

| Block | Content |
|---|---|
| `<pre data-viz="mermaid">` | a Mermaid diagram |
| `<pre data-viz="vega-lite">` | a Vega-Lite JSON spec |
| `<pre data-viz="katex">`, `<span data-viz="katex">` | display math, inline math (TeX) |

- A block that fails to render keeps its source visible, gets an inline notice, and is reported to you as an error of kind `viz`.
- A block whose library isn't listed in `hc-libs` is reported as `undeclared` and left as text.

## Code links

Link every piece of code you show, block or inline, to the code it comes from.
The reader opens it in VS Code or on GitHub, or copies its path, from their own checkout.

Declare each repo once in `<head>`:

```html
<meta name="hc-repo" content="name=api; root=/Users/me/src/api; web=https://github.com/acme/api/blob/3f9c2a1b…/">
```

- `name` matches `[a-z0-9._-]+` and is unique on the page.
- `root` is the absolute path of the checkout you read the code from, which may differ from your working directory.
- `web` is optional. When `origin` is on GitHub, build it from `origin` as an https URL plus `/blob/` plus `git rev-parse HEAD` and a slash.
  Use HEAD even if it isn't pushed: the link works once it is. Other hosts get no `web`.

Mark each reference with `data-src="NAME:PATH#Lstart-Lend"`. `PATH` is relative to the repo root; the lines are optional.
`PATH` may contain spaces. Write a `#` in it as `%23` and a `%` as `%25`.

```html
<pre data-src="api:src/cache.ts#L10-L20">if (size > max) evict(oldest)</pre>
<p>Called from <code data-src="api:src/store.ts#L42">store.put</code>.</p>
```

- Only reference code that exists: the file in `root`, and the lines when you give them.
- Always write `NAME:`, even when the page has a single repo.
- A block (`<pre data-src>`, or `<pre><code data-src>`) gets a link line above it; inline code becomes the link. Each gets a copy button.
  Inline code inside your own `<a>` keeps your link and gets only the copy button.
- Changing or removing a `data-src` later replaces or removes its link.
- Your own SVG may hold `<a data-src="…">`. The kit sets its `href` and `xlink:href`, and keeps them current.
- References your page JS adds later are linked when they appear.
- A malformed meta or reference, or one naming an undeclared repo, is reported as `code-link` and left as text.

The kit adds a collapsed bar at the top of the page: the format (VS Code or GitHub), and each repo's root and GitHub URL.
The reader's changes are kept per page in their browser, and "Save as default" shares them with other pages of the same repo.
The bar opens by itself when a root differs from yours.

On a decision page, clicking Done also records `sources`, the settings the reader answered under:

```json
{"format":"vscode","repos":{"api":{"root":"/home/me/api-fork","default":"/Users/me/src/api","overridden":true}}}
```

`sources` is not a question. When a repo is `overridden`, the reader looked at a different checkout than you did.

## Highlighting

The kit colors code by its language, with no meta and no markup beyond naming the language.

| Code | Colored when |
|---|---|
| `<pre>…</pre>`, `<pre><code>…</code></pre>` | always: name the language, or mark it plain text |
| `<code>…</code>` inline | it has `data-lang`, `data-src` or a `language-*` class; other inline code stays plain, and so does a `data-src` to a file of unknown type |

The language is the first of:
1. `data-lang="ts"`, on the `<pre>` or its `<code>`: a language name or alias, such as `ts`, `py`, `clojure`, `sh`.
2. `class="language-ts"`, as Markdown writes it.
3. The file named by `data-src`: its name (`Dockerfile`) or its extension (`.clj`).

- `data-lang="text"` (or `plaintext`) shows a block as plain text, such as command output or prose.
- The kit never guesses. A block with no language, or an unknown name, stays plain and is reported as `highlight`: one error per cause, with a count.
- Code that already holds your own elements (links, `<b>`) is left as it is.
- Escape `<` as `&lt;` and `&` as `&amp;` in code. An unescaped `Array<string>` becomes a `<string>` element and its text is lost; the kit reports such code as `highlight` and leaves it plain.
- Highlighting keeps the text and the attributes, so code links work on highlighted code.

## Diffs

Show a code change as a diff, not as before and after copies.
Give the kit the change one of two ways:

| You have | Write |
|---|---|
| Changes already made (on disk, in a commit) | the output of `git diff`, in `<pre data-diff="ID">` |
| An edit you propose and haven't applied | the old and the new text, in `<pre data-diff-old>` and `<pre data-diff-new>` inside `<figure data-diff="ID">` |

```html
<pre data-diff="cache-fix" data-repo="api">diff --git a/src/cache.ts b/src/cache.ts
--- a/src/cache.ts
+++ b/src/cache.ts
@@ -10,2 +10,3 @@ class Cache {
   size = 0;
-  max = 100;
+  max = 1000;
+  ttl = 60;
</pre>

<figure data-diff="put-rename">
  <pre data-diff-old data-src="api:src/store.clj#L5">(defn put [m k v] …)</pre>
  <pre data-diff-new>(defn put! [m k v] …)</pre>
</figure>
```

- HTML-escape the text as in any `<pre>` (`&lt;`, `&amp;`): a diff whose text the browser read as tags is a `diff` error. A patch may hold several files.
- `data-repo="NAME"` on a patch, or `data-src` on either text, links each file and hunk to the code (see Code links). A file the patch deletes isn't linked.
  On two texts, the `#L` line of `data-src` numbers their first line.
- Lines are colored by the file's language (see Highlighting); `data-lang` on the diff overrides it. Changed words are marked on top.
- Two texts show 3 lines around each change; the reader opens the rest with "Show N unchanged lines" and closes it again with "Hide". A patch shows the context it has.
- The reader sees a split view when the diff is at least 46rem wide (about 740px), unified otherwise, and can pick either; the pick is kept in their browser.

### Decisions per hunk

Add `data-hunk-choices` with the choices, separated by spaces, to ask about each hunk:

```html
<pre data-diff="cache-fix" data-repo="api" data-hunk-choices="accept reject">…</pre>
```

Each hunk gets one button per choice, a Clear button and a note field, saved as `ID.KEY` and `ID.KEY-note`.
`KEY` is a hash of the hunk's file and changed lines, so an answer carries over to a revision that only moves the hunk.
When the same change appears more than once in a file, its context lines tell those hunks apart.
On Done the kit also records `hunks`: for each `KEY`, its file and its line range (`start,count`) on the old and the new side:

```json
{"cache-fix":{"3fa9c1d2":{"file":"src/cache.ts","old":"10,3","new":"10,4"}}}
```

`hunks` is not a question. Read it to tell which hunk an answer is about, and name hunks by file and lines, not by key.
An `ID.KEY` answer whose `KEY` isn't in `hunks` carried over from a revision where that hunk was different: it's stale.
Hunk choices need an `ID` no other diff on the page uses.

### When a diff can't render

The source stays visible with a notice, and you get an error of kind `diff`: unescaped text the browser read as tags, a patch that doesn't parse, a figure without both texts, two equal texts, or hunk choices on a diff whose `ID` is missing or taken (that diff renders without its controls).
When the highlighter or a language doesn't load, the diff renders without colors and you get a `highlight` error.

## Moot

When one answer makes another question, or one option of it, no longer apply, declare the link and the kit shows it.
No page script: the kit greys, folds and explains what is moot, keeps it current as answers change, and tells the agent on Done.

```html
<script type="application/json" id="hc-moot">
{"evict":      {"when": "cache=inproc", "reason": "you picked In-process for the cache, which evicts on its own"},
 "size=large": {"when": "cache=inproc|memcached", "reason": "a large cache needs Redis"}}
</script>

<section data-question="evict">
  <h2>Eviction</h2>
  …the evidence, the controls, Clear and the note…
</section>
```

- One link list per page: a `<script type="application/json" id="hc-moot">`, anywhere in the page.
- A key names what goes moot: `ID` for a question, `ID=VALUE` for one option of it.
- `when` lists terms separated by spaces; the link holds when any one matches.
  A term is `ID=VALUE`, or `ID=V1|V2` for any of several values.
  `ID=` matches an unanswered question or an empty field, and a checkbox is `true` or `false`.
  A question that is itself moot counts as unanswered, so a chain of links settles.
- `reason` is the line the reader sees while the link holds, after "Moot:".
  Name the other question and its answer in words: "you picked In-process for the cache".
  Without one, the kit writes "“Heading” is “Label”" from the other question and reports an error.

A moot question:
- Wrap every question a key names in an element with `data-question="ID"`, holding its heading, evidence, controls, Clear and note.
  The question's heading should be the container's first heading, as a direct child.
- While moot, the container greys and folds to that heading, the reason and a Show button; opened, it stays greyed.
  It unfolds when the link stops holding. Nothing is disabled, so Clear and the note still work.
- Its answer stays as the reader left it, and counts again once the link stops holding.

A moot option:
- `ID=VALUE` names a button (`data-choice="VALUE"`) or a radio (`value="VALUE"`) of question `ID`.
- The button, or the radio's label, greys and is struck through, with the reason after it. It can still be chosen.
- Options of a `<select>`, checkboxes and text fields can't be moot.

On Done the kit also records `moot`: each key whose link holds, with the reason the reader saw; `{}` when none does.

```json
{"evict":"you picked In-process for the cache, which evicts on its own","size=large":"a large cache needs Redis"}
```

`moot` is not a question, and it is only recorded on a page with a link list that parses.
A key `ID`: the question is moot, so don't act on its answer. A key `ID=VALUE` whose option is the answer: the reader chose an option another answer rules out, so ask.

### When a link can't be used

Each problem is one error of kind `moot`, reported when the page loads, and the link is dropped:
a list that isn't a JSON object, a second list (only the first is read), an entry without a string `when`,
a key or term naming an `ID` no control answers, a term without `=`, a question key with no `data-question` container,
an option key whose question has no such button or radio, or is a select, checkbox or text field,
and links that form or depend on a cycle.
A link without `reason` is kept, with the written reason, and reported too.

## Depth

What the reader needs for the decision stays visible. Put supporting detail in `<details>`.

For a page-wide depth switch, tag elements with `data-tier="N"` (0, 1, 2, …):
- The page opens at level 0. At level L, tagged elements with a tier above L are hidden. Untagged content always shows.
- `<meta name="hc-tiers" content="Overview,Shape,Detail">` names the levels; otherwise they're numbered.
- Find-in-page still finds hidden text and raises the level to show it.

Which depth tool to use:
- `<details>`: detail you've already written, about one point. The reader opens it in place.
- `data-tier`: the whole page at several levels of zoom, from overview to detail. Use it when the page is long and readers differ in how deep they want to go.
- A Deeper button (see Page kinds): detail you haven't written yet. It costs a round trip, so offer it where writing everything up front would bury the decision.

## Choosing a representation

Pick the form that matches the content's shape. These are what the kit offers; anything else, load your own library.

| Content | Form |
|---|---|
| Options compared on several criteria | `<table>`, one row per option, one column per criterion |
| Flow, sequence, dependencies | `<pre data-viz="mermaid">` |
| State: modes and the events between them | `<figure data-statechart>` (see STATECHART.md) |
| Numbers over time or across categories | `<pre data-viz="vega-lite">` |
| Code from a repo | `<pre data-src>` or `<code data-src>` (see Code links), colored by its language (see Highlighting) |
| A main point with supporting detail | visible summary, detail in `<details>` |
| Layers of abstraction | `data-tier` levels |
| A spatial or structural picture | inline `<svg>` using the color tokens |
| A code change | `<pre data-diff>` or `<figure data-diff>` (see Diffs) |
| Before and after, other than code | two columns side by side (`display:grid; grid-template-columns:1fr 1fr`, one column on narrow screens) |
| A tree, graph or map the reader explores | your own library, such as D3 or Leaflet (see JavaScript) |

## Choosing a control

| The question | Control |
|---|---|
| Pick one of 2–5 short options | buttons sharing an `ID` |
| Pick one, where each option needs a sentence | radios with `<label>`s |
| Pick one value from a long or known list | `<select>` |
| Yes or no; keep or drop; approve one item | checkbox |
| A short value, such as a number, name or size | `<input>` |
| Open feedback | one `<textarea>`, usually at the end |
| "I can't decide without more on X" | `<button data-deeper="X">` next to X |
| Accept or reject each change in a diff | `data-hunk-choices` on the diff (see Diffs) |
| An answer given through a picture | your own JS and `htmlChannel.record` (see JavaScript) |
| One answer makes another question, or one of its options, no longer apply | a link in the page's link list, not page script (see Moot) |

Put each question right after the table, diagram or chart it's about, so the reader answers with the evidence in view.
Don't gather questions into a form at the bottom. Only open notes and the Done button go last.

## Page kinds

**Decision pages** need an answer. They must end with:

```html
<button data-decision="status" data-choice="done">Done</button>
```

The agent waits for the click without blocking the conversation:
it runs `html-channel wait <session>/<page>` as a background command,
then ends its turn with a short text message that shows the page's full URL on its own line
(both URLs, labelled, when there is a `remote_url_base`; see Where pages live).
The URL goes in that final message, because a client may hide text written between tool calls.
While the wait runs, the user can keep talking to the agent.
The command exits when the reader acts, and its exit wakes the agent.
It prints the same JSON as `html-channel decisions`, plus `status`:

- `done`: act on `decisions`.
- `deeper`: the reader asked for more on the targets in `deeper` (see below).
- `superseded`: the page got a new revision, so answers to the old one no longer count. Do nothing: the wait for the new revision covers it.
- `waiting`: only with `--max-wait`, when it passed. Start the wait again.

Start a new wait after every rewrite of the page, including one the user asked for in chat.
If the client can't run a background command that wakes the agent when it exits,
ask the user to say "done" in chat, then read the answers with `get_decisions` or `html-channel decisions <session>/<page>`.
`ask_human` (MCP) waits for the same clicks inside a tool call, which blocks the conversation until the reader acts.

A decision page may offer **Deeper** buttons, `<button data-deeper="TARGET">`, where the reader may want more than the page shows.
A click ends the wait with `status: "deeper"` and the requested targets in `deeper`.
Rewrite the page with those targets expanded, start a new wait, and give the user the URL again in the final message.

**Report-only pages** just present output. They have no Done button, and the agent doesn't wait on them.

## Checking a page

Before giving the user the URL, call `check_page` (MCP) or run `html-channel check <session>/<page>`.
It loads the page in headless Chrome, light and dark, and returns `errors` and two `screenshots`.
Fix every error, look at the dark screenshot, and check again after each fix.
Without Chrome it returns only errors from earlier page loads.

## Revising a page

To revise a page, rewrite the same file.
- The server snapshots each new version as `<page>@<n>.html`, and that version is revision `n`.
- An open tab reloads itself when the page gets a new revision.
- Earlier answers carry over to the new revision for any control with the same `ID`. They are shown as *carried*, and a "Clear carried answers" button appears.
- `status`, `deeper`, `sources`, `hunks` and `moot` never carry over: each revision needs its own Done.

## Answering without the server

Every decision page also offers **Download answers**, next to Done. The kit adds it; write nothing for it.

- It saves the page's answers as an `.edn` file, unsent ones included, and records nothing: Done stays the only signal you get.
- While the server is unreachable, the kit hides Done, adds "Download keeps your answers" to the banner, shows an optional name field,
  and asks before the reader leaves with answers they haven't downloaded.
  Answers given meanwhile are kept in the browser and sent once the server is back; a closed tab's are sent the next time a tab of the page is open in that browser.
- Report-only pages get no Download.

A reader may send you that file instead of clicking Done. Read it with `html-channel decisions --file <answers.edn>`,
or `get_decisions` with `file` (a relative path resolves against the project). The file's first line names the command.
Both print the JSON `html-channel decisions` prints (see What the agent gets back), so read it the same way, plus:

- `respondent`: the name the reader typed; `":author"` when they downloaded while connected, which means the person you work with; `null` when neither.
- `state`: the page's state at Download: `connected`, `disconnected`, or `share` for an offline copy of the page, which keeps answers in the reader's browser and has no Done.
- `exported_at` and `file`.
- `checked`: `true` when the revision the answers were given at, `<session>/<page>@<rev>.html`, exists under the root.
  When `false`, `reason` says why and the answers are still printed: say they couldn't be checked against the page, then read them as usual.

`done` is always `true`: Download is how a reader finishes without the server.
`sources`, `hunks` and `moot` appear among `decisions`, as they do after Done.
Nothing is written to `decisions.jsonl`: each file is one reader's answers, kept apart.

## What the agent gets back

Every saved answer is one line appended to `<root>/<session>/decisions.jsonl`.
Answers a reader downloaded instead come as a file (see Answering without the server).

```json
{"page":"cache-layer-review","session":"3f9c2a1b","rev":2,"id":"eviction","value":"lru","at":"2026-10-16T13:08:34.844Z"}
```

- The last entry per `page` + `id` wins.
- `value: null` means the answer was cleared.
- `html-channel wait`, `ask_human`, `get_decisions` and `html-channel decisions` return the effective answers, Deeper requests, and the errors the page reported at this revision.
  `deeper` from `wait` lists only the targets requested since it started; from `ask_human`, only those since its last return, whatever the status; from the other two, every target at this revision.

```json
{"page":"cache-layer-review","rev":2,"done":true,"deeper":[],
 "decisions":{"eviction":{"value":"lru","rev":2,"at":"…"},"notes":{"value":"rename evict!","rev":1,"at":"…"}},
 "errors":[{"kind":"load","message":"failed to load <script> https://…","source":"https://…","at":"…"}]}
```

In this example, `notes` has `rev` 1, which is lower than the page's `rev` 2. That means the answer was carried over and not re-confirmed.
Error kinds are `load` (a script, stylesheet or image failed to load), `js` (an uncaught error), `viz` (a block didn't render), `undeclared`, `code-link` (a malformed repo meta or code reference), `statechart` (a statechart definition problem, see STATECHART.md), `highlight` (code with no or an unknown language, or the highlighter didn't load), `diff` (a diff that didn't render, or hunk choices without a unique ID) and `moot` (a problem in the link list, see Moot).
Explorable pages add `explorable`; explorable pages and pages with a statechart add `inert`, `unprobed` and `probe-cut-off` (see EXPLORABLE.md).
During `check_page`, `dialog` means the page called `alert()`, `confirm()` or `prompt()`, which would interrupt the reader.
`check_page` adds `no-reporter` when the page never loaded `/_kit/boot.js`: then no other error could have been seen.
