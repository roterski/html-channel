(ns decide.tiers
  "Page-wide depth switch for elements tagged data-tier=\"N\" (N >= 0).

  Level L shows every tagged element with tier <= L; deeper ones get
  hidden=\"until-found\", so find-in-page still reaches them, and a match
  raises the level to the found element's tier. Untagged content always shows.
  The page opens at level 0, unless the reader picked another one earlier.
  Level names come from <meta name=\"hc-tiers\" content=\"Overview,Shape,…\">,
  else the numbers. The reader's level is remembered per page in localStorage.")

(def level (atom nil))

(defn tier-of
  "The element's tier, or nil when data-tier isn't a non-negative integer."
  [el]
  (let [t (.. el -dataset -tier)]
    (when (and t (re-matches #"\d+" t)) (js/parseInt t 10))))

(defn tagged []
  (filter tier-of (js/Array.from (js/document.querySelectorAll "[data-tier]"))))

(defn storage-key [] (str "html-channel:tier:" js/location.pathname))

(defn saved-level []
  (try (some-> (.getItem js/localStorage (storage-key)) (js/parseInt 10))
       (catch :default _ nil)))

(defn set-level! [bar l]
  (reset! level l)
  (doseq [el (tagged)]
    (if (> (tier-of el) l)
      (.setAttribute el "hidden" "until-found")
      (.removeAttribute el "hidden")))
  (doseq [b (.querySelectorAll bar "button")]
    (.setAttribute b "aria-pressed" (str (= l (js/parseInt (.. b -dataset -level) 10)))))
  (try (.setItem js/localStorage (storage-key) (str l)) (catch :default _ nil)))

(defn level-names
  "One name per level 0..max-tier; named levels may extend past the deepest tier."
  [max-tier]
  (let [named (some-> (js/document.querySelector "meta[name=hc-tiers]")
                      (.getAttribute "content") (.split ",")
                      (.map #(.trim %)) (.filter #(pos? (.-length %))))]
    (vec (for [i (range (max (inc max-tier) (count named)))]
           (or (get named i) (str i))))))

(defn start!
  "Adds the depth switch when the page has tagged elements; otherwise does nothing."
  []
  (let [tiers (map tier-of (tagged))]
    (when (seq tiers)
      (let [names (level-names (apply max tiers))
            bar (js/document.createElement "nav")
            label (js/document.createElement "span")
            saved (saved-level)]
        (set! (.-id bar) "decide-tiers")
        (.setAttribute bar "aria-label" "Depth")
        (set! (.-textContent label) "Depth")
        (.append bar label)
        (doseq [[i n] (map-indexed vector names)]
          (let [b (js/document.createElement "button")]
            (set! (.-type b) "button")
            (set! (.. b -dataset -level) (str i))
            (set! (.-textContent b) n)
            (.addEventListener b "click" (fn [_] (set-level! bar i)))
            (.append bar b)))
        (.append js/document.body bar)
        (.addEventListener js/document "beforematch"
                           (fn [e]
                             (when-let [t (some-> (.-target e) (.closest "[data-tier]") tier-of)]
                               (set-level! bar (max t @level)))))
        (set-level! bar (if (and (some? saved) (< saved (count names)) (>= saved 0)) saved 0))))))
