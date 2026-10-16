# html-channel page convention

An html-channel page is a static HTML file that an agent writes.
A person opens it in a browser, reads it, and answers through buttons and fields.
Their answers are saved for the agent.
The page itself contains **no JavaScript**: one shared runtime script handles everything.

## Where pages live

```
<project>/.ai/<session>/<page>.html
```

- `<session>` groups one agent conversation's pages. Get it from `channel_setup` (MCP) or `html-channel session new` (CLI).
- `<page>` is the page id. It must match `[a-z0-9-]+`, for example `cache-layer-review`.
  `@` is reserved: `<page>@<n>.html` files are revision snapshots, so don't write or edit them.
- The URL is `http://127.0.0.1:8787/<mount>/<session>/<page>.html`.
  `channel_setup` and `session new` return the `url_base` to put in front of the file name.

## Required markup

Put these in `<head>`:

```html
<link rel="stylesheet" href="/_kit/decide.css">
```

Put these at the end of `<body>`:

```html
<div id="decide-offline" style="position:sticky;top:0;background:#fef3c7;color:#78350f;border:1px solid #f59e0b;padding:.6rem .9rem;border-radius:6px;margin-bottom:1rem;font:14px system-ui">
  <strong>html-channel server is not running</strong>: your answers won't be saved.
  Start it with <code>html-channel serve</code> in the project; this notice then disappears on its own.
</div>
<script type="module" src="/_kit/decide.mjs"></script>
```

- The `#decide-offline` banner must be visible by default, with an inline style. The runtime hides it once the server answers, and shows it again if the server goes away.
- The banner may go anywhere in `<body>`. `position:sticky` keeps it on screen.
- Use root-relative `/_kit/…` paths, never a host or port.

## Controls

| Markup | Saved value |
|---|---|
| `<button data-decision="ID" data-choice="VALUE">` | `VALUE`. Buttons that share an `ID` act as one choice. |
| `<input data-decision="ID">`, `<textarea data-decision="ID">`, `<select data-decision="ID">` | the field's text, saved on change |
| `<input type="checkbox" data-decision="ID">` | `true` or `false` |
| `<input type="radio" name="…" data-decision="ID" value="VALUE">` | the checked radio's `VALUE` |

- Each `ID` is unique per question on the page.
- The id `status` is reserved for the Done button.
- Don't add `onclick` handlers, `<script>` blocks or any other JavaScript. Inline `<style>` is fine.

## Page kinds

**Decision pages** need an answer. They must end with:

```html
<button data-decision="status" data-choice="done">Done</button>
```

The agent then waits for that click with `ask_human` (MCP).
Without MCP, the user says "done" in chat and the agent reads the answers with `get_decisions` or `html-channel decisions <session>/<page>`.

**Report-only pages** just present output. They have no Done button, and the agent doesn't wait on them.

## Revising a page

To revise a page, rewrite the same file.
- The server snapshots each new version as `<page>@<n>.html`, and that version is revision `n`.
- An open tab reloads itself when the page gets a new revision.
- Earlier answers carry over to the new revision for any control with the same `ID`. They are shown as *carried*, and a "Clear carried answers" button appears.
- `status` never carries over: each revision needs its own Done.

## What the agent gets back

Every saved answer is one line appended to `<project>/.ai/<session>/decisions.jsonl`:

```json
{"page":"cache-layer-review","session":"3f9c2a1b","rev":2,"id":"eviction","value":"lru","at":"2026-10-16T13:08:34.844Z"}
```

- The last entry per `page` + `id` wins.
- `value: null` means the answer was cleared.
- `ask_human`, `get_decisions` and `html-channel decisions` return the effective answers:

```json
{"page":"cache-layer-review","rev":2,"done":true,
 "decisions":{"eviction":{"value":"lru","rev":2,"at":"…"},"notes":{"value":"rename evict!","rev":1,"at":"…"}}}
```

In this example, `notes` has `rev` 1, which is lower than the page's `rev` 2. That means the answer was carried over and not re-confirmed.
