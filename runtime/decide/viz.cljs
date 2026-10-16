(ns decide.viz
  "Renders <pre data-viz=\"<lib>\"> blocks with the adapters a page declares in
  <meta name=\"hc-libs\" content=\"<lib> …\">.

  An adapter is /_kit/viz/<lib>.mjs (dashes become underscores) exporting
  render_BANG_ [blocks report!] -> Promise. It renders each block in place and
  reports a block's failure itself, as kind \"viz\".
  Blocks of an undeclared library are reported as kind \"undeclared\" and left alone.")

(defn declared-libs []
  (some-> (js/document.querySelector "meta[name=hc-libs]")
          (.getAttribute "content") (.split #"[\s,]+") (.filter #(pos? (.-length %)))))

(defn adapter-url [lib]
  (.-href (js/URL. (str "../viz/" (.replaceAll lib "-" "_") ".mjs") js/import.meta.url)))

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
          (js-await (.render_BANG_ adapter bs report!)))
        (catch :default e
          (report! "load" (str "adapter for \"" lib "\" failed: " (.-message e)) (adapter-url lib)))))))
