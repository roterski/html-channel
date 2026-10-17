(ns decide.viz
  "Renders <pre data-viz=\"<lib>\"> blocks with the adapters a page declares in
  <meta name=\"hc-libs\" content=\"<lib> …\">.

  An adapter is /_kit/viz/<lib>.mjs (dashes become underscores) exporting
  render_BANG_ [blocks fail!] -> Promise. It renders each block it can in
  place, and calls (fail! pre index message) for each one it can't.
  This namespace owns what a reader and the agent see on failure: the block
  keeps its source, a notice follows it, and the error is reported as kind \"viz\".
  That holds for one bad block and for an adapter or library that didn't load.
  Blocks of an undeclared library are reported as kind \"undeclared\" and left alone.")

(defn declared-libs []
  (some-> (js/document.querySelector "meta[name=hc-libs]")
          (.getAttribute "content") (.split #"[\s,]+") (.filter #(pos? (.-length %)))))

(defn adapter-url [lib]
  (.-href (js/URL. (str "../viz/" (.replaceAll lib "-" "_") ".mjs") js/import.meta.url)))

(defn notice!
  "Shows `text` right after the block, which stays visible as source."
  [pre text]
  (let [n (js/document.createElement "div")]
    (set! (.-className n) "hc-viz-error")
    (set! (.-textContent n) text)
    (.after pre n)))

(defn block-failed!
  "One block of `lib` didn't render: a notice, and a viz error with its index."
  [report! lib pre i message]
  (let [text (str lib " block " i ": " message)]
    (notice! pre text)
    (report! "viz" text (str lib "#" i))))

(defn ^:async start!
  "Pre: the DOM is parsed. `report!` is (fn [kind message source])."
  [report!]
  (let [declared (or (declared-libs) [])
        blocks (js/Array.from (js/document.querySelectorAll "pre[data-viz]"))
        by-lib (group-by #(.. % -dataset -viz) blocks)]
    (doseq [[lib bs] by-lib
            :when (not (.includes declared lib))]
      (report! "undeclared"
               (str (count bs) " <pre data-viz=\"" lib "\"> block(s), but \"" lib
                    "\" isn't listed in <meta name=\"hc-libs\">")
               lib))
    (doseq [lib declared
            :let [bs (get by-lib lib)]
            :when (seq bs)]
      (try
        (let [adapter (js-await (js/import (adapter-url lib)))]
          (js-await (.render_BANG_ adapter bs (fn [pre i message] (block-failed! report! lib pre i message)))))
        (catch :default e
          (let [why (str lib " didn't load, so its blocks show as source: " (.-message e))]
            (doseq [pre bs] (notice! pre why))
            (report! "viz" (str (count bs) " " why) lib)))))))
