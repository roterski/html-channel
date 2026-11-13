(ns decide.statechart-machine
  "A statechart definition (a subset of the XState v5 config, see STATECHART.md)
  turned into what the control needs: a tree of states with resolved targets,
  the lint findings, the XState config that runs it, and the active states of
  a snapshot. Pure: no DOM, no library loading.

  A state in the tree:
    {:uid      unique id, the key path joined with \"__\" (\"ready__view\")
     :key      its key in the definition, shown as its label
     :diagram-id  the id it is drawn under, \"s<n>\" (keys may hold - . or spaces)
     :kind     \"atomic\" \"compound\" \"parallel\" \"final\" \"history\"
     :history  \"shallow\" or \"deep\", for a history state
     :region?  true for a child of a parallel state
     :parent   the parent's uid, nil for the root
     :initial  uid of the initial child of a compound state
     :children [states], in definition order
     :transitions [{:kind \"on\"|\"always\"|\"after\" :event :delay :targets [uids]
                    :guard :assign :code [refs]}]
     :code     [code references]}
  The root has uid \"\" and is the machine itself; it isn't drawn.

  Delayed transitions (`after`) become events named \"after <ms>ms\", which
  the control sends on its own pausable clock.")

(def sep "__")

(def compare-ops {"eq" "==" "neq" "!=" "lt" "<" "lte" "<=" "gt" ">" "gte" ">="})

(defn after-event [ms] (str "after " ms "ms"))

(defn internal-event?
  "Events the control sends itself, never shown as buttons."
  [e]
  (or (.startsWith e "after ") (.startsWith e "hc.")))

;; ---- reading the definition -----------------------------------------------------

(defn as-vec
  "`x` as a vector: nil → [], a vector as is, anything else wrapped."
  [x]
  (cond (nil? x) [] (js/Array.isArray x) x :else [x]))

(defn obj? [x] (and (some? x) (= "object" (js/typeof x)) (not (js/Array.isArray x))))

(defn entries [o] (if (obj? o) (js/Object.entries o) []))

(defn transition-specs
  "The raw transition objects of one entry: a string target, an object, or a list of either."
  [raw]
  (for [t (as-vec raw)]
    (if (string? t) {:target t} t)))

(defn assign-of
  "The declarative assign of a transition's actions, or nil."
  [actions]
  (some (fn [a] (when (obj? a) (get a "assign"))) (as-vec actions)))

(defn code-refs [m] (vec (as-vec (when (obj? m) (get m "code")))))

(defn read-transitions
  "Raw transitions of state definition `d`, unresolved: {:kind :event :delay :target-strs :guard :assign :code}."
  [d]
  (let [mk (fn [kind event delay spec]
             {:kind kind :event event :delay delay
              :target-strs (vec (as-vec (get spec "target")))
              :guard (get spec "guard") :assign (assign-of (get spec "actions"))
              :code (code-refs (get spec "meta"))})]
    (vec (concat
          (for [[ev raw] (entries (get d "on"))
                spec (transition-specs raw)]
            (mk "on" ev nil spec))
          (for [spec (transition-specs (get d "always"))]
            (mk "always" nil nil spec))
          (for [[ms raw] (entries (get d "after"))
                spec (transition-specs raw)]
            (mk "after" (after-event ms) (js/Number ms) spec))))))

(defn kind-of [d]
  (let [t (get d "type")
        kids (seq (entries (get d "states")))]
    (cond (= t "parallel") "parallel"
          (= t "final") "final"
          (= t "history") "history"
          kids "compound"
          :else "atomic")))

(defn build
  "The tree under definition `d`, keyed `k`, at key path `path`, with parent uid `parent`."
  [d k path parent region?]
  (let [uid (.join path sep)
        kind (kind-of d)
        children (vec (for [[ck cd] (entries (get d "states"))]
                        (build cd ck (conj path ck) uid (= kind "parallel"))))]
    {:uid uid :key k :kind kind :region? region? :parent parent
     :history (when (= kind "history") (or (get d "history") "shallow"))
     :id (get d "id")
     :initial-key (get d "initial")
     :children children
     :raw-transitions (read-transitions d)
     :code (code-refs (get d "meta"))}))

(defn index
  "{uid node} over the tree. Also gives each node its :diagram-id, \"s0\", \"s1\", …
  in definition order: an id both renderers parse, whatever characters the key has."
  [root]
  (let [acc #js {}
        n-seen (atom 0)]
    ((fn walk [n]
       (aset n "diagram-id" (str "s" @n-seen))
       (swap! n-seen inc)
       (aset acc (:uid n) n)
       (doseq [c (:children n)] (walk c)))
     root)
    acc))

(defn uid-of-diagram-id
  "The uid of the state drawn as `diagram-id`, or nil."
  [{:keys [idx]} diagram-id]
  (some #(when (= diagram-id (:diagram-id %)) (:uid %)) (js/Object.values idx)))

;; ---- target resolution ------------------------------------------------------------

(defn path-of [uid] (if (= uid "") [] (.split uid sep)))

(defn resolve-target
  "The uid a target string names, from the state `source`, or nil.
  XState v5: \"#id\" is a state's id (or the machine id followed by a dotted path),
  \".a.b\" is a descendant of the source, \"a.b\" starts at a sibling (at a child, from the root)."
  [idx ids machine-id source t]
  (let [found (fn [path] (let [uid (.join path sep)] (when (aget idx uid) uid)))
        base (fn [n] (if (= (:uid n) "") [] (path-of (:parent n))))]
    (cond
      (.startsWith t "#")
      (let [r (.slice t 1)]
        (or (aget ids r)
            (when (= r machine-id) "")
            (when (.startsWith r (str machine-id ".")) (found (.split (.slice r (inc (count machine-id))) ".")))))
      (.startsWith t ".") (found (vec (concat (path-of (:uid source)) (.split (.slice t 1) "."))))
      :else (found (vec (concat (base source) (.split t ".")))))))

;; ---- guards and assigns --------------------------------------------------------------

(defn guard-problem
  "Why guard `g` is malformed or names an undefined context key, else nil."
  [g context]
  (cond
    (not (obj? g)) (str "a guard must be an object like {\"lt\": [\"retries\", 3]}, got " (js/JSON.stringify g))
    (not= 1 (count (js/Object.keys g))) (str "a guard has exactly one operator, got " (js/JSON.stringify g))
    :else
    (let [[op arg] (first (js/Object.entries g))]
      (cond
        (get compare-ops op)
        (cond (not (and (js/Array.isArray arg) (= 2 (count arg)) (string? (first arg))))
              (str "\"" op "\" takes [contextKey, value], got " (js/JSON.stringify arg))
              (not (js/Object.hasOwn context (first arg)))
              (str "guard names context key \"" (first arg) "\", which the definition's context doesn't have"))
        (or (= op "and") (= op "or"))
        (if (js/Array.isArray arg)
          (some #(guard-problem % context) arg)
          (str "\"" op "\" takes a list of guards"))
        (= op "not") (guard-problem arg context)
        :else (str "unknown guard operator \"" op "\"")))))

(defn guard-fn
  "(fn [context] boolean) for a well-formed guard."
  [g]
  (let [[op arg] (first (js/Object.entries g))]
    (case op
      "and" (let [fs (mapv guard-fn arg)] (fn [c] (every? #(% c) fs)))
      "or" (let [fs (mapv guard-fn arg)] (fn [c] (boolean (some #(% c) fs))))
      "not" (let [f (guard-fn arg)] (fn [c] (not (f c))))
      (let [[k v] arg]
        (case op
          "eq" (fn [c] (= (aget c k) v))
          "neq" (fn [c] (not= (aget c k) v))
          "lt" (fn [c] (< (aget c k) v))
          "lte" (fn [c] (<= (aget c k) v))
          "gt" (fn [c] (> (aget c k) v))
          "gte" (fn [c] (>= (aget c k) v)))))))

(defn guard-text
  "A well-formed guard as a short expression: retries < 3, a && !b."
  [g]
  (let [[op arg] (first (js/Object.entries g))]
    (case op
      "and" (.join (mapv guard-text arg) " && ")
      "or" (str "(" (.join (mapv guard-text arg) " || ") ")")
      "not" (str "!(" (guard-text arg) ")")
      (str (first arg) " " (get compare-ops op) " " (js/JSON.stringify (second arg))))))

(defn assign-problem
  "Why assign `a` is malformed or names an undefined context key, else nil."
  [a context]
  (if-not (obj? a)
    (str "assign must be an object like {\"retries\": {\"add\": 1}}, got " (js/JSON.stringify a))
    (some (fn [[k op]]
            (cond (not (js/Object.hasOwn context k))
                  (str "assign names context key \"" k "\", which the definition's context doesn't have")
                  (not (and (obj? op) (= 1 (count (js/Object.keys op)))
                            (or (js/Object.hasOwn op "set") (js/Object.hasOwn op "add"))))
                  (str "assign of \"" k "\" takes {\"set\": value} or {\"add\": number}, got " (js/JSON.stringify op))))
          (js/Object.entries a))))

(defn assign-patch
  "The context changes a well-formed assign makes, from `context`."
  [a context]
  (let [patch #js {}]
    (doseq [[k op] (js/Object.entries a)]
      (aset patch k (if (js/Object.hasOwn op "set") (get op "set") (+ (aget context k) (get op "add")))))
    patch))

;; ---- the model --------------------------------------------------------------------

(defn collapse!
  "Turns each composite named in `names` (a key, if unique, or a uid) into an atomic
  state, in place. Returns findings for names that aren't a composite state."
  [root idx names]
  (let [by-key (reduce (fn [m n] (update m (:key n) (fnil conj []) n)) {} (js/Object.values idx))
        findings #js []]
    (doseq [nm names]
      (let [n (or (aget idx (.replaceAll nm "." sep))
                  (let [ns (get by-key nm)] (when (= 1 (count ns)) (first ns))))]
        (cond (nil? n) (.push findings ["statechart-machine" (str "data-collapse names \"" nm "\", which isn't a state")])
              (empty? (:children n)) (.push findings ["statechart-machine" (str "data-collapse names \"" nm "\", which has no inner states")])
              :else (aset n "collapsed" true))))
    findings))

(defn under-collapsed
  "The collapsed state `uid` lies in or under, or nil."
  [idx uid]
  (loop [u uid found nil]
    (if (nil? u)
      found
      (let [n (aget idx u)]
        (recur (:parent n) (if (and n (:collapsed n) (not= u uid)) u found))))))

(defn model
  "Reads definition `d` (parsed JSON). `collapse` lists composites to draw as single states.
  Returns {:root :idx :context :machine-id :findings [[rule message]] :fatal}.
  :fatal means the definition can't run or be drawn; then :root may be missing."
  [d collapse]
  (if-not (and (obj? d) (obj? (get d "states")) (string? (get d "initial")))
    {:findings [["statechart-json" "the definition needs \"initial\" (a string) and \"states\" (an object)"]] :fatal true}
    (let [context (if (obj? (get d "context")) (get d "context") #js {})
          machine-id (or (get d "id") "machine")
          root (build d machine-id [] nil false)
          root (assoc root :uid "")
          idx (index root)
          ids (reduce (fn [m n] (if-let [i (:id n)] (assoc m i (:uid n)) m)) {} (js/Object.values idx))
          findings #js []
          report! (fn [rule msg] (.push findings [rule msg]))
          label (fn [n] (if (= "" (:uid n)) "the machine" (str "state \"" (.join (path-of (:uid n)) ".") "\"")))]
      ;; initial children
      (doseq [n (js/Object.values idx)]
        (when (= "compound" (:kind n))
          (let [ik (:initial-key n)
                child (some #(when (= ik (:key %)) %) (:children n))]
            (cond (nil? ik) (report! "statechart-json" (str (label n) " has inner states but no \"initial\""))
                  (nil? child) (report! "statechart-target" (str (label n) " has initial \"" ik "\", which isn't one of its states"))
                  :else (aset n "initial" (:uid child))))))
      ;; transitions: resolve targets, check guards and assigns
      (doseq [n (js/Object.values idx)]
        (aset n "transitions"
              (vec (for [t (:raw-transitions n)]
                     (let [targets (vec (for [s (:target-strs t)
                                              :let [u (resolve-target idx ids machine-id n s)]]
                                          (do (when (nil? u)
                                                (report! "statechart-target"
                                                         (str (label n) ", " (or (:event t) "always") ": target \"" s "\" isn't a state")))
                                              u)))
                           gp (when (some? (:guard t)) (guard-problem (:guard t) context))
                           ap (when (some? (:assign t)) (assign-problem (:assign t) context))
                           dp (when (and (= "after" (:kind t)) (not (and (js/Number.isFinite (:delay t)) (>= (:delay t) 0))))
                                (str "after: delay \"" (.replace (:event t) #"^after (.*)ms$" "$1") "\" isn't a number of milliseconds"))]
                       (when dp (report! "statechart-json" (str (label n) ", " dp)))
                       (when gp (report! "statechart-guard" (str (label n) ", " (or (:event t) "always") ": " gp)))
                       (when ap (report! "statechart-guard" (str (label n) ", " (or (:event t) "always") ": " ap)))
                       (assoc t :targets (vec (remove nil? targets)) :bad (boolean (or gp ap dp (some nil? targets)))))))))
      (let [cf (collapse! root idx (or collapse []))]
        (doseq [f cf] (.push findings f)))
      ;; a compound state without a resolved initial child can't run or be drawn
      {:root root :idx idx :context context :machine-id machine-id :findings (vec findings)
       :fatal (boolean (some #(and (= "compound" (:kind %)) (nil? (:initial %))) (js/Object.values idx)))})))

;; ---- reachability -----------------------------------------------------------------

(defn entered
  "The uids entered when `uid` is entered: its ancestors, itself, and its initial
  descendants (all regions of a parallel state; for a history state, its parent's children)."
  [idx uid]
  (let [acc #js []
        down (fn down [u]
               (let [n (aget idx u)]
                 (.push acc u)
                 (case (:kind n)
                   "compound" (when (:initial n) (down (:initial n)))
                   "parallel" (doseq [c (:children n)] (down (:uid c)))
                   "history" (doseq [c (:children (aget idx (:parent n)))] (when (not= (:uid c) u) (down (:uid c))))
                   nil)))]
    (loop [p (:parent (aget idx uid))]
      (when (and p (not= p "")) (.push acc p) (recur (:parent (aget idx p)))))
    (down uid)
    acc))

(defn unreachable
  "States that no path from the start reaches, ignoring guards. In definition order."
  [{:keys [root idx]}]
  (let [seen (js/Set.)
        queue #js []]
    (.add seen "")
    (doseq [u (entered idx (:initial root))] (.push queue u))
    (loop []
      (when-let [u (.shift queue)]
        (when-not (.has seen u)
          (.add seen u)
          (doseq [t (:transitions (aget idx u))
                  target (:targets t)
                  e (entered idx target)]
            (.push queue e)))
        (recur)))
    ;; the root's own transitions apply from every state
    (doseq [t (:transitions root) target (:targets t) e (entered idx target)]
      (when-not (.has seen e) (.add seen e)))
    (vec (for [n (js/Object.values idx)
               :when (and (not= "" (:uid n)) (not (.has seen (:uid n))) (not (under-collapsed idx (:uid n))))]
           (:uid n)))))

;; ---- running it ------------------------------------------------------------------------

(defn drawn-target
  "Where a transition to `uid` lands in the drawn (collapsed) chart."
  [idx uid]
  (or (under-collapsed idx uid) uid))

(defn engine-config
  "The XState v5 machine config for model `m`. `assign` is XState's assign.
  Transitions with a problem are left out. Collapsed states run as atomic states."
  [{:keys [root idx context]} assign]
  (let [transition (fn [t]
                     (cond-> {}
                       (seq (:targets t)) (assoc :target (vec (distinct (for [u (:targets t)] (str "#" (drawn-target idx u))))))
                       (:guard t) (assoc :guard (let [f (guard-fn (:guard t))] (fn [args] (f (.-context args)))))
                       (:assign t) (assoc :actions [(assign (let [a (:assign t)] (fn [args] (assign-patch a (.-context args)))))])))
        node (fn node [n]
               (let [ts (remove :bad (:transitions n))
                     on (reduce (fn [m t]
                                  (if (= "always" (:kind t))
                                    m
                                    (update m (:event t) (fnil conj []) (transition t))))
                                {} ts)
                     always (vec (for [t ts :when (= "always" (:kind t))] (transition t)))
                     kids (if (:collapsed n) [] (:children n))]
                 (cond-> {:id (if (= "" (:uid n)) "hc-root" (:uid n))}
                   (seq (js/Object.keys on)) (assoc :on on)
                   (seq always) (assoc :always always)
                   (and (= "compound" (:kind n)) (not (:collapsed n))) (assoc :initial (:key (aget idx (:initial n))))
                   (and (= "parallel" (:kind n)) (not (:collapsed n))) (assoc :type "parallel")
                   (= "final" (:kind n)) (assoc :type "final")
                   (= "history" (:kind n)) (assoc :type "history" :history (:history n))
                   (seq kids) (assoc :states (reduce (fn [m c] (assoc m (:key c) (node c))) {} kids)))))
        cfg (node root)]
    (assoc cfg
           :context context
           :on (assoc (or (:on cfg) {}) "hc.context" {:actions [(assign (fn [args] (.. args -event -patch)))]}))))

(defn active-uids
  "The uids active in snapshot value `v` (a state value), regions included."
  [{:keys [root idx]} v]
  (let [acc #js []
        walk (fn walk [parent-uid v]
               (let [child (fn [k] (if (= "" parent-uid) k (str parent-uid sep k)))]
                 (if (string? v)
                   (when (aget idx (child v)) (.push acc (child v)))
                   (doseq [[k sub] (js/Object.entries v)]
                     (.push acc (child k))
                     (walk (child k) sub)))))]
    (walk "" v)
    acc))

(defn highlighted
  "The active states to draw highlighted: every active state except parallel regions."
  [{:keys [idx]} active]
  (vec (remove #(:region? (aget idx %)) active)))

(defn events-of
  "The events defined on the active states (and the root), in order, without the control's own."
  [{:keys [root idx]} active]
  (let [seen (js/Set.)
        out #js []]
    (doseq [n (concat [root] (map #(aget idx %) active))
            t (:transitions n)
            :let [e (:event t)]
            :when (and e (= "on" (:kind t)) (not (:bad t)) (not (.has seen e)) (not (internal-event? e)))]
      (.add seen e)
      (.push out e))
    out))

(defn delays-of
  "The delayed transitions of the active states: [{:event :delay}]."
  [{:keys [idx]} active]
  (vec (for [u active
             t (:transitions (aget idx u))
             :when (and (= "after" (:kind t)) (not (:bad t)))]
         {:event (:event t) :delay (:delay t)})))

(defn has-history? [{:keys [idx]}]
  (boolean (some #(and (= "history" (:kind %)) (not (under-collapsed idx (:uid %)))) (js/Object.values idx))))
