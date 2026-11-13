(ns viz.versions
  "Pinned CDN builds the visualization adapters and the statechart control load, in load order.
  The only place library versions live: a version bump is a one-line change here.")

(def mermaid ["https://cdn.jsdelivr.net/npm/mermaid@12.3.0/dist/mermaid.min.js"])

(def vega-lite ["https://cdn.jsdelivr.net/npm/vega@5.30.0/build/vega.min.js"
                "https://cdn.jsdelivr.net/npm/vega-lite@5.21.0/build/vega-lite.min.js"
                "https://cdn.jsdelivr.net/npm/vega-embed@6.26.0/build/vega-embed.min.js"])

(def katex-css "https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css")

(def katex ["https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"])

;; the statechart control's rules engine (window.XState)
(def xstate ["https://cdn.jsdelivr.net/npm/xstate@5.33.4/dist/xstate.umd.min.js"])

;; the statechart renderer for charts with history states; an ES module, imported on first use
(def state-machine-cat "https://cdn.jsdelivr.net/npm/state-machine-cat@15.0.10/+esm")
