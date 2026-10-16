# Research: rich context pages

Researched 2026-10-16, for the threads in [frame.md](frame.md#next-research-threads).
Confidence labels: confirmed · probable · uncertain · unknown.

## 1. Libraries

**LLM-written diagrams often render yet still miss the required structure.**
- DIAGRAM-FAIL-BENCH (NeurIPS 2026, 630 Mermaid tasks, 21 diagram types): documentation raises render rates but doesn't close the gap. Confirmed.
- On a 70-task subset, coding agents produced a working diagram 51% of the time, 60% when retrying against a black-box verifier, and 96% with the paper's own repair method (RA-RAG). Confirmed.
- Mermaid exposes `parse(…, {suppressErrors})` and a `parseError` hook.
  Without `suppressErrorRendering`, a failed render draws a "Syntax error" graphic into the page. Confirmed.

**Higher-level chart specs beat writing raw Vega-Lite.**
- GPT-4o produced usable Vega-Lite for 70% of 24 charts; Gemini for 24%. Probable (single study).
- Flint, a semantic language compiled to Vega-Lite, beat direct Vega-Lite, most of all for weaker models. Probable.

| Need | Candidates | Status |
|---|---|---|
| Graph diagrams | Mermaid; Graphviz via WebAssembly (`@hpcc-js/wasm`, `@viz-js/viz`) | Both Graphviz builds render DOT to SVG in the browser: confirmed |
| Charts | Vega-Lite (`vega-embed`), Observable Plot | Plot reliability for LLMs: unknown |
| Sort/filter tables | Grid.js (~12 KB), Tabulator, AG Grid Community | Probable (comparison blogs) |
| Pivot | FINOS `<perspective-viewer>` 3.8.0, Apache-2.0 | Package confirmed; CDN entry point unverified |
| Editable diagrams | Excalidraw (React; `excalidraw-embed` loads without React), tldraw | tldraw needs a license key in production; whether localhost counts as development: uncertain |

**Not searched:** Chart.js, ECharts, Cytoscape, markmap, ELK, D2 in the browser.

## 2. Progressive disclosure

- Everything users need often belongs in the first view. The split between first view and secondary content decides whether disclosure works (NN/g). Confirmed.
- Moving between levels should be user-driven and reversible. Probable (secondary source).
- Prior art: Victor's explorable explanations and ladder of abstraction; Nelson's StretchText. Secondary sources.
- `hidden="until-found"` plus `beforematch`: Baseline 2025. Confirmed.
  A closed `<details>` opens on find-in-page. Probable.
  Safari had a regression in one major version. Uncertain.
- The `#zoom` behavior in ai-behaviors already defines levels 0–5: Purpose, Shape, Flow, Key points, Detail, Incidental. Confirmed.

## 3. Render feedback

Tested on this machine (Chrome 155, macOS). Confirmed unless noted.

| Path | JS errors | `console.error` | Failed CDN load | Screenshot |
|---|---|---|---|---|
| Chrome CLI, `--enable-logging=stderr` | ✓ | ✓ | ✗ | ✓ `--screenshot`, ~1.4 s |
| Playwright (Python, dev dependency here) | ✓ `pageerror` | ✓ | ✓ `requestfailed` | ✓ |
| In-page capture-phase `error` listener | ✓ | n/a | ✓ (MDN) | ✗ |

- `--force-dark-mode --blink-settings=preferredColorScheme=0` reproduced the dark-mode contrast bug on frame page rev 1.
- `--virtual-time-budget=3000` is needed for the runtime to run before the capture; without it, the offline banner shows.
- The Chrome CLI keeps the window at least ~500 px wide, so `--window-size=420` clips even a trivial page. Phone-width checks need device emulation through the DevTools protocol.
- The failed CDN load showed up as `requestfailed`, not as a 404. Whether the sandbox blocked the network: unknown.
- Failed resource loads skip `window.onerror`; a capture-phase listener on `window` catches them. Rejected promises need `unhandledrejection`. Confirmed (MDN).
- In-page screenshot libraries such as html2canvas rebuild the page from the DOM and miss many CSS properties. Confirmed.

## 4. A wake signal other than Done

Claude Code channels can push events into a session, including an idle one. Confirmed (official docs).
- The MCP server declares `capabilities.experimental['claude/channel']` and emits `notifications/claude/channel` with `{content, meta}`. Claude sees `<channel source=…>`.
- Events that arrive while Claude is busy queue up and arrive together on the next turn. Nothing confirms delivery.
- Custom channels need `--dangerously-load-development-channels server:<name>`. The flag only works in interactive sessions, and is ignored with `-p` and the Agent SDK. Team and Enterprise orgs must enable channels.
- Channels run only as local stdio servers. html-channel already has the same shape: a stdio MCP server that also serves HTTP.
- Claude Desktop and ECA equivalents: unknown.

## Unknowns

1. DIAGRAM-FAIL-BENCH's per-model and per-diagram-type numbers. They're in the full paper, which I haven't read.
2. LLM reliability of Observable Plot, D2 and Graphviz compared with Mermaid.
3. tldraw license terms for localhost tools.
4. CDN entry points for Perspective and `excalidraw-embed`.
5. Safari behavior for find-in-page on folded content.
6. Whether channels leave research preview.

## Sources

- [Claude Code channels reference](https://code.claude.com/docs/en/channels-reference)
- [DIAGRAM-FAIL-BENCH](https://neurips.cc/virtual/2026/173008)
- [MermaidSeqBench](https://arxiv.org/abs/2511.14967)
- [Mermaid API](https://mermaid.js.org/config/setup/mermaid/interfaces/Mermaid.html)
- [Evaluating LLMs for visualization generation](https://arxiv.org/pdf/2507.22890)
- [Flint](https://arxiv.org/pdf/2607.20775)
- [NN/g progressive disclosure](https://nngroup.com/articles/progressive-disclosure/)
- [MDN beforematch](https://developer.mozilla.org/docs/Web/API/Element/beforematch_event)
- [MDN window error event](https://developer.mozilla.org/En/DOM/Window.onerror)
- [html2canvas](https://html2canvas.hertzen.com/documentation)
- [@hpcc-js/wasm](https://www.jsdelivr.com/package/npm/@hpcc-js/wasm)
- [viz.js](https://github.com/viz-js/viz.js)
- [@finos/perspective-viewer](https://www.jsdelivr.com/package/npm/@finos/perspective-viewer)
- [excalidraw-embed](https://cdn.jsdelivr.net/npm/excalidraw-embed@0.18.4/README.md)
- [tldraw license key](https://tldraw.dev/sdk-features/license-key)
