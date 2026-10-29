(ns viz.katex
  "Adapter for <pre data-viz=\"katex\"> (display math) and <span data-viz=\"katex\">
  (inline math): the block's text is TeX."
  (:require [viz.load :as load]
            [viz.versions :as versions]))

(defn ^:async render! [blocks fail!]
  (js-await (js/Promise.all [(load/stylesheet! versions/katex-css) (load/scripts! versions/katex)]))
  (doseq [[i pre] (map-indexed vector blocks)]
    (let [d (load/container! pre)]
      (try
        (.render js/window.katex (.-textContent pre) d
                 {:displayMode (not= "SPAN" (.-tagName pre)) :throwOnError true})
        (load/rendered! pre)
        (catch :default e
          (.remove d)
          (fail! pre i (str (or (.-message e) e))))))))
