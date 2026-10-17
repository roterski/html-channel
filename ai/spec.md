# Spec: rich context pages

Agreed 2026-10-16.
Input: [frame.md](frame.md), [research.md](research.md), [design.md](design.md).
Levels: MUST · SHOULD · MAY · WONT.
Each item has its done-condition.
Nesting = dependency.

## Facts from the code that shape this spec

- **The process that receives a click is often not the agent's process.**
  Whichever process holds the port serves every project (`server.clj` docstring).
  So a notification about a click must come from the agent's own MCP process, by watching its sessions' `decisions.jsonl` (§7.4.2).
- **Page JS can already record answers.**
  The runtime listens for `change` events on the whole document (`runtime/decide/core.cljs`, `on-change`).
  Setting a control's value and firing a bubbling `change` event records it.

## Resolved questions

| Question | Decision |
|---|---|
| Where screenshots go | `<session>/.check/`, with a `.gitignore` the tool writes |
| Channel push default | Opt-in, with `html-channel mcp --channel` |
| Who places Deeper buttons | The agent, with `data-deeper` |
| JS helper `window.htmlChannel.record` | MAY |
| Order of work | 2 → 3 → 4/5 → 7.1–7.3 → 6 → 8 → 7.4 → 9 (10 alongside each) |

## Work breakdown

### 1. Contract: rewrite the no-JS rule

- 1.1 **MUST**: CONVENTION says pages may run any JS to render, but answers are saved only through `data-decision` controls.
  - Done when: CONVENTION no longer forbids `<script>`.
  - Done when: it documents recording an answer from page JS by setting the value and then calling `dispatchEvent(new Event('change', {bubbles: true}))`.
- 1.2 **MUST**: SKILL.md and the README match 1.1.
  Done when: `grep -ri "no javascript"` finds nothing outside git history.
- 1.3 **MAY**: a `window.htmlChannel.record(id, value)` helper.
  Done when: a call from a page produces an entry in `decisions.jsonl`.

### 2. Base theme

- 2.1 **MUST**: tokens `--hc-bg/fg/muted/line/card/accent/good/warn/bad` on `:root`.
  `color-scheme: light dark` is set, and dark values live under `prefers-color-scheme`.
- 2.2 **MUST**: classless styles for `body`, headings, `p`, `table`, `details/summary`, `code/pre`, `input/textarea/select` and `svg text`, wrapped in `:where()` so that page styles always win.
  - Done when: a page with no `<style>` passes 2.3 in both themes.
  - Done when: the existing demo page looks the same before and after (screenshot comparison).
- 2.3 **MUST**: every text token pair reaches at least 4.5:1 contrast (WCAG AA) in both themes.
  Done when: a unit test computes the ratios from `decide.css` and fails below 4.5.
- 2.4 **SHOULD**: a readable line width (`max-width` around 50–56rem), and tables wrapped in `overflow-x: auto` so they work at phone width.

### 3. Error capture in the runtime

Sections 4, 5 and 7 all rely on this.

- 3.1 **MUST**: capture-phase `error` listeners (failed loads and JS errors), plus `unhandledrejection`.
  - They must be installed before any page script runs.
    CONVENTION moves the runtime into `<head>`, ahead of library tags.
  - Uncertain: a module script in `<head>` is deferred, and may install too late.
    If so, it needs a synchronous `/_kit/boot.js` first.
    3.4 settles this.
- 3.2 **MUST**: errors go to `POST _errors`, which appends `{page, rev, kind, message, source, at, check?}` to `<session>/errors.jsonl`.
- 3.3 **MUST**: `ask_human`, `get_decisions` and `html-channel decisions` return `errors` for the current revision.
  Done when: a page with a broken CDN `src` shows the failed URL in `errors` after one load.
- 3.4 **MUST**: a spike shows that the listener catches a failed CDN script declared before it in `<head>`.
  Falsified if: no report arrives.

### 4. Library adapters

- 4.1 **MUST**: pages declare libraries with `<meta name="hc-libs" content="mermaid vega-lite">`.
  The runtime loads `/_kit/viz/<lib>.mjs`, which imports the library from a pinned jsDelivr URL.
- 4.2 **MUST**: adapters render `<pre data-viz="<lib>">` blocks in place.
  On a parse failure, the block keeps its source and gets an inline notice, and `kind: "viz"` is reported with the block's index and the parser message.
  Done when: a deliberate Mermaid syntax error yields a `viz` entry and no "Syntax error" graphic.
- 4.3 **MUST**: a `data-viz` block whose library isn't declared reports `kind: "undeclared"`.
- 4.4 Initial adapters:
  - Mermaid: **MUST**.
  - Vega-Lite: **SHOULD**.
  - Graphviz (`@viz-js/viz`), and a table adapter for sort and filter: **MAY**.
- 4.5 **MUST**: each version is pinned in exactly one file under `runtime/viz/`.
  Done when: a version bump is a one-line diff.
- 4.6 **WONT** in this round: adapters for pivot tables (Perspective) or editable diagrams.
  Pages can still use them as their own libraries (the escape hatch) plus 1.1.

### 5. Headless check

- 5.1 **MUST**: an MCP tool `check_page {page, session?}` and a CLI command `html-channel check <session>/<page>`.
- 5.2 **MUST**: find Chrome via `HTML_CHANNEL_CHROME`, then the standard macOS app path, then `google-chrome`, `chromium` and `chromium-browser` on PATH.
  Without Chrome, return `{chrome: false, errors: <in-page only>}`, not an error.
- 5.3 **MUST**: two runs, one light and one with `--force-dark-mode --blink-settings=preferredColorScheme=0`.
  - Both use `--virtual-time-budget=3000` and a 900px window.
  - The URL carries `?_check=<id>`, and the runtime tags its reports with that id.
  - The result is `{rev, errors, screenshots: {light, dark}}`.
  - Screenshots are written to `<session>/.check/` as absolute paths.
    The tool writes a `.gitignore` there.
- 5.4 **MUST**: a spike shows that `POST _errors` completes within the virtual-time budget.
  Falsified if: in-page errors never arrive during a check.
- 5.5 **SHOULD**: CONVENTION and the skill tell the agent to run `check_page` before `ask_human` and to read the dark screenshot.
- 5.6 **WONT**: phone-width checks (they need DevTools protocol emulation), Playwright, or automated contrast scoring.

### 6. Depth tiers

- 6.1 **MUST**: `<details>` stays the default.
  CONVENTION says what a decision needs stays outside folds.
- 6.2 **MUST**: any element may carry `data-tier="N"` (N ≥ 0).
  If a page has any, the runtime adds a sticky level switch.
  Elements above the chosen level get `hidden="until-found"`.
  Done when: hiding and showing works, and a find-in-page match inside a hidden tier reveals it in Chrome.
- 6.3 **SHOULD**: `<meta name="hc-tiers" content="Purpose,Shape,Flow">` names the levels and sets how many there are.
  Without it, levels are numbered.
- 6.4 **MAY**: the browser remembers the reader's chosen level in `localStorage`.

### 7. Deeper

- 7.1 **MUST**: an agent-placed `<button data-deeper="target">` records `{id: "deeper", value: target}` at the current revision.
  Like `status`, it never carries over.
- 7.2 **MUST**: `ask_human` returns when the current revision has `done` or any `deeper` entries, as `status: "deeper", deeper: [targets]`.
  Done when: an e2e test clicks Deeper, `ask_human` returns, the page is rewritten, and the next `ask_human` blocks.
- 7.3 **MUST**: CONVENTION tells the agent what to do next: revise the page with the target expanded, then wait again.
- 7.4 **SHOULD**: channel push.
  - 7.4.1 **MUST**: off by default.
    `html-channel mcp --channel` turns it on and declares `capabilities.experimental['claude/channel']`.
    Done when: without the flag, the initialize response has no experimental capability and no notification is ever sent.
  - 7.4.2 **MUST**: when on, the MCP process polls its sessions' `decisions.jsonl`.
    A new `done` or `deeper` entry, on a page with no `ask_human` in flight, sends `notifications/claude/channel` with `meta: {page, rev, status}`.
  - 7.4.3 **MUST**: when on, the server sends `instructions` saying what the event means and that the next step is `get_decisions`.
  - 7.4.4 **MUST**: a manual check that an idle Claude Code session started with `--dangerously-load-development-channels server:html-channel` wakes on Deeper.
    It's manual because the flag needs an interactive session.

### 8. Representation catalog

- 8.1 **MUST**: CONVENTION gets a table of at most 10 rows mapping content shape to an element or adapter.
  - Rows include:
    - options × criteria → comparison table
    - flow or dependency → Mermaid
    - series → Vega-Lite
    - hierarchy → nested `<details>`
    - many rows → table adapter
  - The table ends with: "anything else: load your own library".
  - Done when: this section adds fewer than 40 lines to CONVENTION.

### 9. ai-behaviors `#html` (separate repo)

- 9.1 **MUST**: replace `InlinePageScript` in the forbidden set with `AnswerOutsideRuntime`.
- 9.2 **SHOULD**: tell the agent to run `check_page` before `ask_human` when it's available.
- 9.3 **SHOULD**: when combined with `#zoom`, map its levels one-to-one onto `data-tier` (0 is Purpose … 5 is Incidental), with `hc-tiers` naming them.
- 9.4 **MUST**: hook tests and the parity test pass.
  Done when: `bb test` is green in ai-behaviors.

### 10. Tests and CI

- 10.1 **MUST**: e2e tests cover 3.3, 4.2, 4.3, 6.2 and 7.2.
- 10.2 **SHOULD**: CI runs `check` against `examples/` when Chrome is available, and is skipped otherwise.
- 10.3 **MUST**: `bb build` copies `runtime/viz/` into the kit.
  The CI check that build outputs are up to date covers it.
