(ns decide.core
  "Transport-independent runtime for html-channel pages. See CONVENTION.md.

  Pages carry only markup:
    <button data-decision=\"x\" data-choice=\"a\">     one choice per decision id
    <input|textarea|select data-decision=\"y\">       free-form value, saved on change
    <input type=checkbox data-decision=\"z\">         saved as true/false
    <button data-deeper=\"target\">                  a request to go deeper, saved as id \"deeper\"
    <div id=\"decide-offline\">…</div>                visible until the server answers

  Page JS records an answer with window.htmlChannel.record(id, value), or by
  setting a control's value and dispatching a bubbling `change` event. Both
  work before this module starts: /_kit/boot.js keeps them until `start!`.

  A transport is {:page id
                  :session id
                  :mode  \"served\" | \"share\"   ; share: an offline copy, answers kept in the browser
                  :send! (fn [entry]) -> Promise<{outcome \"saved\"|\"rejected\"|\"unreachable\", reason}>
                  :load  (fn [])      -> Promise<{rev done deeper decisions} | nil>
                  :rev   (fn [])      -> Promise<int | nil>}  ; nil = unreachable

  Kit modules that follow answers (decide.moot) register with on-answers-shown!:
  they run after every recorded answer and every restore, once the controls show it.
  Kit modules that report to the agent on Done (code links, diffs, moot) register
  a read with report-on-done!: Done records each read before `status`, and
  done-reports gives the same values without recording them (decide.download).

  Element states: data-state=\"pending\" until the transport confirms, then
  \"saved\"; \"rejected\" when the server refused the answer itself (reported
  as a page error); \"carried\" for an answer restored from an earlier revision.")

(def transport (atom nil))

;; :rev      revision the page was loaded at (nil until first load)
;; :carried  ids whose shown value comes from an earlier revision
;; :flushing the promise of the send in progress, else nil
(def state (atom {:online nil :queue [] :flushing nil :rev nil :carried []}))

;; id -> {:value :rev :at}: every answer the page shows, restored or recorded,
;; whether or not the transport has confirmed it. Read by decide.download.
;; Invariant: no "deeper" key. A Deeper click is a request to the agent, not an
;; answer; the server reports it apart from the answers, under `deeper`.
(def answers (atom {}))

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

;; The unsent queue is mirrored to localStorage, one key per tab, so answers given
;; while the server is away outlive a closed tab: another tab of the page claims
;; them (claim-orphaned-queues!). Each tab refreshes its key's :beat on every poll;
;; a key not refreshed for orphan-after-ms belongs to a closed tab.

(def tab-id (str (js/Date.now) "-" (.slice (str (js/Math.random)) 2 10)))

(def orphan-after-ms 10000)

(defn queue-key-prefix [] (str "html-channel:queue:" js/location.pathname "#"))

(defn queue-key [] (str (queue-key-prefix) tab-id))

(defn mirror-queue!
  "Writes this tab's queue, stamped now, or removes its key when the queue is empty.
  Storage that throws (blocked, full) leaves the queue in memory only."
  []
  (try
    (if (seq (:queue @state))
      (.setItem js/localStorage (queue-key)
                (js/JSON.stringify {:beat (js/Date.now) :queue (:queue @state)}))
      (.removeItem js/localStorage (queue-key)))
    (catch :default _ nil)))

(defn orphaned-entries!
  "Removes the mirrored queues of this page's closed tabs and returns their entries,
  oldest first. A key that doesn't parse counts as orphaned."
  []
  (try
    (let [prefix (queue-key-prefix)
          own (queue-key)
          now (js/Date.now)]
      (->> (js/Object.keys js/localStorage)
           (filter #(and (.startsWith % prefix) (not= % own)))
           (mapcat (fn [k]
                     (let [m (try (js/JSON.parse (.getItem js/localStorage k)) (catch :default _ nil))]
                       (if (and m (< (- now (:beat m)) orphan-after-ms))
                         []
                         (do (.removeItem js/localStorage k) (or (:queue m) []))))))
           (sort-by :at)
           vec))
    (catch :default _ [])))

(defn report-error!
  "Reports a page error through /_kit/boot.js; the console when the page lacks it."
  [kind message source]
  (if-let [f (some-> js/window .-htmlChannel .-reportError)]
    (f kind message source)
    (js/console.error "html-channel:" kind message source)))

(defn ^:async send-queue!
  "Sends queued entries in order, entries queued meanwhile included, until the
  queue is empty or the server is unreachable. Resolves true when the queue is empty.
  An entry the server rejects is dropped and reported: it would fail again on
  every retry, and keep every answer behind it from being saved."
  []
  (loop []
    (when-let [entry (first (:queue @state))]
      (let [{:keys [outcome reason]} (js-await ((:send! @transport) entry))
            id (:id entry)
            settled! (fn [s]
                       (swap! state update :queue #(vec (rest %)))
                       (mirror-queue!)
                       (when-not (some #(= (:id %) id) (:queue @state))
                         (mark! id s)))]
        (case outcome
          "saved" (do (settled! "saved") (recur))
          "rejected" (do (settled! "rejected")
                         (report-error! "js" (str "answer " (js/JSON.stringify id) " rejected by "
                                                  (if (= "share" (:mode @transport)) "this browser" "the server")
                                                  ": " reason)
                                        nil)
                         (recur))
          (set-online! false)))))
  (empty? (:queue @state)))

(defn flush!
  "Sends the queue. Resolves true when it is empty.
  A call while a send is in progress joins it rather than returning early, so
  whoever awaits flush! knows the server has every entry queued so far."
  []
  (or (:flushing @state)
      (let [p (.finally (send-queue!) #(swap! state assoc :flushing nil))]
        (swap! state assoc :flushing p)
        p)))

(defn remove-clear-button! []
  (some-> (js/document.getElementById "decide-clear-carried") .remove))

(defn uncarry! [id]
  (swap! state update :carried (fn [ids] (vec (remove #(= % id) ids))))
  (when (empty? (:carried @state)) (remove-clear-button!)))

;; fns of no arguments, called once the controls show a changed or restored answer
(def answer-listeners (atom []))

(defn on-answers-shown!
  "Calls `f` after every recorded answer and every restore, once the controls show it."
  [f]
  (swap! answer-listeners conj f))

(defn answers-shown! []
  (doseq [f @answer-listeners] (f)))

;; fns of [id value], called on every recorded answer (not on restores)
(def record-listeners (atom []))

(defn on-record!
  "Calls `(f id value)` each time an answer is recorded: a choice, an edit, a
  Clear, htmlChannel.record, or Done's reports and status. Restores don't call it."
  [f]
  (swap! record-listeners conj f))

(defn record!
  "Pre: the id's controls already show `value` (every caller sets them first)."
  [id value]
  (uncarry! id)
  (let [entry {:page (:page @transport) :id id :value value :rev (:rev @state)
               :at (.toISOString (js/Date.))}]
    (swap! state update :queue conj entry)
    (when-not (= "deeper" id)
      (swap! answers assoc id {:value value :rev (:rev entry) :at (:at entry)})))
  (mirror-queue!)
  (mark! id "pending")
  (doseq [f @record-listeners] (f id value))
  (answers-shown!)
  (flush!))

;; ---- reports on Done ---------------------------------------------------------

;; [[id read] …] in registration order; read returns the value to report, or nil
(def done-report-reads (atom []))

(defn report-on-done!
  "Registers `read`, a fn of no arguments, as the value of report-only `id`:
  recorded on Done, before `status`, and read by Download. nil reports nothing."
  [id read]
  (swap! done-report-reads conj [id read]))

(defn done-reports
  "{id value} for every registered read that has something to report now."
  []
  (reduce (fn [m [id read]]
            (let [v (read)]
              (if (nil? v) m (assoc m id v))))
          {} @done-report-reads))

(defn record-done-reports! []
  (doseq [[id v] (js/Object.entries (done-reports))]
    (record! id v)))

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

(def report-only-ids
  "Ids the runtime records for the agent but never shows back on the page:
  the code-link settings live in the reader's browser (decide.code-links), the
  hunk map describes the page's diffs (decide.diff), and the moot map what the
  page's links made moot at Done (decide.moot)."
  #{"sources" "hunks" "moot"})

(defn show-entry!
  "Shows saved `entry` on `els`. True when it is carried from an earlier revision.
  Pre: `rev` is the page's current revision."
  [els entry rev]
  (let [v (:value entry)
        carried? (and (< (:rev entry) rev) (some? v) (pos? (.-length els)))]
    (doseq [el els]
      (show-value! el v)
      (set! (.. el -dataset -state) (if carried? "carried" "saved")))
    carried?))

(defn restore!
  "Shows the effective answers, and keeps them for controls added later
  (restore-within!). Pre: `rev` is the page's current revision."
  [decisions rev]
  (swap! state assoc :loaded {:decisions decisions :rev rev})
  (doseq [[id entry] (js/Object.entries decisions)]
    (swap! answers assoc id (select-keys entry [:value :rev :at])))
  (let [carried (atom [])]
    (doseq [[id entry] (js/Object.entries decisions)
            :when (not (contains? report-only-ids id))]
      (when (show-entry! (els-for id) entry rev) (swap! carried conj id)))
    (swap! state assoc :carried @carried)
    (if (seq @carried) (insert-clear-button!) (remove-clear-button!)))
  (answers-shown!))

(defn restore-within!
  "Shows the loaded answers on the controls under `root`, for controls page code
  or the kit added after the first load. A no-op before it: restore! covers them.
  Pre: no control under `root` was answered since it was added."
  [root]
  (when-let [{:keys [decisions rev]} (:loaded @state)]
    (let [ids (distinct (map #(.. % -dataset -decision)
                             (js/Array.from (.querySelectorAll root "[data-decision]"))))]
      (doseq [id ids
              :let [entry (aget decisions id)]
              :when (and entry (not (contains? report-only-ids id)))]
        (let [els (.querySelectorAll root (str "[data-decision=\"" (js/CSS.escape id) "\"]"))]
          (when (and (show-entry! els entry rev) (not (.includes (:carried @state) id)))
            (swap! state update :carried conj id)
            (insert-clear-button!))))
      (answers-shown!))))

;; ---- events ------------------------------------------------------------------

(defn clear! [id]
  (doseq [el (els-for id)] (show-value! el nil))
  (record! id nil))

(defn on-click
  "A choice records its value; clicking the chosen one again clears it, except
  Done, which first records the registered reports (report-on-done!).
  <button data-clear=\"ID\"> clears ID, leaving its note."
  [e]
  (let [t (.-target e)]
    (if-let [btn (.closest t "button[data-decision][data-choice]")]
      (let [id (.. btn -dataset -decision)]
        (if (and (.contains (.-classList btn) "chosen") (not= "status" id))
          (clear! id)
          (do (doseq [b (els-for id)] (.remove (.-classList b) "chosen"))
              (.add (.-classList btn) "chosen")
              (when (and (= "status" id) (= "done" (.. btn -dataset -choice)))
                (record-done-reports!))
              (record! id (.. btn -dataset -choice)))))
      (if-let [btn (.closest t "button[data-clear]")]
        (clear! (.. btn -dataset -clear))
        (when-let [btn (.closest t "button[data-deeper]")]
          (.add (.-classList btn) "chosen")
          (record! "deeper" (.. btn -dataset -deeper)))))))

(defn on-change [e]
  (let [el (.-target e)]
    (when (and (.. el -dataset -decision) (not= "BUTTON" (.-tagName el)))
      (record! (.. el -dataset -decision) (el-value el)))))

;; ---- closed tabs' queues -------------------------------------------------

(defn claim-orphaned-queues!
  "Queues the unsent answers of this page's closed tabs (orphaned-entries!) and
  shows them as pending, skipping any entry older than the answer the page already
  shows for its id, so a claim never overrides a newer answer.
  Pre: the page has loaded its saved answers (:rev is set), so the comparison sees
  them; called before check! flushes, so claimed entries are sent with this tab's own."
  []
  (let [claimed (filter (fn [entry]
                          (let [shown (:at (get @answers (:id entry)))]
                            (not (and shown (> shown (:at entry))))))
                        (orphaned-entries!))]
    (when (seq claimed)
      (doseq [entry claimed
              :let [id (:id entry)]]
        (doseq [el (els-for id)] (show-value! el (:value entry)))
        (swap! state update :queue conj entry)
        (when-not (= "deeper" id)
          (swap! answers assoc id (select-keys entry [:value :rev :at])))
        (mark! id "pending"))
      (mirror-queue!)
      (answers-shown!))))

(def claim-lock-hold-ms
  "How long a tab keeps the claim lock after claiming: one tab's localStorage
  change reaches other tabs shortly after, not at once."
  250)

(defn ^:async claim-orphaned-queues-once!
  "claim-orphaned-queues! under a Web Lock all tabs of the page share, so two tabs
  never claim the same queue. A tab that finds the lock taken skips this poll; the
  next one retries. Without Web Locks, claims unguarded.
  Pre: as claim-orphaned-queues!."
  []
  (if-let [locks (some-> js/navigator .-locks)]
    (js-await (.request locks (str "html-channel:claim:" js/location.pathname) {:ifAvailable true}
                        (fn [lock]
                          (when lock
                            (claim-orphaned-queues!)
                            (js/Promise. (fn [resolve] (js/setTimeout resolve claim-lock-hold-ms)))))))
    (claim-orphaned-queues!)))

;; ---- polling -----------------------------------------------------------------

(defn ^:async load! []
  (when-let [eff (js-await ((:load @transport)))]
    (swap! state assoc :rev (:rev eff))
    (restore! (:decisions eff) (:rev eff))
    (show-deeper! (:deeper eff))))

(defn ^:async check!
  "Health and revision poll: once the page has loaded its answers, claims closed
  tabs' unsent answers; refreshes this tab's mirror, toggles the banner, flushes
  the queue, loads the page's answers once, and reloads when the page has a newer
  revision."
  []
  (when (some? (:rev @state)) (js-await (claim-orphaned-queues-once!)))
  (when (seq (:queue @state)) (mirror-queue!))
  (let [rev (js-await ((:rev @transport)))]
    (set-online! (some? rev))
    (when (some? rev)
      (let [drained (if (seq (:queue @state)) (js-await (flush!)) true)
            loaded (:rev @state)]
        (cond
          (nil? loaded) (js-await (load!))
          (and drained (> rev loaded)) (js/location.reload))))))

;; ---- page API ------------------------------------------------------------------

(defn record-from-page!
  "window.htmlChannel.record: shows `value` on the id's controls and saves it."
  [id value]
  (doseq [el (els-for id)] (show-value! el value))
  (record! id value))

(defn save-early-answers!
  "Saves what page JS answered before this runtime started (kept by /_kit/boot.js),
  then stops boot.js from keeping more. A changed control is read now, so it
  saves its latest value."
  []
  (when-let [early (.-early js/window.htmlChannel)]
    (set! (.-early js/window.htmlChannel) nil)
    (doseq [a early]
      (if-let [el (.-el a)]
        (record! (.. el -dataset -decision) (el-value el))
        (record-from-page! (.-id a) (.-value a))))))

(defn start! [t]
  (reset! transport t)
  (.toggle (.-classList js/document.documentElement) "decide-share" (= "share" (:mode t)))
  (set! js/window.htmlChannel (or js/window.htmlChannel {}))
  (set! (.-record js/window.htmlChannel) record-from-page!)
  (save-early-answers!)
  (.addEventListener js/document "click" on-click)
  (.addEventListener js/document "change" on-change)
  (check!)
  (js/setInterval check! 3000))
