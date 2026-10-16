(ns viz.versions
  "Pinned CDN builds the visualization adapters load, in load order.
  The only place library versions live: a version bump is a one-line change here.")

(def mermaid ["https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js"])

(def vega-lite ["https://cdn.jsdelivr.net/npm/vega@5.30.0/build/vega.min.js"
                "https://cdn.jsdelivr.net/npm/vega-lite@5.21.0/build/vega-lite.min.js"
                "https://cdn.jsdelivr.net/npm/vega-embed@6.26.0/build/vega-embed.min.js"])
