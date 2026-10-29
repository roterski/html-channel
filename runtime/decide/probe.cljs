(ns decide.probe
  "Interaction probe for explorable pages, run only during a headless check
  (?_check= in the URL): does each [data-instrument] respond to its controls?

  For each instrument: pause animations, scroll it into view, fingerprint it
  (its markup plus the pixels of each canvas in it), then drive its controls
  one at a time until the fingerprint changes. Its controls are those inside
  it and those pointing at it (data-target, data-set, a data-timeline's bar);
  answer controls (data-decision, data-clear, data-deeper) are never touched.
  An instrument with no controls gets a synthetic drag across its center.

  Reports, with the instrument's id as the source:
    inert     it has controls, and none of them changed it
    unprobed  it has no controls, and a synthetic drag changed nothing
              (a handler that checks event.isTrusted ignores synthetic input)
  A WebGL canvas reads back blank unless created with preserveDrawingBuffer: true.

  Post: the page is scrolled back to the top and the pause is as it was.")

(defn frames
  "Resolves after about `n` frames' time, so redraws land. A timer, not
  requestAnimationFrame: headless Chrome under --virtual-time-budget doesn't
  run animation frames, and the check would end before the probe does."
  [n]
  (js/Promise. (fn [resolve] (js/setTimeout resolve (* 50 n)))))

(defn fingerprint [el]
  (str (.-outerHTML el)
       (apply str (for [c (js/Array.from (.querySelectorAll el "canvas"))]
                    (try (.toDataURL c) (catch :default _ "tainted"))))))

(def skip "[data-decision], [data-clear], [data-deeper], #hc-pause")

(defn controls-of [el id]
  (let [q (js/CSS.escape id)
        inside (.querySelectorAll el "input:not([type=hidden]), select, textarea, button, a[data-set], [data-scrub]")
        outside (js/document.querySelectorAll
                 (str "[data-target=\"" q "\"], a[data-set=\"" q "\"], [data-timeline=\"" q "\"] input[type=range]"))]
    (->> (concat (js/Array.from inside) (js/Array.from outside))
         (remove #(.matches % skip))
         distinct
         vec)))

(defn fire! [el type]
  (.dispatchEvent el (js/Event. type {:bubbles true})))

(defn key! [el k]
  (.dispatchEvent el (js/KeyboardEvent. "keydown" {:key k :bubbles true})))

(defn drive!
  "Changes one control's value the way a reader would."
  [c]
  (let [tag (.-tagName c) type (.-type c)]
    (cond
      (.hasAttribute c "data-scrub")
      (key! c (if (= (.getAttribute c "aria-valuenow") (.getAttribute c "aria-valuemax")) "Home" "End"))

      (and (= tag "INPUT") (or (= type "range") (= type "number")))
      (let [hi (or (not-empty (.-max c)) "100") lo (or (not-empty (.-min c)) "0")]
        (set! (.-value c) (if (= (.-value c) hi) lo hi))
        (fire! c "input") (fire! c "change"))

      (and (= tag "INPUT") (or (= type "checkbox") (= type "radio")))
      (.click c)

      (= tag "SELECT")
      (do (set! (.-selectedIndex c) (mod (inc (.-selectedIndex c)) (max 1 (.. c -options -length))))
          (fire! c "input") (fire! c "change"))

      (or (= tag "INPUT") (= tag "TEXTAREA"))
      (do (set! (.-value c) (str (.-value c) "7"))
          (fire! c "input") (fire! c "change"))

      :else (.click c))))

(defn drag! [el]
  (let [r (.getBoundingClientRect el)
        x (+ (.-left r) (/ (.-width r) 2))
        y (+ (.-top r) (/ (.-height r) 2))
        t (or (js/document.elementFromPoint x y) el)
        at (fn [type dx]
             (let [init {:bubbles true :clientX (+ x dx) :clientY y :pointerId 1 :buttons 1 :isPrimary true}]
               (.dispatchEvent t (if (.startsWith type "pointer") (js/PointerEvent. type init) (js/MouseEvent. type init)))))]
    (at "pointerdown" 0) (at "mousedown" 0)
    (at "pointermove" 30) (at "mousemove" 30)
    (at "pointerup" 30) (at "mouseup" 30)
    (at "click" 30)))

(defn ^:async probe-one
  "nil when `el` responded, else [kind message]."
  [el]
  (let [id (.. el -dataset -instrument)
        cs (controls-of el id)]
    (.scrollIntoView el {:block "center"})
    (js-await (frames 2))
    (let [before (fingerprint el)]
      (if (seq cs)
        (loop [[c & more] cs]
          (if-not c
            ["inert" (str "instrument \"" id "\" didn't change when its " (count cs)
                          " control(s) were driven"
                          (when (.querySelector el "canvas")
                            " (a WebGL canvas needs preserveDrawingBuffer: true to be read)"))]
            (do (drive! c)
                (js-await (frames 2))
                (when (= before (fingerprint el)) (recur more)))))
        (do (drag! el)
            (js-await (frames 2))
            (when (= before (fingerprint el))
              ["unprobed" (str "instrument \"" id "\" has no controls, and a synthetic drag across it changed nothing; "
                               "check by hand that it responds")]))))))

(defn ^:async probe!
  "Pre: the page has loaded. `report!` is (fn [kind message source])."
  [report!]
  (let [hc js/window.htmlChannel
        was-paused (.-paused hc)]
    (when (.-setPaused hc) ((.-setPaused hc) true))
    (doseq [el (js/Array.from (js/document.querySelectorAll "[data-instrument]"))]
      (when-let [[kind message] (js-await (probe-one el))]
        (report! kind message (.. el -dataset -instrument))))
    (js/scrollTo 0 0)
    (when (.-setPaused hc) ((.-setPaused hc) was-paused))))

(defn start!
  "Probes once the page and its adapters have had time to render."
  [report!]
  (let [run #(js/setTimeout (fn [] (probe! report!)) 500)]
    (if (= "complete" (.-readyState js/document))
      (run)
      (.addEventListener js/window "load" run))))
