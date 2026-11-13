(ns decide.statechart-diagram
  "Diagram source for a statechart model (decide.statechart-machine): Mermaid
  stateDiagram-v2 text, or state-machine-cat text for charts with history states.
  Both are drawn from the same tree, with the active states marked. Pure: no DOM.

  Each state is drawn under its :diagram-id (\"s3\"), labeled with its key, so keys
  may hold any characters; the control maps drawn ids back to uids. A parallel state's
  regions aren't drawn as states of their own: in Mermaid they are the `--`
  segments, in state-machine-cat its child clusters. Neither is ever marked active.
  A transition is written inside the innermost block holding both its ends,
  because Mermaid can't link states of two different composite blocks from inside one.
  The root's own transitions apply from every state and aren't drawn."
  (:require [decide.statechart-machine :as sm]))

(def active-class "hc-sc-active")

(defn ancestors-of
  "uids from `uid`'s parent up to the root (\"\")."
  [idx uid]
  (loop [u (:parent (aget idx uid)) acc []]
    (if (nil? u) acc (recur (:parent (aget idx u)) (conj acc u)))))

(defn container
  "The block a transition between `a` and `b` belongs in: their nearest common ancestor."
  [idx a b]
  (let [as (into #{} (conj (ancestors-of idx a) a))]
    (or (some #(when (.has as %) %) (conj (ancestors-of idx b) b)) "")))

(defn block-of
  "The block a transition from `src` to `dst` is written in: a container that is
  drawn as a block, so never a state itself (a self-transition goes in its parent)."
  [idx src dst]
  (let [c (container idx src dst)]
    (if (or (= c src) (= c dst)) (or (:parent (aget idx c)) "") c)))

(defn label-of
  "A transition's label: event, guard, delay."
  [t]
  (let [g (when (:guard t) (str "[" (sm/guard-text (:guard t)) "]"))]
    (.join (vec (remove nil? [(case (:kind t) "always" (when-not g "always") (:event t)) g])) " ")))

(defn drawn-transitions
  "[{:src :dst :label :block}] for every transition drawn in the (collapsed) chart."
  [{:keys [idx]}]
  (vec (for [n (js/Object.values idx)
             :when (and (not= "" (:uid n)) (not (sm/under-collapsed idx (:uid n))))
             t (:transitions n)
             :when (not (:bad t))
             dst (if (seq (:targets t)) (distinct (map #(sm/drawn-target idx %) (:targets t))) [(:uid n)])]
         {:src (:uid n) :dst dst :label (label-of t) :block (block-of idx (:uid n) dst)})))

(defn drawn-children [n] (if (:collapsed n) [] (:children n)))

(defn did
  "The id state `uid` is drawn under."
  [idx uid]
  (:diagram-id (aget idx uid)))

;; ---- Mermaid ------------------------------------------------------------------

(def mermaid-entities {"#" "#35;" "<" "#lt;" ">" "#gt;" ";" "#59;" ":" "#58;" "\"" "#quot;"})

(defn mermaid-label
  "Label text Mermaid takes literally: # < > ; : \" as entity codes, in one pass."
  [s]
  (.replace s (js/RegExp. "[#<>;:\"]" "g") (fn [c] (get mermaid-entities c))))

(defn mermaid
  "stateDiagram-v2 text for model `m` with the uids in `active` marked.
  `theme` is a JSON-able Mermaid themeVariables map, `accent` a #rrggbb color."
  [{:keys [root idx] :as m} active theme accent]
  (let [out #js []
        ln (fn [depth s] (.push out (str (.repeat "  " depth) s)))
        by-block (group-by :block (drawn-transitions m))
        node-name (fn [n] (case (:kind n) "history" (if (= "deep" (:history n)) "H*" "H") (:key n)))
        emit-transitions (fn [depth block]
                           (doseq [{:keys [src dst label]} (get by-block block)]
                             (ln depth (str (did idx src) " --> " (did idx dst) (when (seq label) (str " : " (mermaid-label label)))))))
        emit-level (fn emit-level [depth parent]
                     (let [kids (drawn-children parent)]
                       (when (:initial parent) (ln depth (str "[*] --> " (did idx (:initial parent)))))
                       (doseq [c kids]
                         (ln depth (str "state \"" (mermaid-label (node-name c)) "\" as " (:diagram-id c)))
                         (case (if (seq (drawn-children c)) (:kind c) "atomic")
                           "compound" (do (ln depth (str "state " (:diagram-id c) " {"))
                                          (emit-level (inc depth) c)
                                          (ln depth "}"))
                           "parallel" (do (ln depth (str "state " (:diagram-id c) " {"))
                                          (doseq [[i region] (map-indexed vector (drawn-children c))]
                                            (when (pos? i) (ln (inc depth) "--"))
                                            (if (seq (drawn-children region))
                                              (emit-level (inc depth) region)
                                              (ln (inc depth) (str "state \"" (mermaid-label (node-name region)) "\" as " (:diagram-id region)))))
                                          (ln depth "}"))
                           nil)
                         (when (= "final" (:kind c)) (ln depth (str (:diagram-id c) " --> [*]"))))
                       (emit-transitions depth (:uid parent))))
        hl (vec (for [u (sm/highlighted m active) :when (not= "" u)] (did idx u)))]
    (.push out (str "%%{init: " (js/JSON.stringify {:theme "base" :themeVariables theme}) "}%%"))
    (.push out "stateDiagram-v2")
    (ln 1 (str "classDef active stroke:" accent ",stroke-width:4px"))
    (emit-level 1 root)
    (when (seq hl) (ln 1 (str "class " (.join hl ",") " active")))
    (.join out "\n")))

;; ---- state-machine-cat --------------------------------------------------------------

(defn smcat-string [s] (js/JSON.stringify s))

(defn initial-name
  "The name of the initial pseudo-state inside the state drawn as `diagram-id`."
  [diagram-id]
  (str "hc_initial_" diagram-id))

(defn smcat
  "state-machine-cat text for model `m` with the uids in `active` given class hc-sc-active.
  Parallel states are written [type=parallel], never with the .parallel name suffix."
  [{:keys [root idx] :as m} active]
  (let [hl (into #{} (sm/highlighted m active))
        by-block (group-by :block (drawn-transitions m))
        attrs (fn [n]
                (let [kind (if (seq (drawn-children n)) (:kind n) (if (or (= "compound" (:kind n)) (= "parallel" (:kind n))) "atomic" (:kind n)))
                      type (case kind
                             "parallel" "parallel"
                             "final" "final"
                             "history" (if (= "deep" (:history n)) "deephistory" "history")
                             nil)]
                  (str " ["
                       (.join (vec (remove nil?
                                           [(when type (str "type=" type))
                                            (when-not (= "history" kind) (str "label=" (smcat-string (:key n))))
                                            (when (.has hl (:uid n)) (str "class=" (smcat-string active-class)))]))
                              " ")
                       "]")))
        level (fn level [parent]
                (let [kids (drawn-children parent)
                      states (vec (concat
                                   (when (:initial parent) [(str (initial-name (:diagram-id parent)) " [type=initial]")])
                                   (for [c kids]
                                     (str (:diagram-id c) (attrs c)
                                          (when (seq (drawn-children c)) (str " {\n" (level c) "\n}"))))))
                      ts (vec (concat
                               (when (:initial parent) [(str (initial-name (:diagram-id parent)) " => " (did idx (:initial parent)) ";")])
                               (for [{:keys [src dst label]} (get by-block (:uid parent))]
                                 (str (did idx src) " => " (did idx dst) (when (seq label) (str ": " (.replaceAll label ";" ","))) ";"))))]
                  (str (.join states ",\n") ";"
                       (when (seq ts) (str "\n" (.join ts "\n"))))))]
    (level root)))
