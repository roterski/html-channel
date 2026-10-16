(ns decide.core
  "Transport-independent runtime for html-channel pages. See CONVENTION.md.

  Pages carry only markup:
    <button data-decision=\"x\" data-choice=\"a\">     one choice per decision id
    <input|textarea|select data-decision=\"y\">       free-form value, saved on change
    <input type=checkbox data-decision=\"z\">         saved as true/false
    <button data-deeper=\"target\">                  a request to go deeper, saved as id \"deeper\"
    <div id=\"decide-offline\">…</div>                visible until the server answers

  Page JS records an answer with window.htmlChannel.record(id, value), or by
  setting a control's value and dispatching a bubbling `change` event.

  A transport is {:page id
                  :send! (fn [entry]) -> Promise<bool>
                  :load  (fn [])      -> Promise<{rev done deeper decisions} | nil>
                  :rev   (fn [])      -> Promise<int | nil>}  ; nil = unreachable

  Element states: data-state=\"pending\" until the transport confirms, then
  \"saved\"; \"carried\" for an answer restored from an earlier revision.")

(def transport (atom nil))

;; :rev      revision the page was loaded at (nil until first load)
;; :carried  ids whose shown value comes from an earlier revision
(def state (atom {:online nil :queue [] :flushing false :rev nil :carried []}))

(defn els-for [id]
  (js/document.querySelectorAll (str "[data-decision=\"" (js/CSS.escape id) "\"]")))

(defn mark! [id s]
  (doseq [el (els-for id)] (set! (.. el -dataset -state) s)))

(defn set-online! [online?]
  (when (not= online? (:online @state))
    (swap! state assoc :online online?)
    (when-let [b (js/document.getElementById "decide-offline")]
      (set! (.-hidden b) online?))
    (.toggle (.-classList js/document.documentElement) "decide-is-offline" (not online?))))

;; ---- queue -----------------------------------------------------------------

(defn ^:async flush!
  "Sends queued entries in order. Resolves true when the queue is empty."
  []
  (if (:flushing @state)
    false
    (do
      (swap! state assoc :flushing true)
      (loop []
        (when-let [entry (first (:queue @state))]
          (if (js-await ((:send! @transport) entry))
            (do (swap! state update :queue #(vec (rest %)))
                (when-not (some #(= (:id %) (:id entry)) (:queue @state))
                  (mark! (:id entry) "saved"))
                (recur))
            (set-online! false))))
      (swap! state assoc :flushing false)
      (empty? (:queue @state)))))

(defn remove-clear-button! []
  (some-> (js/document.getElementById "decide-clear-carried") .remove))

(defn uncarry! [id]
  (swap! state update :carried (fn [ids] (vec (remove #(= % id) ids))))
  (when (empty? (:carried @state)) (remove-clear-button!)))

(defn record! [id value]
  (uncarry! id)
  (swap! state update :queue conj
         {:page (:page @transport) :id id :value value :rev (:rev @state)
          :at (.toISOString (js/Date.))})
  (mark! id "pending")
  (flush!))

;; ---- element values ----------------------------------------------------------

(defn el-value [el]
  (if (= "checkbox" (.-type el)) (.-checked el) (.-value el)))

(defn show-value!
  "Shows `v` on `el`; nil empties it."
  [el v]
  (cond
    (= "BUTTON" (.-tagName el)) (.toggle (.-classList el) "chosen" (= v (.. el -dataset -choice)))
    (= "checkbox" (.-type el)) (set! (.-checked el) (boolean v))
    (= "radio" (.-type el)) (set! (.-checked el) (= v (.-value el)))
    :else (set! (.-value el) (if (nil? v) "" v))))

(defn clear-carried! []
  (let [ids (:carried @state)]
    (doseq [id ids]
      (doseq [el (els-for id)] (show-value! el nil))
      (record! id nil))))

(defn insert-clear-button! []
  (when-not (js/document.getElementById "decide-clear-carried")
    (let [b (js/document.createElement "button")]
      (set! (.-id b) "decide-clear-carried")
      (set! (.-type b) "button")
      (set! (.-textContent b) "Clear carried answers")
      (.addEventListener b "click" (fn [_] (clear-carried!)))
      (.prepend js/document.body b))))

(defn show-deeper!
  "Marks the Deeper buttons whose target is in `targets` as chosen."
  [targets]
  (doseq [b (js/document.querySelectorAll "button[data-deeper]")]
    (.toggle (.-classList b) "chosen" (.includes (or targets []) (.. b -dataset -deeper)))))

(defn restore!
  "Shows the effective answers. Pre: `rev` is the page's current revision."
  [decisions rev]
  (let [carried (atom [])]
    (doseq [[id entry] (js/Object.entries decisions)]
      (let [els (els-for id)
            v (:value entry)
            carried? (and (< (:rev entry) rev) (some? v) (pos? (.-length els)))]
        (when carried? (swap! carried conj id))
        (doseq [el els]
          (show-value! el v)
          (set! (.. el -dataset -state) (if carried? "carried" "saved")))))
    (swap! state assoc :carried @carried)
    (if (seq @carried) (insert-clear-button!) (remove-clear-button!))))

;; ---- events ------------------------------------------------------------------

(defn on-click [e]
  (if-let [btn (.closest (.-target e) "button[data-decision][data-choice]")]
    (let [id (.. btn -dataset -decision)]
      (doseq [b (els-for id)] (.remove (.-classList b) "chosen"))
      (.add (.-classList btn) "chosen")
      (record! id (.. btn -dataset -choice)))
    (when-let [btn (.closest (.-target e) "button[data-deeper]")]
      (.add (.-classList btn) "chosen")
      (record! "deeper" (.. btn -dataset -deeper)))))

(defn on-change [e]
  (let [el (.-target e)]
    (when (and (.. el -dataset -decision) (not= "BUTTON" (.-tagName el)))
      (record! (.. el -dataset -decision) (el-value el)))))

;; ---- polling -----------------------------------------------------------------

(defn ^:async load! []
  (when-let [eff (js-await ((:load @transport)))]
    (swap! state assoc :rev (:rev eff))
    (restore! (:decisions eff) (:rev eff))
    (show-deeper! (:deeper eff))))

(defn ^:async check!
  "Health and revision poll: toggles the banner, flushes the queue, loads the
  page's answers once, and reloads when the page has a newer revision."
  []
  (let [rev (js-await ((:rev @transport)))]
    (set-online! (some? rev))
    (when (some? rev)
      (let [drained (if (seq (:queue @state)) (js-await (flush!)) true)
            loaded (:rev @state)]
        (cond
          (nil? loaded) (js-await (load!))
          (and drained (> rev loaded)) (js/location.reload))))))

;; ---- page API ------------------------------------------------------------------

(defn report-error!
  "Reports a page error through /_kit/errors.js; the console when the page lacks it."
  [kind message source]
  (if-let [f (some-> js/window .-htmlChannel .-reportError)]
    (f kind message source)
    (js/console.error "html-channel:" kind message source)))

(defn record-from-page!
  "window.htmlChannel.record: shows `value` on the id's controls and saves it."
  [id value]
  (doseq [el (els-for id)] (show-value! el value))
  (record! id value))

(defn start! [t]
  (reset! transport t)
  (set! js/window.htmlChannel (or js/window.htmlChannel {}))
  (set! (.-record js/window.htmlChannel) record-from-page!)
  (.addEventListener js/document "click" on-click)
  (.addEventListener js/document "change" on-change)
  (check!)
  (js/setInterval check! 3000))
