(ns decide.moot
  "Links between questions, owned by the kit (CONVENTION.md, Moot).

    <script type=\"application/json\" id=\"hc-moot\">
      {\"evict\":      {\"when\": \"cache=inproc\", \"reason\": \"you picked In-process for the cache\"},
       \"size=large\": {\"when\": \"cache=inproc|memcached\", \"reason\": \"…\"}}
    </script>
    <section data-question=\"evict\">…the question…</section>

  A key names what goes moot: a question, whose data-question container folds to
  its first heading and greys, or one option of it (ID=VALUE), whose button or
  radio label greys and is struck through. `when` holds terms separated by spaces,
  moot when any one matches; a term is ID=V1|V2. ID= matches an unanswered question
  or an empty field, a checkbox reads true or false, and a moot question reads as
  unanswered, so chains settle: question links are evaluated in dependency order.

  Links are evaluated after every recorded answer and every restore
  (decide.core/on-answers-shown!). Answers are never written: on Done the map
  {key reason} of what is moot is recorded as the report-only decision `moot`.
  Problems in the list are errors of kind \"moot\", reported once, at start; a link
  with a problem is dropped, except one missing its reason, which gets a written one."
  (:require [decide.core :as core]))

;; {:questions [link] in dependency order, :options [link]}
;; link: {:key :id :value (options only) :terms [{:id :values}] :reason (or nil)
;;        :container (questions) :target (options)}
(def links (atom nil))

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
  "{:terms [{:id :values}]} for `when`, or {:error message}."
  [w]
  (let [words (.filter (.split (.trim w) #"\s+") #(pos? (.-length %)))]
    (if (zero? (.-length words))
      {:error "its \"when\" is empty"}
      (loop [ws (seq words) terms []]
        (if-let [word (first ws)]
          (if-let [[id v] (split-at-eq word)]
            (if (seq (controls id))
              (recur (rest ws) (conj terms {:id id :values (vec (.split v "|"))}))
              {:error (str "its \"when\" reads \"" id "\", which no control on the page answers")})
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

(defn read-link
  "{:link link} for entry `entry` under `key`, with {:problem message} when its
  reason is missing; {:problem message} alone when the link can't be used."
  [key entry]
  (let [where (str "the link \"" key "\"")
        [id value] (or (split-at-eq key) [key nil])]
    (cond
      (or (not (plain-object? entry)) (not (string? (.-when entry))))
      {:problem (str where " needs {\"when\": \"…\", \"reason\": \"…\"}")}
      (not (seq (controls id)))
      {:problem (str where " names \"" id "\", which no control on the page answers")}
      :else
      (let [{:keys [terms error]} (parse-terms (.-when entry))
            place (cond
                    error {:error error}
                    (some? value) (option-target id value)
                    :else (if-let [c (js/document.querySelector (str "[data-question=\"" (js/CSS.escape id) "\"]"))]
                            {:container c}
                            {:error (str "no element has data-question=\"" id "\" to fold")}))
            reason (when (and (string? (.-reason entry)) (seq (.trim (.-reason entry)))) (.trim (.-reason entry)))]
        (if (:error place)
          {:problem (str where ": " (:error place))}
          (cond-> {:link (merge {:key key :id id :value value :terms terms :reason reason} place)}
            (nil? reason) (assoc :problem (str where " has no \"reason\", so the kit writes one"))))))))

(defn dependency-order
  "[ordered stuck]: question links ordered so each comes after the question links
  its terms read, and the ones left over because they form or depend on a cycle."
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

(defn read-links
  "{:links {:questions :options} :problems [message]} for the list's text, or
  {:problems [message]} alone when it isn't a JSON object."
  [text]
  (let [[parsed parse-error] (try [(js/JSON.parse text) nil] (catch :default e [nil (.-message e)]))]
    (if (not (plain-object? parsed))
      {:problems [(str "the link list isn't a JSON object" (when parse-error (str ": " parse-error)))]}
      (let [read (map (fn [[k v]] (read-link k v)) (js/Object.entries parsed))
            usable (vec (keep :link read))
            [ordered stuck] (dependency-order (vec (filter #(nil? (:value %)) usable)))]
        {:links {:questions ordered :options (vec (filter #(some? (:value %)) usable))}
         :problems (cond-> (vec (keep :problem read))
                     (seq stuck)
                     (conj (str "the links " (.join (into-array (map #(str "\"" (:key %) "\"") stuck)) ", ")
                                " form or depend on a cycle, so they're dropped")))}))))

;; ---- evaluating -------------------------------------------------------------------

(defn answer-of
  "The answer `id`'s controls show now, as a string: a chosen button's choice, a
  checked radio's value, a select's or a field's text, \"true\" or \"false\" for a
  checkbox; \"\" when unanswered."
  [id]
  (or (some (fn [el]
              (cond
                (= "BUTTON" (.-tagName el)) (when (.contains (.-classList el) "chosen") (.. el -dataset -choice))
                (= "radio" (.-type el)) (when (.-checked el) (.-value el))
                (= "checkbox" (.-type el)) (str (.-checked el))
                :else (when (pos? (.-length (.-value el))) (.-value el))))
            (controls id))
      ""))

(defn first-heading
  "The container's first child that is a heading, else nil."
  [container]
  (some #(when (.test #"^H[1-6]$" (.-tagName %)) %) (js/Array.from (.-children container))))

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
                                                (when (.includes (:values t) v) [(:id t) v])))
                                      (:terms link)))]
    (reduce (fn [acc link]
              (if-let [[id v] (holding-term link)]
                (do (when (nil? (:value link)) (.add moot-ids (:id link)))
                    (assoc acc (:key link) (or (:reason link) (written-reason id v))))
                acc))
            {} (concat questions options))))

;; ---- showing ------------------------------------------------------------------------

(defn toggle! [container button]
  (let [open? (.toggle (.-classList container) "hc-moot-open")]
    (.setAttribute button "aria-expanded" (str open?))
    (set! (.-textContent button) (if open? "Hide" "Show"))))

(defn bar-of [container] (.querySelector container ":scope > .hc-moot-bar"))

(defn show-question!
  "Folds and greys the link's container, with `reason` in view. A container that is
  already moot keeps whether the reader opened it."
  [{:keys [container]} reason]
  (let [bar (or (bar-of container)
                (let [reason-el (js/document.createElement "span")
                      button (js/document.createElement "button")
                      bar (js/document.createElement "div")
                      heading (first-heading container)]
                  (set! (.-className reason-el) "hc-moot-reason")
                  (set! (.-type button) "button")
                  (set! (.-className button) "hc-moot-toggle")
                  (.setAttribute button "aria-expanded" "false")
                  (set! (.-textContent button) "Show")
                  (.addEventListener button "click" (fn [_] (toggle! container button)))
                  (set! (.-className bar) "hc-moot-bar")
                  (.append bar reason-el button)
                  (if heading
                    (do (.add (.-classList heading) "hc-moot-head") (.after heading bar))
                    (.prepend container bar))
                  (.add (.-classList container) "hc-moot")
                  bar))]
    (set! (.-textContent (.querySelector bar ".hc-moot-reason")) (str "Moot: " reason))))

(defn unshow-question! [{:keys [container]}]
  (when-let [bar (bar-of container)]
    (.remove bar)
    (.remove (.-classList container) "hc-moot" "hc-moot-open")
    (some-> (first-heading container) .-classList (.remove "hc-moot-head"))))

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

(defn update!
  "Re-evaluates the links and shows the result."
  []
  (let [{:keys [questions options] :as ls} @links
        now (evaluate ls)]
    (doseq [l questions]
      (if-let [r (aget now (:key l))] (show-question! l r) (unshow-question! l)))
    (doseq [l options]
      (if-let [r (aget now (:key l))] (show-option! l r) (unshow-option! l)))
    (reset! moot-now now)))

(defn on-done
  "Capture phase: records `moot` before core's bubble-phase handler records `status`."
  [e]
  (when (.closest (.-target e) "[data-decision=status][data-choice=done]")
    (.record js/window.htmlChannel "moot" @moot-now)))

(defn start!
  "Reads the page's link list, reports its problems, shows what is moot now, and
  keeps it current. `report!` is (fn [kind message source]).
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
          (.addEventListener js/document "click" on-done true)
          (core/on-answers-shown! update!)
          (update!))))))
