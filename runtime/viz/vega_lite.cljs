(ns viz.vega-lite
  "Adapter for <pre data-viz=\"vega-lite\">: the block's text is a Vega-Lite JSON spec."
  (:require [viz.load :as load]
            [viz.versions :as versions]))

(defn ^:async render! [blocks fail!]
  (js-await (load/scripts! versions/vega-lite))
  (doseq [[i pre] (map-indexed vector blocks)]
    (let [d (load/container! pre)]
      ;; vega-embed makes its container inline-block, which shrinks to the chart:
      ;; a "width": "container" chart would then measure 0 and draw nothing
      (set! (.. d -style -display) "block")
      (try
        (let [spec (js/JSON.parse (.-textContent pre))]
          ;; the dark theme paints its own grey; let the page background show through
          (js-await (js/window.vegaEmbed d spec (cond-> {:actions false :renderer "svg"
                                                         :config {:background "transparent"}}
                                                  (load/dark?) (assoc :theme "dark"))))
          (load/rendered! pre))
        (catch :default e
          (.remove d)
          (fail! pre i (str (or (.-message e) e))))))))
