(ns decide.explore
  "Controls for explorable pages. They only drive the page's own demos through
  DOM events; none of them records an answer.

    <span data-scrub=\"name\" data-target=\"demo\" data-min data-max data-step>3</span>
        a number the reader drags sideways (or steps with the arrow keys);
        dispatches hc-param {name value} on the demo, once at start and on every change
    <a data-set=\"demo\" data-state='{\"k\": 1}'>text</a>
        dispatches hc-state {k 1} on the demo when clicked; a scrub on that demo
        whose name is a key of the state moves to that value
    <div data-timeline=\"demo\" data-duration=\"4\"></div>
        play/pause, step, a scrub bar and a speed setting over t in [0, duration] s;
        dispatches hc-time {t playing} on the demo, once at start and on every change
  \"demo\" is the id of an element, or the value of its data-instrument.

  The global pause: window.htmlChannel.paused, toggled by a button the runtime
  adds to explorable pages and pages with a timeline, and announced as
  hc-pause {paused} on document. It starts on under prefers-reduced-motion.
  Timelines stop while it is on and resume when it goes off.
  Markup errors (a missing target, bad JSON) are reported as kind \"explorable\".")

(defn explorable? []
  (some? (js/document.querySelector "meta[name=hc-kind][content=explorable]")))

(defn emit! [target type detail]
  (.dispatchEvent target (js/CustomEvent. type {:detail detail})))

(defn target-of
  "The element with id `id`, else the [data-instrument=`id`]; nil after
  reporting the markup error."
  [report! el attr id]
  (or (and id (or (js/document.getElementById id)
                  (js/document.querySelector (str "[data-instrument=\"" (js/CSS.escape id) "\"]"))))
      (do (report! "explorable" (str "<" (.toLowerCase (.-tagName el)) " " attr "=\"" id "\">"
                                     " points at no element with that id or data-instrument")
                   attr)
          nil)))

(defn num-attr [el attr fallback]
  (let [v (js/parseFloat (.getAttribute el attr))]
    (if (js/isNaN v) fallback v)))

(defn decimals [step]
  (let [s (str step) i (.indexOf s ".")]
    (if (neg? i) 0 (- (count s) i 1))))

;; ---- global pause -------------------------------------------------------------

(def pause-listeners (atom []))

(defn set-paused! [paused?]
  (set! (.-paused js/window.htmlChannel) paused?)
  (when-let [b (js/document.getElementById "hc-pause")]
    (.setAttribute b "aria-pressed" (str paused?))
    (set! (.-textContent b) (if paused? "▶ Play animations" "⏸ Pause animations")))
  (doseq [f @pause-listeners] (f paused?))
  (emit! js/document "hc-pause" {:paused paused?}))

(defn add-pause-button! []
  (let [b (js/document.createElement "button")]
    (set! (.-id b) "hc-pause")
    (set! (.-type b) "button")
    (.addEventListener b "click" (fn [_] (set-paused! (not (.-paused js/window.htmlChannel)))))
    (.append js/document.body b)))

;; ---- scrubbable numbers --------------------------------------------------------

(defn start-scrub! [report! el]
  (when-let [target (target-of report! el "data-target" (.. el -dataset -target))]
    (let [name (.. el -dataset -scrub)
          lo (num-attr el "data-min" 0)
          hi (num-attr el "data-max" 100)
          step (num-attr el "data-step" 1)
          value (atom (num-attr el "data-value" (js/parseFloat (.-textContent el))))
          show! (fn []
                  (set! (.-textContent el) (.toFixed @value (decimals step)))
                  (.setAttribute el "aria-valuenow" (str @value))
                  (emit! target "hc-param" {:name name :value @value}))
          set-value! (fn [v]
                       ;; rounded to the step's decimals: 0.5 + 17 × 0.01 is 0.6700000000000002
                       (let [snapped (js/Number (.toFixed (+ lo (* step (js/Math.round (/ (- v lo) step)))) (decimals step)))
                             v (js/Math.min hi (js/Math.max lo snapped))]
                         (when (not= v @value)
                           (reset! value v)
                           (show!))))]
      (when (js/isNaN @value) (reset! value lo))
      ;; a state link that names this number moves it too
      (.addEventListener target "hc-state"
                         (fn [e]
                           (let [v (aget (.-detail e) name)]
                             (when (number? v) (set-value! v)))))
      (set! (.-tabIndex el) 0)
      (doseq [[k v] [["role" "slider"] ["aria-valuemin" (str lo)] ["aria-valuemax" (str hi)]]]
        (.setAttribute el k v))
      (when-not (.hasAttribute el "aria-label") (.setAttribute el "aria-label" name))
      (.addEventListener el "keydown"
                         (fn [e]
                           (when-let [v (let [k (.-key e)]
                                          (cond (or (= k "ArrowRight") (= k "ArrowUp")) (+ @value step)
                                                (or (= k "ArrowLeft") (= k "ArrowDown")) (- @value step)
                                                (= k "Home") lo
                                                (= k "End") hi))]
                             (.preventDefault e)
                             (set-value! v))))
      ;; one step per 4 px of horizontal drag, relative to where the drag began
      (.addEventListener el "pointerdown"
                         (fn [e]
                           (.preventDefault e)
                           (.setPointerCapture el (.-pointerId e))
                           (let [x0 (.-clientX e) v0 @value
                                 move (fn [m] (set-value! (+ v0 (* step (js/Math.round (/ (- (.-clientX m) x0) 4))))))
                                 up (fn up [_]
                                      (.removeEventListener el "pointermove" move)
                                      (.removeEventListener el "pointerup" up)
                                      (.removeEventListener el "pointercancel" up))]
                             (.addEventListener el "pointermove" move)
                             (.addEventListener el "pointerup" up)
                             (.addEventListener el "pointercancel" up))))
      (show!))))

;; ---- state links ----------------------------------------------------------------

(defn start-state-link! [report! el]
  (when-let [target (target-of report! el "data-set" (.. el -dataset -set))]
    (let [state (try (js/JSON.parse (or (.. el -dataset -state) "{}"))
                     (catch :default e
                       (report! "explorable" (str "<a data-set=\"" (.. el -dataset -set) "\">: data-state isn't JSON: "
                                                  (.-message e))
                                "data-state")
                       nil))]
      (when state
        (when-not (.hasAttribute el "href") (.setAttribute el "href" "#"))
        (.addEventListener el "click"
                           (fn [e]
                             (.preventDefault e)
                             (emit! target "hc-state" state)))))))

;; ---- timelines --------------------------------------------------------------------

(defn el! [tag attrs text]
  (let [e (js/document.createElement tag)]
    (doseq [[k v] (js/Object.entries attrs)] (.setAttribute e k v))
    (when text (set! (.-textContent e) text))
    e))

(defn start-timeline! [report! el]
  (when-let [target (target-of report! el "data-timeline" (.. el -dataset -timeline))]
    (let [duration (num-attr el "data-duration" 4)
          step (num-attr el "data-step" (/ duration 100))
          loop? (not= "false" (.. el -dataset -loop))
          ;; :frame is the pending animation frame, so pausing cancels it and playing never runs two loops
          state (atom {:t 0 :playing false :resume false :speed 1 :last nil :frame nil})
          play (el! "button" {:type "button" :class "hc-tl-play"} nil)
          back (el! "button" {:type "button" :aria-label "Step back"} "⏮")
          fwd (el! "button" {:type "button" :aria-label "Step forward"} "⏭")
          bar (el! "input" {:type "range" :min "0" :max (str duration) :step "any" :aria-label "Time"} nil)
          speed (el! "select" {:aria-label "Speed"} nil)
          readout (el! "output" {} nil)
          show! (fn []
                  (let [{:keys [t playing]} @state]
                    (set! (.-value bar) (str t))
                    (set! (.-textContent readout) (str (.toFixed t 2) " s"))
                    (set! (.-textContent play) (if playing "⏸" "▶"))
                    (.setAttribute play "aria-label" (if playing "Pause" "Play"))
                    (emit! target "hc-time" {:t t :playing playing})))
          seek! (fn [t]
                  (swap! state assoc :t (js/Math.min duration (js/Math.max 0 t)))
                  (show!))
          tick (fn tick [now]
                 (let [{:keys [playing last t speed]} @state]
                   (when playing
                     (let [t' (+ t (* speed (/ (- now (or last now)) 1000)))
                           t' (cond (< t' duration) t'
                                    loop? (mod t' duration)
                                    :else duration)]
                       (swap! state assoc :t t' :last now :playing (or loop? (< t' duration)) :frame nil)
                       (show!)
                       (when (:playing @state)
                         (swap! state assoc :frame (js/requestAnimationFrame tick)))))))
          set-playing! (fn [p]
                         (when (not= p (:playing @state))
                           (some-> (:frame @state) js/cancelAnimationFrame)
                           ;; playing from the end of a timeline that doesn't loop starts over
                           (when (and p (>= (:t @state) duration)) (swap! state assoc :t 0))
                           (swap! state assoc :playing p :last nil
                                  :frame (when p (js/requestAnimationFrame tick)))
                           (show!)))
          ;; the reader's own play, pause or seek outranks resuming after the global pause
          by-reader! (fn [f] (fn [e] (swap! state assoc :resume false) (f e)))]
      (doseq [s [0.25 0.5 1 2]]
        (let [o (el! "option" {:value (str s)} (str s "×"))]
          (set! (.-selected o) (= s 1))
          (.append speed o)))
      (.add (.-classList el) "hc-timeline")
      (.append el play back fwd bar readout speed)
      (.addEventListener play "click" (by-reader! (fn [_] (set-playing! (not (:playing @state))))))
      (.addEventListener back "click" (by-reader! (fn [_] (set-playing! false) (seek! (- (:t @state) step)))))
      (.addEventListener fwd "click" (by-reader! (fn [_] (set-playing! false) (seek! (+ (:t @state) step)))))
      (.addEventListener bar "input" (by-reader! (fn [_] (set-playing! false) (seek! (js/parseFloat (.-value bar))))))
      (.addEventListener speed "change" (fn [_] (swap! state assoc :speed (js/parseFloat (.-value speed)))))
      (swap! pause-listeners conj
             (fn [paused?]
               (if paused?
                 (do (swap! state assoc :resume (:playing @state)) (set-playing! false))
                 (when (:resume @state) (swap! state assoc :resume false) (set-playing! true)))))
      (show!)
      (if (or (.-paused js/window.htmlChannel) (= "false" (.. el -dataset -autoplay)))
        (swap! state assoc :resume (not= "false" (.. el -dataset -autoplay)))
        (set-playing! true)))))

;; ---- start ----------------------------------------------------------------------

(defn start!
  "Pre: the DOM is parsed and window.htmlChannel exists. `report!` is (fn [kind message source])."
  [report!]
  (let [timelines (js/Array.from (js/document.querySelectorAll "[data-timeline]"))]
    (set! (.-paused js/window.htmlChannel)
          (.-matches (js/matchMedia "(prefers-reduced-motion: reduce)")))
    (set! (.-setPaused js/window.htmlChannel) set-paused!)
    (when (or (explorable?) (seq timelines))
      (add-pause-button!)
      (set-paused! (.-paused js/window.htmlChannel)))
    (doseq [el (js/document.querySelectorAll "[data-scrub]")] (start-scrub! report! el))
    (doseq [el (js/document.querySelectorAll "a[data-set]")] (start-state-link! report! el))
    (doseq [el timelines] (start-timeline! report! el))))
