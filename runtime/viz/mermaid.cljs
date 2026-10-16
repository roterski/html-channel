(ns viz.mermaid
  "Adapter for <pre data-viz=\"mermaid\">: the block's text is a Mermaid diagram."
  (:require [viz.load :as load]
            [viz.versions :as versions]))

(defn ^:async render! [blocks report!]
  (js-await (load/scripts! versions/mermaid))
  (let [m js/window.mermaid]
    ;; suppressErrorRendering: a failed diagram throws instead of drawing a "Syntax error" graphic
    (.initialize m {:startOnLoad false :securityLevel "strict" :suppressErrorRendering true
                    :theme (if (load/dark?) "dark" "default")})
    (doseq [[i pre] (map-indexed vector blocks)]
      (try
        (let [{:keys [svg]} (js-await (.render m (str "hc-mermaid-" i) (.-textContent pre)))
              d (load/container! pre)]
          (set! (.-innerHTML d) svg)
          (load/rendered! pre))
        (catch :default e
          (load/failed! pre report! "mermaid" i (.trim (str (or (.-message e) e)))))))))
