(ns decide.statechart
  "The statechart control: a chart the reader drives. See STATECHART.md.

    <figure data-statechart data-instrument=\"ID\">
      <script type=\"application/json\">{ XState-style definition }</script>
    </figure>
    <figure data-statechart data-instrument=\"ID\" data-machine=\"DEF\" data-collapse=\"a,b\"></figure>
        draws the shared <script type=\"application/json\" id=\"DEF\">, with the
        listed composite states drawn as single states
    data-renderer=\"mermaid|smcat\"   forces a renderer; otherwise state-machine-cat
                                      draws charts with history states, Mermaid the rest

  The kit adds, inside the figure: the \"Inferred from code\" line (meta.inferred),
  the chart, a button per event the active states take (disabled while every guard
  on it is false; clicking a transition label presses it), a context panel, the event log, and the \"Where in the code\" list
  (meta.code references, as <code data-src>, which decide.code-links links).
  XState v5 runs the definition. Delayed transitions run on a clock that stops
  while window.htmlChannel.paused.

  The log has one entry per event and per context-panel edit (consecutive edits of
  one key merge). Listens on the figure for hc-param (a data-scrub sets the context
  key it names in the start, and the log is replayed from it) and hc-state with {\"events\": [...]} (a data-set link resets and replays them).
  After each change it sets data-active on the figure (the highlighted uids, as JSON) and
  sends hc-statechart {value, context, event}.

  Reports, as kind \"statechart\" with the rule as the source: statechart-json,
  statechart-target, statechart-guard, statechart-unreachable, statechart-history,
  statechart-machine, statechart-instrument."
  (:require [decide.statechart-diagram :as diagram]
            [decide.statechart-machine :as sm]
            [viz.load :as load]
            [viz.mermaid :as mermaid]
            [viz.versions :as versions]))

(def kind "statechart")

(defn present? [] (some? (js/document.querySelector "[data-statechart]")))

(defn el! [tag attrs & children]
  (let [e (js/document.createElement tag)]
    (doseq [[k v] (js/Object.entries attrs)] (when (some? v) (.setAttribute e k v)))
    (doseq [c children] (when (some? c) (.append e c)))
    e))

(defn chart-name [fig i]
  (or (not-empty (.. fig -dataset -instrument)) (not-empty (.-id fig)) (str "#" i)))

;; ---- colors ---------------------------------------------------------------------

(defn hex
  "#rrggbb for a computed rgb()/rgba() color, else the color as is."
  [c]
  (if-let [m (.exec #"rgba?\((\d+),\s*(\d+),\s*(\d+)" c)]
    (str "#" (.join (vec (for [i [1 2 3]] (.padStart (.toString (js/Number (aget m i)) 16) 2 "0"))) ""))
    c))

(defn token
  "The kit color token `name` as #rrggbb, resolved inside `el`."
  [el name]
  (let [probe (el! "span" {"style" (str "color: var(" name ")")})]
    (.append el probe)
    (let [c (.-color (js/getComputedStyle probe))]
      (.remove probe)
      (hex c))))

(defn mermaid-theme
  "Mermaid themeVariables from the kit's tokens, so the chart follows light and dark."
  [el]
  (let [t #(token el %)
        bg (t "--hc-bg") fg (t "--hc-fg") card (t "--hc-card") muted (t "--hc-muted")]
    {:background bg :primaryColor card :primaryTextColor fg :primaryBorderColor muted
     :secondaryColor bg :tertiaryColor bg :lineColor fg :textColor fg :mainBkg card
     :nodeBorder muted :clusterBkg bg :clusterBorder muted :titleColor fg
     :stateBkg card :stateLabelColor fg :compositeBackground bg :compositeTitleBackground card
     :altBackground bg :compositeBorder muted :innerEndBackground fg :specialStateColor fg
     :transitionColor fg :transitionLabelColor fg :labelBackgroundColor bg :edgeLabelBackground bg}))

;; ---- libraries ----------------------------------------------------------------

(def smcat-module (atom nil))

(defn ^:async smcat! []
  (or @smcat-module
      (let [m (js-await (js/import versions/state-machine-cat))]
        (reset! smcat-module m)
        m)))

(defn ^:async xstate! []
  (js-await (load/scripts! versions/xstate))
  js/window.XState)

;; ---- one chart ------------------------------------------------------------------

(defn renderer-of
  "\"mermaid\" or \"smcat\": the figure's data-renderer, else smcat for charts with history."
  [fig m]
  (let [forced (.. fig -dataset -renderer)]
    (cond (or (= "mermaid" forced) (= "smcat" forced)) forced
          (sm/has-history? m) "smcat"
          :else "mermaid")))

(defn state-label [m uid] (.join (sm/path-of uid) "."))

(defn code-entries
  "[{:uid :label :refs}] for the states and transitions with meta.code, in definition order."
  [{:keys [idx] :as m}]
  (vec (concat
        (for [n (js/Object.values idx)
              :when (and (not= "" (:uid n)) (seq (:code n)))]
          {:uid (:uid n) :label (state-label m (:uid n)) :refs (:code n)})
        (for [n (js/Object.values idx)
              t (:transitions n)
              :when (seq (:code t))]
          {:uid nil
           :label (str (or (:event t) "always") ": "
                       (if (= "" (:uid n)) "any state" (state-label m (:uid n))) " → "
                       (.join (vec (map #(state-label m %) (:targets t))) ", "))
           :refs (:code t)}))))

(defn build-ui!
  "Adds the control's parts to `fig`; returns them."
  [fig m definition]
  (let [inferred (some-> definition (aget "meta") (aget "inferred") (aget "basis"))
        chart (el! "div" {"class" "hc-sc-chart"})
        events (el! "div" {"class" "hc-sc-events" "role" "group" "aria-label" "Send an event"})
        ctx-keys (js/Object.keys (:context m))
        context (when (pos? (count ctx-keys))
                  (el! "fieldset" {"class" "hc-sc-context"} (el! "legend" {} "Context")))
        reset (el! "button" {"type" "button" "class" "hc-sc-reset"} "Reset")
        log (el! "ol" {"class" "hc-sc-log-entries"})
        log-box (el! "div" {"class" "hc-sc-log"}
                     (el! "div" {"class" "hc-sc-log-head"} (el! "span" {} "Event log") reset)
                     log)
        refs (code-entries m)
        code (when (seq refs)
               (el! "div" {"class" "hc-sc-code"}
                    (el! "p" {} "Where in the code")
                    (apply el! "ul" {}
                           (for [{:keys [uid label refs]} refs]
                             (apply el! "li" {"data-uid" uid}
                                    (el! "span" {} label)
                                    (for [r refs] (el! "code" {"data-src" r} r)))))))]
    (when inferred (.append fig (el! "p" {"class" "hc-sc-inferred"} (str "Inferred from code: " inferred))))
    (.append fig chart events)
    (when context (.append fig context))
    (.append fig log-box)
    (when code (.append fig code))
    {:chart chart :events events :context context :reset reset :log log :code code}))

(defn context-inputs!
  "One labeled control per context key; `on-change` gets the key and the new value."
  [fieldset context enums on-change]
  (doseq [[k v] (js/Object.entries context)]
    (let [opts (when enums (aget enums k))
          input (cond
                  (js/Array.isArray opts)
                  (apply el! "select" {"name" k} (for [o opts] (el! "option" {"value" (str o)} (str o))))
                  (boolean? v) (el! "input" {"type" "checkbox" "name" k})
                  (number? v) (el! "input" {"type" "number" "step" "any" "name" k})
                  :else (el! "input" {"type" "text" "name" k}))
          value-of (fn []
                     (cond (= "checkbox" (.-type input)) (.-checked input)
                           (number? v) (js/parseFloat (.-value input))
                           (js/Array.isArray opts) (or (some #(when (= (str %) (.-value input)) %) opts) (.-value input))
                           :else (.-value input)))]
      (.addEventListener input "change"
                         (fn [_] (let [x (value-of)]
                                   (when-not (and (number? x) (js/isNaN x))
                                     (on-change k x)))))
      (.append fieldset (el! "label" {} (el! "span" {} k) input)))))

(defn show-context! [fieldset context]
  (when fieldset
    (doseq [[k v] (js/Object.entries context)]
      (when-let [input (.querySelector fieldset (str "[name=\"" (js/CSS.escape k) "\"]"))]
        (when-not (= input js/document.activeElement)
          (if (= "checkbox" (.-type input))
            (set! (.-checked input) (boolean v))
            (set! (.-value input) (str v))))))))

;; ---- the clock for delayed transitions --------------------------------------------

(defn paused? [] (boolean (some-> js/window .-htmlChannel .-paused)))

(defn clear-timers! [st]
  (doseq [t (:timers @st)] (some-> (:handle t) js/clearTimeout))
  (swap! st assoc :timers []))

(defn arm! [st t send!]
  (assoc t :started (js/performance.now)
         :handle (js/setTimeout (fn [] (send! {:type (:event t)})) (:remaining t))))

(defn start-timers!
  "One timer per delayed transition of the active states; they wait while paused."
  [st delays send!]
  (clear-timers! st)
  (swap! st assoc :timers
         (vec (for [{:keys [event delay]} delays]
                (let [t {:event event :remaining delay}]
                  (if (paused?) t (arm! st t send!)))))))

(defn pause-timers! [st paused send!]
  (swap! st update :timers
         (fn [ts]
           (vec (for [t ts]
                  (if paused
                    (if-let [h (:handle t)]
                      (do (js/clearTimeout h)
                          (assoc t :handle nil :remaining (js/Math.max 0 (- (:remaining t) (- (js/performance.now) (:started t))))))
                      t)
                    (if (:handle t) t (arm! st t send!))))))))

;; ---- running ----------------------------------------------------------------------

(defn tag-states!
  "Sets data-uid on each drawn state's group in `chart`. Mermaid ids its groups
  ...-state-<diagram-id>-<n>; state-machine-cat titles them <diagram-id> or cluster_<diagram-id>."
  [chart m]
  (doseq [g (js/Array.from (.querySelectorAll chart "g[id*='-state-']"))]
    (when-let [mt (.exec #"-state-(s\d+)-\d+$" (.-id g))]
      (when-let [u (sm/uid-of-diagram-id m (aget mt 1))] (.setAttribute g "data-uid" u))))
  (doseq [g (js/Array.from (.querySelectorAll chart "g.node, g.cluster"))]
    (when-let [t (some-> (.querySelector g ":scope > title") .-textContent)]
      (when-let [u (sm/uid-of-diagram-id m (.replace t #"^cluster_" ""))] (.setAttribute g "data-uid" u)))))

(defn mark-event-labels!
  "Marks each transition label in the drawn chart whose event is one of `events`
  with data-hc-event, so a click on it can send the event. A label reads
  \"EVENT\" or \"EVENT [guard]\". Mermaid's labels are .edgeLabel; state-machine-cat's
  are the <text> of a g.edge, whose <title> (\"a->b\") isn't part of the label."
  [chart events]
  (let [mark! (fn [el text]
                (let [ev (.trim (first (.split (.trim text) " [")))]
                  (when (.includes events ev) (.setAttribute el "data-hc-event" ev))))]
    (doseq [l (js/Array.from (.querySelectorAll chart ".edgeLabel"))]
      (mark! l (.-textContent l)))
    (doseq [g (js/Array.from (.querySelectorAll chart "g.edge"))]
      (mark! g (.join (vec (map #(.-textContent %) (js/Array.from (.querySelectorAll g "text")))) " ")))))

(defn ^:async draw!
  "Draws the chart with the active states marked. Only the latest draw lands."
  [fig st]
  (let [{:keys [m active renderer ui n-draw i]} @st
        n (inc (or n-draw 0))
        chart (:chart ui)]
    (swap! st assoc :n-draw n)
    (try
      (let [svg (if (= "smcat" renderer)
                  (js-await (.render (js-await (smcat!)) (diagram/smcat m active) {:outputType "svg"}))
                  (let [mm (js-await (mermaid/ready!))
                        src (diagram/mermaid m active (mermaid-theme fig) (token fig "--hc-accent"))]
                    (.-svg (js-await (.render mm (str "hc-sc-" i "-" n) src)))))]
        (when (= n (:n-draw @st))
          (set! (.-className chart) (str "hc-sc-chart hc-sc-" renderer))
          (set! (.-innerHTML chart) svg)
          (tag-states! chart m)
          (mark-event-labels! chart (sm/events-of m (:active @st)))
          (when (= "smcat" renderer)
            (.setProperty (.-style chart) "--hc-sc-accent" (token fig "--hc-accent")))))
      (catch :default e
        (when (= n (:n-draw @st))
          (set! (.-textContent chart) (str "The chart couldn't be drawn: " (or (.-message e) e)))
          (js/console.error "html-channel statechart:" e))))))

(defn show!
  "Shows the actor's current snapshot: chart, buttons, context, log, data-active."
  [fig st event send!]
  (let [{:keys [m actor ui log cursor]} @st
        snap (.getSnapshot actor)
        active (sm/active-uids m (.-value snap))
        hl (sm/highlighted m active)]
    (swap! st assoc :active active)
    (.setAttribute fig "data-active" (js/JSON.stringify hl))
    (draw! fig st)
    ;; event buttons, updated in place: a click that blurs a context field (whose
    ;; change redraws) must still land on a button that is in the page
    (let [box (:events ui)
          evs (sm/events-of m active)
          old (into {} (for [b (js/Array.from (.-children box))] [(.. b -dataset -event) b]))]
      (doseq [[e b] (js/Object.entries old)]
        (when-not (.includes evs e) (.remove b)))
      (let [bs (vec (for [e evs]
                      (let [b (or (get old e)
                                  (doto (el! "button" {"type" "button" "data-event" e} e)
                                    (.addEventListener "click" (fn [_] (send! {:type e})))))]
                        (set! (.-disabled b) (not (.can snap #js {:type e})))
                        b)))
            order (fn [] (.join (vec (map #(.. % -dataset -event) (js/Array.from (.-children box)))) "\n"))]
        ;; moving a button cancels a click in progress, so move them only when the order changed
        (when (not= (order) (.join evs "\n"))
          (doseq [b bs] (.append box b)))))
    (show-context! (:context ui) (.-context snap))
    ;; log, redrawn only when it or the cursor changed
    (when-not (and (identical? log (:shown-log @st)) (= cursor (:shown-cursor @st)))
      (swap! st assoc :shown-log log :shown-cursor cursor)
      (let [ol (:log ui)
            entry (fn [idx text edit?]
                    (let [b (el! "button" {"type" "button"} text)
                          place (cond (= idx cursor) "hc-sc-current" (> idx cursor) "hc-sc-later")
                          li (el! "li" {"class" (cond (and place edit?) (str place " hc-sc-edit") edit? "hc-sc-edit" :else place)} b)]
                      (when (= idx cursor) (.setAttribute b "aria-current" "step"))
                      (.addEventListener b "click" (fn [_] ((:go! @st) idx)))
                      li))]
        (set! (.-textContent ol) "")
        (.append ol (entry -1 (str "start → " (.join (vec (map #(state-label m %) (:start-hl @st))) ", ")) false))
        (doseq [[idx {:keys [kind label hl no-effect]}] (map-indexed vector log)]
          (.append ol (entry idx (str label (when no-effect " (no effect)") " → " (.join (vec (map #(state-label m %) hl)) ", "))
                             (= "edit" kind))))))
    (start-timers! st (sm/delays-of m active) send!)
    (.dispatchEvent fig (js/CustomEvent. "hc-statechart"
                                         #js {:detail #js {:value (.-value snap) :context (.-context snap) :event event}}))))

(defn ^:async run!
  "Starts the chart in `fig` from model `m`."
  [fig i m definition renderer pending]
  (let [ui (build-ui! fig m definition)
        st (atom {:m m :ui ui :renderer renderer :i i :log [] :cursor -1 :timers []})
        xs (js-await (xstate!))
        machine (.createMachine xs (sm/engine-config m (.-assign xs)))
        fresh (fn [snapshot] (doto (.createActor xs machine (when snapshot #js {:snapshot snapshot})) (.start)))
        actor (fresh nil)
        start-snap (.getPersistedSnapshot actor)
        hl-of (fn [a] (sm/highlighted m (sm/active-uids m (.-value (.getSnapshot a)))))]
    (swap! st assoc :actor actor :start-snap start-snap :start-hl (hl-of actor))
    ;; The log is a vector of entries, each an event or a context edit:
    ;;   {:kind "event" :label TYPE :ev EVENT}  {:kind "edit" :label "KEY = VALUE" :key KEY :patch {KEY VALUE}}
    ;; plus the outcome of applying it: :snap (the persisted snapshot after it), :hl and
    ;; :no-effect (an event the active states didn't take). :cursor is the current
    ;; entry's index, -1 for the start. Invariant: the actor is at the state after
    ;; entry :cursor.
    (letfn [(replace-actor! [snapshot]
              (.stop (:actor @st))
              (swap! st assoc :actor (fresh snapshot)))
            ;; the start, with the values the prose scrubs have set: a scrub keeps showing
            ;; its number after Reset, so the chart keeps using it
            (start-over! []
              (replace-actor! start-snap)
              (when-let [pc (:prose-context @st)]
                (.send (:actor @st) #js {:type "hc.context" :patch pc}))
              (swap! st assoc :start-hl (hl-of (:actor @st))))
            (restore! [idx]
              (if (neg? idx) (start-over!) (replace-actor! (:snap (nth (:log @st) idx)))))
            (apply-entry! [entry]
              ;; sends `entry` to the actor; returns it with its outcome
              (let [actor (:actor @st)
                    event? (= "event" (:kind entry))
                    no-effect (and event? (not (.can (.getSnapshot actor) (:ev entry))))]
                (.send actor (if event? (:ev entry) #js {:type "hc.context" :patch (:patch entry)}))
                (assoc entry :snap (.getPersistedSnapshot actor) :hl (hl-of actor) :no-effect no-effect)))
            (append! [entry]
              ;; drops the entries after the cursor, then adds `entry` there
              (let [{:keys [log cursor]} @st]
                (swap! st assoc
                       :log (conj (vec (take (inc cursor) log)) (apply-entry! entry))
                       :cursor (inc cursor))))
            (send! [ev]
              (append! {:kind "event" :label (:type ev) :ev ev})
              (show! fig st (:type ev) send!))
            (edit! [k v]
              ;; a context-panel edit. Consecutive edits of one key make one entry: the
              ;; current entry, when it edits `k`, is redone from the state before it
              (let [{:keys [log cursor actor]} @st
                    current (when (>= cursor 0) (nth log cursor))
                    entry {:kind "edit" :label (str k " = " (js/JSON.stringify v)) :key k
                           :patch (doto #js {} (aset k v))}]
                (when-not (identical? v (aget (.-context (.getSnapshot actor)) k))
                  (if (and current (= "edit" (:kind current)) (= k (:key current)))
                    (do (restore! (dec cursor))
                        (swap! st assoc :log (conj (vec (take cursor log)) (apply-entry! entry))))
                    (append! entry))
                  (show! fig st nil send!))))
            (replay! []
              ;; the start changed (a prose scrub): every entry is applied again from it
              (start-over!)
              (swap! st assoc :log (mapv apply-entry! (:log @st)))
              (restore! (:cursor @st))
              (show! fig st nil send!))
            (go! [idx]
              (restore! idx)
              (swap! st assoc :cursor idx)
              (show! fig st nil send!))
            (restart! []
              (start-over!)
              (swap! st assoc :log [] :cursor -1)
              (show! fig st nil send!))]
      (swap! st assoc :go! go!)
      (when-let [fs (:context ui)]
        (context-inputs! fs (:context m) (some-> definition (aget "meta") (aget "enum")) edit!))
      (.addEventListener (:reset ui) "click" (fn [_] (restart!)))
      (.addEventListener js/document "hc-pause" (fn [e] (pause-timers! st (.. e -detail -paused) send!)))
      (.addEventListener (js/matchMedia "(prefers-color-scheme: dark)") "change" (fn [_] (draw! fig st)))
      ;; clicking a transition label presses its event's button, so guards apply as they do there;
      ;; clicking a state with code references shows its entry in the list
      (.addEventListener (:chart ui) "click"
                         (fn [e]
                           (if-let [label (.closest (.-target e) "[data-hc-event]")]
                             (when-let [b (.querySelector (:events ui) (str "button[data-event=\"" (js/CSS.escape (.getAttribute label "data-hc-event")) "\"]"))]
                               (when-not (.-disabled b) (.click b)))
                             (when-let [list (:code ui)]
                               (when-let [g (.closest (.-target e) "[data-uid]")]
                                 (let [uid (.getAttribute g "data-uid")]
                                   (when-let [li (.querySelector list (str "li[data-uid=\"" (js/CSS.escape uid) "\"]"))]
                                     (.scrollIntoView li #js {:block "nearest"})
                                     (.add (.-classList li) "hc-sc-flash")
                                     (js/setTimeout #(.remove (.-classList li) "hc-sc-flash") 1500))))))))
      (show! fig st nil send!)
      ;; prose controls: scrubs and replay links, including those that fired before the engine was ready
      (let [handle (fn [type detail]
                     (case type
                       "hc-param" (when (js/Object.hasOwn (:context m) (.-name detail))
                                    (let [patch (doto #js {} (aset (.-name detail) (.-value detail)))]
                                      (swap! st assoc :prose-context (js/Object.assign #js {} (:prose-context @st) patch))
                                      (replay!)))
                       "hc-state" (when (js/Array.isArray (.-events detail))
                                    (restart!)
                                    (doseq [ev (.-events detail)]
                                      (send! (if (string? ev) {:type ev} ev))))
                       nil))]
        (doseq [[type detail] @pending] (handle type detail))
        (reset! pending nil)
        (swap! st assoc :prose handle)
        (doseq [type ["hc-param" "hc-state"]]
          (.addEventListener fig type (fn [e] (handle type (.-detail e)))))))
    st))

(defn fail-chart!
  "The definition can't run: the figure shows the source and a notice."
  [fig message source-text]
  (.append fig
           (el! "div" {"class" "hc-viz-error"} message)
           (el! "pre" {"class" "hc-sc-source"} source-text)))

(defn start-one!
  "Pre: decide.core has started. Reads, checks and runs one figure."
  [report! fig i]
  (let [nm (chart-name fig i)
        say! (fn [rule msg] (report! kind (str "statechart \"" nm "\": " msg) rule))
        machine-id (.. fig -dataset -machine)
        script (if machine-id
                 (js/document.getElementById machine-id)
                 (.querySelector fig ":scope > script[type=\"application/json\"]"))
        ;; prose events that arrive before the engine has loaded wait here
        pending (atom [])]
    (doseq [type ["hc-param" "hc-state"]]
      (.addEventListener fig type (fn [e] (when-let [p @pending] (swap! pending conj [type (.-detail e)])))))
    (when-not (.hasAttribute fig "data-instrument")
      (say! "statechart-instrument" "<figure data-statechart> needs data-instrument, so the check's probe drives it"))
    (cond
      (and machine-id (not (and script (= "application/json" (.-type script)))))
      (say! "statechart-machine" (str "data-machine=\"" machine-id "\" names no <script type=\"application/json\" id=\"" machine-id "\">"))
      (nil? script)
      (do (say! "statechart-json" "no <script type=\"application/json\"> definition inside the figure")
          (fail-chart! fig "This statechart has no definition." ""))
      :else
      (let [text (.-textContent script)
            definition (try (js/JSON.parse text) (catch :default e (say! "statechart-json" (str "the definition isn't JSON: " (.-message e))) nil))
            collapse (vec (remove empty? (map #(.trim %) (.split (or (.. fig -dataset -collapse) "") ","))))
            m (when definition (sm/model definition collapse))]
        (when m
          (doseq [[rule msg] (:findings m)] (say! rule msg)))
        (if (or (nil? m) (:fatal m))
          (fail-chart! fig (if m "This statechart's definition has errors; see the reported problems." "This statechart's definition isn't valid JSON.") text)
          (let [renderer (renderer-of fig m)]
            (doseq [u (sm/unreachable m)]
              (say! "statechart-unreachable" (str "state \"" (state-label m u) "\" can't be reached from the start")))
            (when (and (= "mermaid" renderer) (sm/has-history? m))
              (say! "statechart-history" "data-renderer=\"mermaid\" draws history states only as a plain H state"))
            (-> (run! fig i m definition renderer pending)
                (.catch (fn [e]
                          (fail-chart! fig (str "The statechart couldn't start: " (or (.-message e) e)) text))))))))))

(defn start!
  "Starts every statechart on the page. `report!` is (fn [kind message source]).
  Runs before decide.explore starts, so scrub start values reach the charts."
  [report!]
  (doseq [[i fig] (map-indexed vector (js/Array.from (js/document.querySelectorAll "[data-statechart]")))]
    (start-one! report! fig i)))
