# html-channel page convention

An html-channel page is an HTML file that an agent writes.
A person opens it in a browser, reads it, and answers through buttons and fields.
Their answers are saved for the agent.
The page may run any JavaScript to *render* content.
Answers are saved only through `data-decision` controls, which one shared runtime script handles.

For a worked example of every form and control below, read the source of `/_kit/example.html` on the server
(for example, `curl http://127.0.0.1:8787/_kit/example.html`), or `example.html` next to this file in the skill.

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
  <strong>html-channel server is not running</strong>: your answers won't be saved.
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

- Each `ID` is unique per question on the page.
- The ids `status` (the Done button) and `deeper` are reserved.

## JavaScript

- Load a library with `<script src="…">` from a CDN, at an exact version (`mermaid@11.4.1`, never `mermaid@11`), after the required `<head>` scripts.
- Prefer a kit adapter (next section) when one exists: it needs no code, and its errors reach you.
- To record an answer from your own code, call `window.htmlChannel.record("ID", value)`,
  or set a `data-decision` control's value and dispatch `new Event("change", {bubbles: true})` on it.
  Both work from the first inline script: answers given before the runtime starts are saved when it does.
  Never send answers to the server any other way.

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
<meta name="hc-libs" content="mermaid vega-lite">
```

| Block | Content |
|---|---|
| `<pre data-viz="mermaid">` | a Mermaid diagram |
| `<pre data-viz="vega-lite">` | a Vega-Lite JSON spec |

- A block that fails to render keeps its source visible, gets an inline notice, and is reported to you as an error of kind `viz`.
- A block whose library isn't listed in `hc-libs` is reported as `undeclared` and left as text.

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
| Flow, sequence, dependencies, state | `<pre data-viz="mermaid">` |
| Numbers over time or across categories | `<pre data-viz="vega-lite">` |
| A main point with supporting detail | visible summary, detail in `<details>` |
| Layers of abstraction | `data-tier` levels |
| A spatial or structural picture | inline `<svg>` using the color tokens |
| Before and after | two columns side by side (`display:grid; grid-template-columns:1fr 1fr`, one column on narrow screens) |
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
| An answer given through a picture | your own JS and `htmlChannel.record` (see JavaScript) |

Put each question right after the table, diagram or chart it's about, so the reader answers with the evidence in view.
Don't gather questions into a form at the bottom. Only open notes and the Done button go last.

## Page kinds

**Decision pages** need an answer. They must end with:

```html
<button data-decision="status" data-choice="done">Done</button>
```

The agent then waits for that click with `ask_human` (MCP).
Before calling `ask_human`, show the user the page's full URL in a short text message, with the URL on its own line.
`ask_human` blocks, and the user can't answer a page they can't find.
Without MCP, the user says "done" in chat and the agent reads the answers with `get_decisions` or `html-channel decisions <session>/<page>`.

A decision page may offer **Deeper** buttons, `<button data-deeper="TARGET">`, where the reader may want more than the page shows.
A click ends the wait: `ask_human` returns `status: "deeper"`, with the targets requested since its last return in `deeper`.
Rewrite the page with those targets expanded, then call `ask_human` again.
Calling it again without a rewrite waits for the next click.

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
- `status` and `deeper` never carry over: each revision needs its own Done.

## What the agent gets back

Every saved answer is one line appended to `<root>/<session>/decisions.jsonl`:

```json
{"page":"cache-layer-review","session":"3f9c2a1b","rev":2,"id":"eviction","value":"lru","at":"2026-10-16T13:08:34.844Z"}
```

- The last entry per `page` + `id` wins.
- `value: null` means the answer was cleared.
- `ask_human`, `get_decisions` and `html-channel decisions` return the effective answers, Deeper requests, and the errors the page reported at this revision.
  `deeper` from `ask_human` lists only the targets requested since its last return, whatever the status; from the other two it lists every target at this revision.

```json
{"page":"cache-layer-review","rev":2,"done":true,"deeper":[],
 "decisions":{"eviction":{"value":"lru","rev":2,"at":"…"},"notes":{"value":"rename evict!","rev":1,"at":"…"}},
 "errors":[{"kind":"load","message":"failed to load <script> https://…","source":"https://…","at":"…"}]}
```

In this example, `notes` has `rev` 1, which is lower than the page's `rev` 2. That means the answer was carried over and not re-confirmed.
Error kinds are `load` (a script, stylesheet or image failed to load), `js` (an uncaught error), `viz` (a block didn't render) and `undeclared`.
During `check_page`, `dialog` means the page called `alert()`, `confirm()` or `prompt()`, which would interrupt the reader.
`check_page` adds `no-reporter` when the page never loaded `/_kit/boot.js`: then no other error could have been seen.
