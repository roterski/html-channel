# Design: rich context pages

Chosen 2026-10-16 on the page `design-rich-pages` rev 2 (`ai/html-channel/ffa328e2/`).
Input: [frame.md](frame.md), [research.md](research.md).
For each choice, the other candidates stay listed with the reason they lost.

## Decisions

### 1. Libraries: kit adapters plus an escape hatch (1b)

- For suggested libraries, the page writes a declarative block, such as `<pre data-viz="mermaid">…</pre>`, and lists the libraries in `<meta name="hc-libs">`.
- The kit loads the pinned CDN build, renders the block, and routes parse errors to render feedback (§2).
- Any other library goes in as a raw `<script src>` tag with its own init code.
- Not chosen:
  - **1a, raw tags only:** the agent writes init code every time, and errors go unnoticed.
  - **1c, an import map injected by the server:** still needs init code, and the server would rewrite agent-written HTML.

### 2. Render feedback: in-page capture plus a headless check (2b)

- The runtime listens for capture-phase `error` and `unhandledrejection` events and reports them to the server.
- A new MCP tool `check_page` and command `html-channel check` load the page in system Chrome, headless, in light and dark.
  They return `{errors, screenshots}`, using the runtime's own reporting during that load.
- Without Chrome, it falls back to in-page capture only.
- Known limit: the Chrome CLI can't check phone width (minimum window about 500 px).
- Not chosen:
  - **2a, in-page capture only:** catches nothing visual, and the reader sees a broken page before the agent knows.
  - **2c, Playwright:** needs Python or Node, which breaks the bb-only install.

### 3. Going deeper: ends the wait, plus a channel push when available (3c)

- A reserved control, `data-deeper="section-id"`, records a request. `ask_human` returns `status: "deeper"` with the target; the agent revises the page and waits again.
- When the session loaded html-channel as a Claude Code channel, Deeper or Done on a page no agent is waiting on also emits `notifications/claude/channel`.
- User requirement: the tool must work with other agents, so it must work without channels.
  Ending the wait is the baseline in every client; the channel push is an addition.
- Not chosen:
  - **3a alone:** can't wake an idle session.
  - **3b alone:** Claude Code only, needs a dev flag, research preview.

### 4. Base theme: classless styles in decide.css (4a)

- `decide.css` styles bare `body`, headings, tables, `details`, `code` and inputs with tokens (`--hc-…`), `color-scheme`, light and dark modes, and a readable line width.
- Pages add layout only.
- Not chosen:
  - **4b, tokens only:** pages that don't use the tokens can still repeat the rev 1 contrast bug.
  - **4c, Pico or water.css from a CDN:** their look and their tokens, and a CDN request at runtime.

### 5. Depth: folds by default, with optional numbered tiers (5c)

- `<details>` is the default.
  The visible part holds what the decision needs.
- An optional numbered `data-tier` turns on a page-wide depth switch in the runtime.
- `#html` in ai-behaviors maps the `#zoom` levels onto those numbers.
  CONVENTION never names `#zoom` levels.
- Not chosen:
  - **5a, folds only:** no way to set the depth for the whole page.
  - **5b, tiers named after `#zoom`:** ties CONVENTION to ai-behaviors, and the agent must tag everything.

### 6. Representation guidance: a catalog in CONVENTION (6a)

- A short table maps the shape of the content to a suggested library or element.
- It lists what's available and doesn't prescribe; the choice for each subject stays with prompting.
- Not chosen:
  - **6b, a new ai-behaviors modifier:** applies only when invoked.
  - **6c, prompting only:** results vary by model and session.

## How the pieces fit

```
agent writes page ─▶ check_page ─▶ fix ─▶ ask_human ─┬─ Done ───▶ act
      ▲                                              └─ Deeper ─▶ revise ─┐
      └───────────────────────────────────────────────────────────────────┘
```

- Adapter parse errors (§1) feed the same error report as the in-page capture (§2).
- The depth switch (§5) and the Deeper control (§3) sit in the runtime's sticky bar, next to the offline banner.

## Required changes outside html-channel

Every decision above relies on rewording the no-JS rule to "page JS renders; answers flow only through the runtime".
That rule appears in three places, which must change together:
- `CONVENTION.md`
- `skill/html-channel/SKILL.md`
- ai-behaviors `behaviors/html/prompt.md` (`InlinePageScript`)

`#html` also gains the mapping from `#zoom` levels to `data-tier` numbers (§5).

## Next

Spec: [spec.md](spec.md).
