(ns viz.load
  "Shared by the adapters: loading pinned builds and placing a rendered block.
  Failures go through the fail! function the runtime passes to render!.")

(def loaded (atom {}))

(defn script!
  "Appends a classic <script src=url> once per url. Resolves when it has run;
  rejects when it fails to load (which /_kit/boot.js also reports)."
  [url]
  (or (get @loaded url)
      (let [p (js/Promise.
               (fn [resolve reject]
                 (let [s (js/document.createElement "script")]
                   (set! (.-src s) url)
                   (set! (.-onload s) (fn [_] (resolve true)))
                   (set! (.-onerror s) (fn [_] (reject (js/Error. (str "could not load " url)))))
                   (.append js/document.head s))))]
        (swap! loaded assoc url p)
        p)))

(defn stylesheet!
  "Appends a <link rel=stylesheet href=url> once per url. Resolves when it has
  loaded; rejects when it fails to load (which /_kit/boot.js also reports)."
  [url]
  (or (get @loaded url)
      (let [p (js/Promise.
               (fn [resolve reject]
                 (let [l (js/document.createElement "link")]
                   (set! (.-rel l) "stylesheet")
                   (set! (.-href l) url)
                   (set! (.-onload l) (fn [_] (resolve true)))
                   (set! (.-onerror l) (fn [_] (reject (js/Error. (str "could not load " url)))))
                   (.append js/document.head l))))]
        (swap! loaded assoc url p)
        p)))

(defn ^:async scripts!
  "Loads `urls` one after another, in order."
  [urls]
  (doseq [u urls] (js-await (script! u))))

(defn dark? [] (.-matches (js/matchMedia "(prefers-color-scheme: dark)")))

(defn container!
  "A fresh <div class=hc-viz> right after `pre`; a <span class=hc-viz> after an inline <span> block."
  [pre]
  (let [d (js/document.createElement (if (= "SPAN" (.-tagName pre)) "span" "div"))]
    (set! (.-className d) "hc-viz")
    (.after pre d)
    d))

(defn rendered!
  "The block rendered into `container`: its source stays in the DOM, hidden."
  [pre]
  (set! (.-hidden pre) true))
