(ns viz.mermaid
  "Adapter for <pre data-viz=\"mermaid\">: the block's text is a Mermaid diagram.
  Also the one place Mermaid is loaded and configured, for the statechart control too."
  (:require [viz.load :as load]
            [viz.versions :as versions]))

(defn ^:async ready!
  "Resolves to window.mermaid, loaded and configured. Every caller gets the same
  configuration, so whichever initializes last changes nothing for the others."
  []
  (js-await (load/scripts! versions/mermaid))
  (let [m js/window.mermaid]
    ;; suppressErrorRendering: a failed diagram throws instead of drawing a "Syntax error" graphic
    (.initialize m {:startOnLoad false :securityLevel "strict" :suppressErrorRendering true
                    :theme (if (load/dark?) "dark" "default")})
    m))

(defn ^:async render! [blocks fail!]
  (let [m (js-await (ready!))]
    (doseq [[i pre] (map-indexed vector blocks)]
      (try
        (let [{:keys [svg]} (js-await (.render m (str "hc-mermaid-" i) (.-textContent pre)))
              d (load/container! pre)]
          (set! (.-innerHTML d) svg)
          (load/rendered! pre))
        (catch :default e
          (fail! pre i (.trim (str (or (.-message e) e)))))))))
