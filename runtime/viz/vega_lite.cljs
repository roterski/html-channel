(ns viz.vega-lite
  "Adapter for <pre data-viz=\"vega-lite\">: the block's text is a Vega-Lite JSON spec."
  (:require [viz.load :as load]
            [viz.versions :as versions]))

(defn ^:async render! [blocks report!]
  (js-await (load/scripts! versions/vega-lite))
  (doseq [[i pre] (map-indexed vector blocks)]
    (let [d (load/container! pre)]
      (try
        (let [spec (js/JSON.parse (.-textContent pre))]
          (js-await (js/window.vegaEmbed d spec (cond-> {:actions false :renderer "svg"}
                                                  (load/dark?) (assoc :theme "dark"))))
          (load/rendered! pre))
        (catch :default e
          (.remove d)
          (load/failed! pre report! "vega-lite" i (str (or (.-message e) e))))))))
