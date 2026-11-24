(ns decide.moot
  "Links between questions, owned by the kit (CONVENTION.md, Moot).

    <script type=\"application/json\" id=\"hc-moot\">
      {\"evict\":      {\"when\": \"cache=inproc\", \"reason\": \"you picked In-process for the cache\"},
       \"size=large\": {\"when\": \"cache=inproc|memcached\", \"reason\": \"…\"}}
    </script>
    <section data-question=\"evict\">…the question…</section>

  A key names what goes moot: a question, whose data-question container folds to
  the child holding its title and greys, or one option of it (ID=VALUE), whose button or
  radio label greys and is struck through. `when` holds terms separated by spaces,
  moot when any one matches; a term is ID=V1|V2. ID= matches an unanswered question
  or an empty field, an answered checkbox reads true or false (one never answered
  matches ID= and what it shows), and a moot question reads as unanswered, so
  chains settle: question links are evaluated in dependency order.

  Links are evaluated after every recorded answer and every restore
  (decide.core/on-answers-shown!), and when controls or containers appear or go
  (page script, the kit's diffs). Each evaluation resolves where a link shows;
  a link that can't yet (an ID no control answers, no container, no such option)
  shows nothing and makes nothing moot. Answers are never written: on Done the map
  {key reason} of what is moot is recorded as the report-only decision `moot`.

  Problems are errors of kind \"moot\". The list's shape (JSON, entries, `when`
  syntax, cycles) is checked at start, and a link with a shape problem is dropped,
  except one missing its reason, which gets a written one. Display problems,
  links that still can't show or show only partly settle-ms after the load event,
  are reported then, once."
  (:require [decide.core :as core]))

;; {:questions [link] in dependency order, :options [link]}
;; link: {:key :id :value (options only) :terms [{:id :values}] :reason (or nil)}
;; A resolved link adds :container (questions) or :target (options), or :problem.
(def links (atom nil))

;; key -> the resolved link, for the links shown moot now
(def shown (js/Map.))

;; {key message} for the links with a display problem at the last evaluation:
;; one that can't show (:problem), or one that shows only partly (:warning).
;; Reported once the page settles.
(def display-problems (atom {}))

(def settle-ms
  "How long links get after the load event to resolve before the ones that still
  can't are reported: page script and the kit's diffs add controls after load."
  1500)

;; {key reason} of the links that held at the last evaluation
(def moot-now (atom {}))

;; ---- reading the list ------------------------------------------------------------

(defn split-at-eq
  "[before after] around the first = in `s`, or nil when it has none."
  [s]
  (let [i (.indexOf s "=")]
    (when (>= i 0) [(subs s 0 i) (subs s (inc i))])))

(defn controls [id] (js/Array.from (core/els-for id)))

(defn plain-object? [x] (= "[object Object]" (.call js/Object.prototype.toString x)))

(defn choice-control? [el]
  (or (= "BUTTON" (.-tagName el)) (= "radio" (.-type el))))

(defn control-kind [el]
  (cond
    (= "SELECT" (.-tagName el)) "select"
    (= "checkbox" (.-type el)) "checkbox"
    :else "text field"))

(defn parse-terms
  "{:terms [{:id :values}]} for `when`, or {:error message}. Syntax only: whether
  its IDs have controls is checked on every evaluation (resolve-place)."
  [w]
  (let [words (.filter (.split (.trim w) #"\s+") #(pos? (.-length %)))]
    (if (zero? (.-length words))
      {:error "its \"when\" is empty"}
      (loop [ws (seq words) terms []]
        (if-let [word (first ws)]
          (if-let [[id v] (split-at-eq word)]
            (let [values (vec (.split v "|"))]
              (if (and (> (count values) 1) (some #(= "" %) values))
                {:error (str "the term \"" word "\" in its \"when\" has an empty value; to match an unanswered question, write \""
                             id "=\" as a term of its own")}
                (recur (rest ws) (conj terms {:id id :values values}))))
            {:error (str "the term \"" word "\" in its \"when\" has no =")})
          {:terms terms})))))

(defn radio-label [input]
  (or (.closest input "label")
      (when (seq (.-id input))
        (js/document.querySelector (str "label[for=\"" (js/CSS.escape (.-id input)) "\"]")))
      input))

(defn option-target
  "{:target el} greyed for option `value` of question `id`: the button, or the
  radio's label. {:error message} when the question has no such option."
  [id value]
  (let [els (controls id)
        match (some (fn [el] (when (or (and (= "BUTTON" (.-tagName el)) (= value (.. el -dataset -choice)))
                                       (and (= "radio" (.-type el)) (= value (.-value el))))
                               el))
                    els)]
    (cond
      match {:target (if (= "radio" (.-type match)) (radio-label match) match)}
      (some choice-control? els) {:error (str "question \"" id "\" has no button or radio with the value \"" value "\"")}
      :else {:error (str "\"" id "\" is a " (control-kind (first els))
                         ": only button and radio options can be moot")})))

(defn link-name [key] (str "the link \"" key "\""))

(defn read-link
  "{:link link} for entry `entry` under `key`, with {:problem message} when its
  reason is missing; {:problem message} alone when its shape can't be used.
  Reads no page: where a link shows is resolved on every evaluation."
  [key entry]
  (let [where (link-name key)
        [id value] (or (split-at-eq key) [key nil])]
    (if (or (not (plain-object? entry)) (not (string? (.-when entry))))
      {:problem (str where " needs {\"when\": \"…\", \"reason\": \"…\"}")}
      (let [{:keys [terms error]} (parse-terms (.-when entry))
            reason (when (and (string? (.-reason entry)) (seq (.trim (.-reason entry)))) (.trim (.-reason entry)))]
        (if error
          {:problem (str where ": " error)}
          (cond-> {:link {:key key :id id :value value :terms terms :reason reason}}
            (nil? reason) (assoc :problem (str where " has no \"reason\", so the kit writes one"))))))))

(defn control? [el]
  (or (.hasAttribute el "data-decision") (.matches el "input, select, textarea, button, option")))

(defn resolve-place
  "Where link `l` shows now: {:container el} for a question, {:target el} for an
  option, with a :warning when it shows only partly: a radio without a label
  greys alone, and of several containers only the first folds. {:problem message}
  while it can't show: an ID no control answers, no container, a container that
  is a control or holds the controls its `when` reads (folding would hide them),
  or no such option."
  [{:keys [key id value terms]}]
  (let [where (link-name key)
        missing (some #(when-not (seq (controls %)) %) (cons id (map :id terms)))]
    (cond
      (= missing id) {:problem (str where " names \"" id "\", which no control on the page answers")}
      (some? missing) {:problem (str where ": its \"when\" reads \"" missing "\", which no control on the page answers")}
      (some? value)
      (let [{:keys [target error]} (option-target id value)]
        (cond
          error {:problem (str where ": " error)}
          (= "INPUT" (.-tagName target)) {:target target
                                          :warning (str where ": the radio \"" value "\" has no <label>, so only the radio greys; wrap it and its text in a <label>")}
          :else {:target target}))
      :else
      (let [cs (js/Array.from (js/document.querySelectorAll (str "[data-question=\"" (js/CSS.escape id) "\"]")))
            c (first cs)
            inside (when c (some (fn [t] (when (some #(.contains c %) (controls (:id t))) (:id t))) terms))]
        (cond
          (nil? c) {:problem (str where ": no element has data-question=\"" id "\" to fold")}
          (control? c) {:problem (str where ": data-question=\"" id "\" is on a control; put it on an element around the question")}
          (some? inside) {:problem (str where ": its data-question container holds the controls of \"" inside
                                        "\", which its \"when\" reads, so folding would hide them; move them out")}
          (> (count cs) 1) {:container c
                            :warning (str where ": " (count cs) " elements have data-question=\"" id "\"; only the first folds")}
          :else {:container c})))))

(defn dependency-order
  "[ordered stuck]: question links ordered so each comes after the question links
  its terms read, and the ones left over because they are on or depend on a cycle."
  [questions]
  (let [linked (js/Set. (map :id questions))]
    (loop [done (js/Set.) ordered [] left questions]
      (let [ready (vec (filter (fn [l] (every? (fn [t] (or (not (.has linked (:id t))) (.has done (:id t))))
                                               (:terms l)))
                               left))]
        (if (empty? ready)
          [ordered left]
          (do (doseq [l ready] (.add done (:id l)))
              (recur done (vec (concat ordered ready)) (vec (remove (fn [l] (.has done (:id l))) left)))))))))

(defn cycle-members
  "The links among `stuck` that are on a cycle: each reaches its own question by
  following the questions its terms read. The rest of `stuck` only depend on one."
  [stuck]
  (let [by-id (js/Map. (map (fn [l] [(:id l) l]) stuck))
        reads (fn [l] (keep #(.get by-id (:id %)) (:terms l)))
        reaches-itself? (fn [start]
                          (loop [todo (vec (reads start)) seen (js/Set.)]
                            (if-let [l (first todo)]
                              (cond
                                (identical? l start) true
                                (.has seen (:id l)) (recur (vec (rest todo)) seen)
                                :else (do (.add seen (:id l))
                                          (recur (vec (concat (rest todo) (reads l))) seen)))
                              false)))]
    (filterv reaches-itself? stuck)))

(defn cycle-problem [members]
  (if (= 1 (count members))
    (str (link-name (:key (first members))) " forms a cycle, so it's dropped")
    (str "the links " (.join (into-array (map #(str "\"" (:key %) "\"") members)) ", ")
         " form a cycle, so they're dropped")))

(defn read-links
  "{:links {:questions :options} :problems [message]} for the list's text, or
  {:problems [message]} alone when it isn't a JSON object."
  [text]
  (let [[parsed parse-error] (try [(js/JSON.parse text) nil] (catch :default e [nil (.-message e)]))]
    (if (not (plain-object? parsed))
      {:problems [(str "the link list isn't a JSON object" (when parse-error (str ": " parse-error)))]}
      (let [read (map (fn [[k v]] (read-link k v)) (js/Object.entries parsed))
            usable (vec (keep :link read))
            questions (vec (filter #(nil? (:value %)) usable))
            [_ stuck] (dependency-order questions)
            members (cycle-members stuck)
            dropped (js/Set. (map :key members))
            ;; without the cycle's own links, their questions are never moot, and
            ;; the links that read them order like any other
            [ordered _] (dependency-order (vec (remove #(.has dropped (:key %)) questions)))]
        {:links {:questions ordered :options (vec (filter #(some? (:value %)) usable))}
         :problems (cond-> (vec (keep :problem read))
                     (seq members) (conj (cycle-problem members)))}))))

;; ---- evaluating -------------------------------------------------------------------

(defn answer-of
  "The answer `id`'s controls show now, as a string: a chosen button's choice, a
  checked radio's value, a select's or a field's text, \"true\" or \"false\" for an
  answered checkbox; \"\" when unanswered, a checkbox with no answer included."
  [id]
  (or (some (fn [el]
              (cond
                (= "BUTTON" (.-tagName el)) (when (.contains (.-classList el) "chosen") (.. el -dataset -choice))
                (= "radio" (.-type el)) (when (.-checked el) (.-value el))
                (= "checkbox" (.-type el)) (if (some? (:value (get @core/answers id))) (str (.-checked el)) "")
                :else (when (pos? (.-length (.-value el))) (.-value el))))
            (controls id))
      ""))

(defn checkbox-shows
  "\"true\" or \"false\" for how question `id`'s checkbox shows now; nil when its
  controls hold no checkbox."
  [id]
  (some #(when (= "checkbox" (.-type %)) (str (.-checked %))) (controls id)))

(defn term-matches?
  "Term `t` holds for answer `v`. A checkbox with no answer reads \"\" and also
  matches what it shows: false when unticked, true when ticked in the markup."
  [t v]
  (or (.includes (:values t) v)
      (and (= "" v)
           (when-let [shown (checkbox-shows (:id t))] (.includes (:values t) shown)))))

(defn details? [el] (= "DETAILS" (.-tagName el)))

(defn first-heading
  "The container's title: a <details> container's <summary>, else its first
  heading at any depth; nil when it has neither."
  [container]
  (or (when (details? container) (.querySelector container ":scope > summary"))
      (.querySelector container "h1, h2, h3, h4, h5, h6")))

(defn head-child
  "The container's direct child that holds its title (first-heading), kept
  visible when it folds; nil when it has no title."
  [container]
  (when-let [h (first-heading container)]
    (loop [el h]
      (if (identical? (.-parentElement el) container) el (recur (.-parentElement el))))))

(defn text-of [el] (.trim (.replace (.-textContent el) #"\s+" " ")))

(defn answer-label
  "How the reader sees answer `v` of question `id`: its button text, radio label or
  select option text, else `v`."
  [id v]
  (or (some (fn [el]
              (cond
                (and (= "BUTTON" (.-tagName el)) (= v (.. el -dataset -choice))) (text-of el)
                (and (= "radio" (.-type el)) (= v (.-value el))) (let [l (radio-label el)] (when (not= l el) (text-of l)))
                (= "SELECT" (.-tagName el)) (some #(when (= v (.-value %)) (text-of %)) (js/Array.from (.-options el)))))
            (controls id))
      v))

(defn written-reason
  "“Heading” is “Label” for the term `id` = `v` that made a link hold."
  [id v]
  (let [c (js/document.querySelector (str "[data-question=\"" (js/CSS.escape id) "\"]"))
        heading-text (some-> c first-heading text-of)
        h (if (seq heading-text) heading-text id)]
    (if (= "" v)
      (str "“" h "” is unanswered")
      (str "“" h "” is “" (answer-label id v) "”"))))

(defn evaluate
  "{key reason} of the links that hold now. A question made moot reads as
  unanswered to the links after it. Pre: `questions` is in dependency order."
  [{:keys [questions options]}]
  (let [moot-ids (js/Set.)
        value-of (fn [id] (if (.has moot-ids id) "" (answer-of id)))
        holding-term (fn [link] (some (fn [t] (let [v (value-of (:id t))]
                                                (when (term-matches? t v) [(:id t) v])))
                                      (:terms link)))]
    (reduce (fn [acc link]
              (if-let [[id v] (holding-term link)]
                (do (when (nil? (:value link)) (.add moot-ids (:id link)))
                    (assoc acc (:key link) (or (:reason link) (written-reason id v))))
                acc))
            {} (concat questions options))))

;; ---- showing ------------------------------------------------------------------------

(defn toggle!
  "Opens or folds a moot container. Opening a <details> container also opens the
  details, since its content shows only then."
  [container button]
  (let [open? (.toggle (.-classList container) "hc-moot-open")]
    (when (and open? (details? container)) (set! (.-open container) true))
    (.setAttribute button "aria-expanded" (str open?))
    (set! (.-textContent button) (if open? "Hide" "Show"))))

(defn bar-of [container]
  (.querySelector container ":scope > .hc-moot-bar, :scope > summary > .hc-moot-bar"))

(defn wrap-loose-text!
  "Wraps each non-blank text node directly inside `container` in a <span>: the
  fold hides element children (decide.css), and a bare text node isn't one."
  [container]
  (doseq [n (filter #(and (= 3 (.-nodeType %)) (seq (.trim (.-textContent %))))
                    (js/Array.from (.-childNodes container)))]
    (let [span (js/document.createElement "span")]
      (.replaceWith n span)
      (.append span n))))

(defn show-question!
  "Folds and greys the link's container, with `reason` in view. A container that is
  already moot keeps whether the reader opened it."
  [{:keys [container]} reason]
  (let [bar (or (bar-of container)
                (let [reason-el (js/document.createElement "span")
                      button (js/document.createElement "button")
                      bar (js/document.createElement "div")
                      head (head-child container)]
                  (wrap-loose-text! container)
                  (set! (.-className reason-el) "hc-moot-reason")
                  (set! (.-type button) "button")
                  (set! (.-className button) "hc-moot-toggle")
                  (.setAttribute button "aria-expanded" "false")
                  (set! (.-textContent button) "Show")
                  ;; preventDefault: inside a <summary>, a click would also open or close the details
                  (.addEventListener button "click" (fn [e] (.preventDefault e) (toggle! container button)))
                  (set! (.-className bar) "hc-moot-bar")
                  (.append bar reason-el button)
                  (when head (.add (.-classList head) "hc-moot-head"))
                  ;; a closed <details> shows only its <summary>, so its bar goes inside it
                  (cond
                    (and head (details? container) (= "SUMMARY" (.-tagName head))) (.append head bar)
                    head (.after head bar)
                    :else (.prepend container bar))
                  (.add (.-classList container) "hc-moot")
                  bar))]
    (set! (.-textContent (.querySelector bar ".hc-moot-reason")) (str "Moot: " reason))))

(defn unshow-question! [{:keys [container]}]
  (when-let [bar (bar-of container)]
    (.remove bar)
    (.remove (.-classList container) "hc-moot" "hc-moot-open")
    (some-> (head-child container) .-classList (.remove "hc-moot-head"))))

(defn option-reason-of [target]
  (let [n (.-nextElementSibling target)]
    (when (and n (.contains (.-classList n) "hc-moot-option-reason")) n)))

(defn show-option! [{:keys [target]} reason]
  (let [r (or (option-reason-of target)
              (let [r (js/document.createElement "span")]
                (set! (.-className r) "hc-moot-option-reason")
                (.add (.-classList target) "hc-moot-option")
                (.after target r)
                r))]
    (set! (.-textContent r) (str "Moot: " reason))))

(defn unshow-option! [{:keys [target]}]
  (when-let [r (option-reason-of target)]
    (.remove r)
    (.remove (.-classList target) "hc-moot-option")))

(defn show! [l reason] (if (:container l) (show-question! l reason) (show-option! l reason)))

(defn unshow! [l] (if (:container l) (unshow-question! l) (unshow-option! l)))

(defn same-place? [a b]
  (and (identical? (:container a) (:container b)) (identical? (:target a) (:target b))))

(defn update!
  "Resolves where each link shows, evaluates the ones that resolved, and shows
  the result. A link that can't resolve shows nothing and makes nothing moot."
  []
  (let [{:keys [questions options]} @links
        resolved (mapv #(merge % (resolve-place %)) (concat questions options))
        placed (filterv #(nil? (:problem %)) resolved)
        now (evaluate {:questions (filterv #(nil? (:value %)) placed)
                       :options (filterv #(some? (:value %)) placed)})]
    (reset! display-problems (reduce (fn [acc l]
                                       (if-let [m (or (:problem l) (:warning l))] (assoc acc (:key l) m) acc))
                                     {} resolved))
    (doseq [l resolved]
      (let [reason (when (nil? (:problem l)) (aget now (:key l)))
            before (.get shown (:key l))]
        (when (and before (or (nil? reason) (not (same-place? before l))))
          (unshow! before)
          (.delete shown (:key l)))
        (when reason
          (show! l reason)
          (.set shown (:key l) l))))
    (reset! moot-now now)))

(def answer-markup "[data-decision], [data-question]")

(defn answer-markup-in? [nodes]
  (some (fn [n] (and (= 1 (.-nodeType n))
                     (or (.matches n answer-markup) (some? (.querySelector n answer-markup)))))
        (js/Array.from nodes)))

(defn on-mutations
  "Re-evaluates when controls or containers were added or removed. The kit's own
  bars, reasons and text wrappers carry neither, so they don't trigger it."
  [records]
  (when (some #(or (answer-markup-in? (.-addedNodes %)) (answer-markup-in? (.-removedNodes %))) records)
    (update!)))

(defn report-display-problems! [report!]
  (doseq [[_ message] (js/Object.entries @display-problems)]
    (report! "moot" message "hc-moot")))

(defn moot-report
  "The links that hold now, reported on Done and in Download.
  Pre: the page has a link list that parses (start! registers this only then)."
  []
  @moot-now)

(defn start!
  "Reads the page's link list, reports its shape problems, shows what is moot now,
  keeps it current, and reports the links that still can't show once the page
  has settled. `report!` is (fn [kind message source]).
  Pre: decide.core has started, so its listeners and htmlChannel.record work."
  [report!]
  (let [lists (js/document.querySelectorAll "script#hc-moot")
        n (.-length lists)]
    (when (pos? n)
      (when (> n 1)
        (report! "moot" (str "a page has one link list, <script id=\"hc-moot\">; found " n ", so only the first is read")
                 "hc-moot"))
      (let [{ls :links problems :problems} (read-links (.-textContent (aget lists 0)))]
        (doseq [p problems] (report! "moot" p "hc-moot"))
        (when ls
          (reset! links ls)
          (core/report-on-done! "moot" moot-report)
          (core/on-answers-shown! update!)
          (.observe (js/MutationObserver. on-mutations) js/document.body {:childList true :subtree true})
          (update!)
          (let [report-later #(js/setTimeout (fn [] (report-display-problems! report!)) settle-ms)]
            (if (= "complete" js/document.readyState)
              (report-later)
              (.addEventListener js/window "load" report-later {:once true}))))))))
