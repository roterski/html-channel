(ns decide.diff
  "Renders code diffs from a patch or from two texts, with optional per-hunk decisions.

    <pre data-diff=\"ID\" data-repo=\"NAME\">…unified patch…</pre>
        a patch, one or more files (git diff output); data-repo links its paths
    <figure data-diff=\"ID\">
      <pre data-diff-old data-src=\"NAME:PATH#L10\">…</pre>
      <pre data-diff-new>…</pre>
    </figure>
        two texts; data-src (on either) names the file and the first line number
    data-hunk-choices=\"accept reject\"  on either form: each hunk gets one button per
        choice (data-decision=\"ID.KEY\"), a Clear button and an ID.KEY-note textarea
    data-lang                          on either form, as for highlighting

  Both inputs become the same hunks (jsdiff parsePatch / structuredPatch). A hunk's
  KEY hashes its file path with its removed and added lines, so it survives a
  revision that only shifts it. Hunks whose changes repeat in a file hash their
  context too; an exact repeat gets -2, -3, … On Done the
  map {ID {KEY {file old new}}} is recorded as the report-only decision `hunks`,
  so the agent reads which hunk each answer is about.

  Lines are syntax-colored (decide.highlight) and changed words marked on top.
  Two texts are highlighted whole; a patch per hunk, so a hunk that starts inside
  a multi-line string or comment can be colored wrongly. Two texts also offer the
  unchanged lines between hunks behind \"Show N unchanged lines\".
  Layout: split on wide containers, unified on narrow ones (CSS container query),
  or the reader's pick, kept in their browser for every page.

  The split layout is built the first time it shows. Each sign also carries
  visually hidden \"added\" or \"removed\" text for screen readers.

  The rendered diff follows its source, which is then hidden. When it can't render
  (jsdiff didn't load, tags the browser parsed out of unescaped text, an
  unparseable patch, a missing side, no changes), the source stays visible with
  a notice and an error of kind \"diff\". Hunk choices without a page-unique ID
  are a diff error too; the diff then renders read-only. When Shiki or a grammar
  doesn't load, the diff renders plain and the problem is a \"highlight\" error."
  (:require [decide.code-links :as code-links]
            [decide.core :as core]
            [decide.highlight :as highlight]
            [viz.versions :as versions]))

(def layout-key "html-channel:diff-layout")
(def layouts ["auto" "split" "unified"])
(def context-lines 3)
(def min-similarity
  "Changed words are marked only on line pairs sharing at least this share of characters."
  0.25)
(def max-marked-line
  "Changed words are marked only on line pairs no longer than this, in characters."
  1000)

;; {diff-id {hunk-key {:file :old :new}}} for diffs rendered with hunk choices
(def hunk-map (atom {}))

(def jsdiff-promise (atom nil))

(defn jsdiff! [] (or @jsdiff-promise (reset! jsdiff-promise (js/import versions/jsdiff))))

;; ---- helpers --------------------------------------------------------------------

(defn el! [tag attrs & children]
  (let [e (js/document.createElement tag)]
    (doseq [[k v] (js/Object.entries (or attrs {}))] (.setAttribute e k v))
    (doseq [c children :when (some? c)] (.append e c))
    e))

(defn fnv1a
  "32-bit FNV-1a of `s`, as 8 hex digits."
  [s]
  (loop [i 0 h 0x811c9dc5]
    (if (< i (.-length s))
      (recur (inc i) (js/Math.imul (bit-xor h (.charCodeAt s i)) 0x01000193))
      (.padStart (.toString (unsigned-bit-shift-right h 0) 16) 8 "0"))))

(defn ensure-newline [s] (if (or (= "" s) (.endsWith s "\n")) s (str s "\n")))

(defn patch-path [name]
  (when (and (seq name) (not= "/dev/null" name)) (.replace name #"^[ab]/" "")))

(defn file-name [path] (last (.split (or path "") "/")))

(defn numeric-sort [xs] (.sort (into-array xs) (fn [a b] (- a b))))

;; ---- model ----------------------------------------------------------------------

(defn hunk-of [h]
  {:old-start (.-oldStart h) :old-lines (.-oldLines h)
   :new-start (.-newStart h) :new-lines (.-newLines h)
   :lines (filterv #(not (.startsWith % "\\")) (.-lines h))})

(defn patch-model [jsdiff pre]
  {:files (->> (.parsePatch jsdiff (.-textContent pre))
               (mapv (fn [f] {:path (or (patch-path (.-newFileName f)) (patch-path (.-oldFileName f)) "")
                              :deleted? (nil? (patch-path (.-newFileName f)))
                              :hunks (mapv hunk-of (.-hunks f))}))
               (filterv #(seq (:hunks %))))
   :repo (not-empty (.getAttribute pre "data-repo"))
   :old-offset 0 :new-offset 0
   :lang-scope [pre]})

(defn texts-model [jsdiff fig]
  (let [old-pre (.querySelector fig "pre[data-diff-old]")
        new-pre (.querySelector fig "pre[data-diff-new]")]
    (if-not (and old-pre new-pre)
      {:error "needs a <pre data-diff-old> and a <pre data-diff-new> inside it"}
      (let [old-src (code-links/parse-src (.getAttribute old-pre "data-src"))
            new-src (code-links/parse-src (.getAttribute new-pre "data-src"))
            src (or new-src old-src)
            path (or (:path src) "")
            old-text (ensure-newline (.-textContent old-pre))
            new-text (ensure-newline (.-textContent new-pre))
            p (.structuredPatch jsdiff path path old-text new-text "" "" {:context context-lines})]
        {:files (filterv #(seq (:hunks %)) [{:path path :hunks (mapv hunk-of (.-hunks p))}])
         :texts {:old old-text :new new-text}
         :repo (:repo src)
         :old-offset (dec (or (:start old-src) (:start new-src) 1))
         :new-offset (dec (or (:start new-src) (:start old-src) 1))
         :lang-scope [fig old-pre new-pre]}))))

(defn rows
  "The hunk's lines as {:kind ctx|del|add :old n :new n :text}, numbered from the
  hunk's starts (before any data-src offset)."
  [hunk]
  (loop [ls (:lines hunk) o (:old-start hunk) n (:new-start hunk) out []]
    (if-let [l (first ls)]
      (let [text (subs l 1)]
        (case (subs l 0 1)
          "-" (recur (rest ls) (inc o) n (conj out {:kind "del" :old o :text text}))
          "+" (recur (rest ls) o (inc n) (conj out {:kind "add" :new n :text text}))
          (recur (rest ls) (inc o) (inc n) (conj out {:kind "ctx" :old o :new n :text text}))))
      out)))

(defn blocks
  "`rows` grouped as {:ctx row} and change blocks {:dels [..] :adds [..]}."
  [rows]
  (let [out [] cur (atom nil)
        flush! #(when @cur (.push out @cur) (reset! cur nil))]
    (doseq [r rows]
      (case (:kind r)
        "ctx" (do (flush!) (.push out {:ctx r}))
        "del" (do (when (and @cur (seq (:adds @cur))) (flush!))
                  (when-not @cur (reset! cur {:dels [] :adds []}))
                  (.push (:dels @cur) r))
        (do (when-not @cur (reset! cur {:dels [] :adds []}))
            (.push (:adds @cur) r))))
    (flush!)
    out))

(defn mark-words!
  "Sets :marks, [[start end] …] character ranges, on the paired removed and added
  lines of each change block whose texts are similar enough."
  [jsdiff rows]
  (doseq [b (blocks rows)
          :when (:dels b)
          [d a] (map vector (:dels b) (:adds b))
          :when (and (<= (.-length (:text d)) max-marked-line) (<= (.-length (:text a)) max-marked-line))]
    (let [parts (.diffWordsWithSpace jsdiff (:text d) (:text a))
          common (reduce + 0 (map #(if (or (.-added %) (.-removed %)) 0 (.-length (.-value %))) parts))
          longest (max 1 (.-length (:text d)) (.-length (:text a)))]
      (when (>= (/ common longest) min-similarity)
        (let [dm [] am [] o (atom 0) n (atom 0)]
          (doseq [p parts
                  :let [len (.-length (.-value p))]]
            (cond
              (.-removed p) (do (.push dm [@o (+ @o len)]) (swap! o + len))
              (.-added p) (do (.push am [@n (+ @n len)]) (swap! n + len))
              :else (do (swap! o + len) (swap! n + len))))
          (aset d "marks" dm)
          (aset a "marks" am))))))

;; ---- language and tokens ------------------------------------------------------------

(defn ^:async language
  "{:lang id}, {:plain true} or {:problem cause} for a diff's file at `path`.
  Shiki not loading is a problem, not an exception."
  [model path]
  (let [req (or (highlight/declared (:lang-scope model))
                (highlight/request-for-file (file-name path)))]
    (if (or (nil? req) (:plain req))
      (highlight/language-of req nil)
      (try (highlight/language-of req (:names (js-await (highlight/shiki!))))
           (catch :default e {:problem (str "Shiki didn't load (" (.-message e) ")")})))))

(defn ^:async tokens-or-nil
  "Token lines for `code` in `lang`; nil without a language, or when the grammar
  doesn't load, which then sets `problem` (an atom). Once `problem` is set, it
  doesn't try again."
  [lang code problem]
  (when (and lang (not @problem))
    (try (js-await (highlight/tokenize! lang code))
         (catch :default e (reset! problem (str lang " didn't load (" (.-message e) ")")) nil))))

(defn ^:async tokens-of-lines
  "Token lines for `texts` (each one line), or nil (see tokens-or-nil)."
  [lang texts problem]
  (when (seq texts)
    (js-await (tokens-or-nil lang (.join (into-array texts) "\n") problem))))

(defn ^:async color-patch-rows!
  "Sets :tokens on each row of a patch hunk, highlighting its old and new sides."
  [lang rows problem]
  (let [olds (filterv :old rows)
        news (filterv :new rows)
        ot (js-await (tokens-of-lines lang (mapv :text olds) problem))
        nt (js-await (tokens-of-lines lang (mapv :text news) problem))]
    (when ot (doseq [[i r] (map-indexed vector olds)] (aset r "oldTokens" (aget ot i))))
    (when nt (doseq [[i r] (map-indexed vector news)] (aset r "newTokens" (aget nt i))))
    (doseq [r rows]
      (aset r "tokens" (if (= "del" (:kind r)) (:oldTokens r) (:newTokens r))))))

(defn color-text-rows!
  "Sets :tokens on rows from whole-text token lines `ot` and `nt` (nil: plain)."
  [ot nt rows]
  (doseq [r rows]
    (aset r "tokens" (if (= "del" (:kind r))
                       (some-> ot (aget (dec (:old r))))
                       (some-> nt (aget (dec (:new r))))))))

;; ---- rendering ---------------------------------------------------------------------

(defn line-nodes
  "`text` as spans: its tokens' colors, with marked character ranges wrapped."
  [text tokens marks]
  (let [frag (js/document.createDocumentFragment)
        pos (atom 0)]
    (doseq [t (or tokens [{:content text}])]
      (let [start @pos
            end (+ start (.-length (.-content t)))
            cuts (numeric-sort (distinct (concat [start end]
                                                 (filter #(and (> % start) (< % end)) (mapcat identity marks)))))]
        (doseq [[a b] (map vector cuts (rest cuts))]
          (let [s (highlight/token-span t (subs (.-content t) (- a start) (- b start)))]
            (when (some (fn [[ms me]] (and (<= ms a) (<= b me))) marks)
              (.add (.-classList s) "hc-diff-word"))
            (.append frag s)))
        (reset! pos end)))
    frag))

(def signs {"del" "−" "add" "+" "ctx" " "})
(def spoken {"del" "removed" "add" "added"})

(defn sign-cell
  "The sign of a `kind` row: shown, plus a word for screen readers."
  [kind]
  (el! "td" {"class" "hc-diff-sign"}
       (el! "span" {"aria-hidden" "true"} (get signs kind))
       (when-let [w (get spoken kind)] (el! "span" {"class" "hc-sr"} w))))

(defn side-cells
  "Number, sign and code cells for `row` on one side; empty cells for nil."
  [row number]
  (if row
    [(el! "td" {"class" "hc-diff-no"} (str number))
     (sign-cell (:kind row))
     (el! "td" {"class" "hc-diff-code"} (line-nodes (:text row) (:tokens row) (:marks row)))]
    [(el! "td" {"class" "hc-diff-no hc-diff-empty"})
     (el! "td" {"class" "hc-diff-sign hc-diff-empty"})
     (el! "td" {"class" "hc-diff-code hc-diff-empty"})]))

(defn unified-table [rows num]
  (el! "table" {"class" "hc-diff-table"}
       (let [tb (el! "tbody" {})]
         (doseq [r rows]
           (.append tb (el! "tr" {"class" (str "hc-diff-" (:kind r))}
                            (el! "td" {"class" "hc-diff-no"} (if (:old r) (str (num :old r)) ""))
                            (el! "td" {"class" "hc-diff-no"} (if (:new r) (str (num :new r)) ""))
                            (sign-cell (:kind r))
                            (el! "td" {"class" "hc-diff-code"} (line-nodes (:text r) (:tokens r) (:marks r))))))
         tb)))

(defn split-row [left right num]
  (let [tr (el! "tr" {})
        cell-kind (fn [r] (if r (str "hc-diff-" (:kind r)) "hc-diff-none"))]
    (doseq [c (side-cells left (when left (num :old left)))] (.add (.-classList c) (cell-kind left)) (.append tr c))
    (doseq [c (side-cells right (when right (num :new right)))] (.add (.-classList c) (cell-kind right)) (.append tr c))
    tr))

(defn split-table [rows num]
  (el! "table" {"class" "hc-diff-table"}
       (let [tb (el! "tbody" {})]
         (doseq [b (blocks rows)]
           (if-let [r (:ctx b)]
             (.append tb (split-row r r num))
             (doseq [i (range (max (count (:dels b)) (count (:adds b))))]
               (.append tb (split-row (get (:dels b) i) (get (:adds b) i) num)))))
         tb)))

(defn body
  "Both layouts of `rows`; CSS shows one. The split table is built when it first
  shows (build-shown-splits!). `num` is (fn [side row]) -> shown line number."
  [rows num]
  (let [split (el! "div" {"class" "hc-diff-split"})]
    (aset split "hcBuild" #(split-table rows num))
    (el! "div" {"class" "hc-diff-body"}
         (el! "div" {"class" "hc-diff-unified"} (unified-table rows num))
         split)))

(defn build-shown-splits!
  "Builds the split tables under `root` that CSS now shows and that aren't built yet."
  [root]
  (doseq [d (js/Array.from (.querySelectorAll root ".hc-diff-split"))
          :let [build (aget d "hcBuild")]
          :when (and build (not= "none" (.-display (js/getComputedStyle d))))]
    (aset d "hcBuild" nil)
    (.append d (build))))

(defn range-label
  "start,count the way git writes it, where an empty range starts at the line
  before it (-0,0 for a new file). Pre: `start` is jsdiff's, which counts an
  empty range from the line after (formatPatch undoes it the same way)."
  [start count]
  (str (if (zero? count) (dec start) start) "," count))

(defn hunk-head [model {:keys [path deleted?]} hunk]
  (let [new-start (+ (:new-start hunk) (:new-offset model))
        nl (:new-lines hunk)
        head (str "@@ -" (range-label (+ (:old-start hunk) (:old-offset model)) (:old-lines hunk))
                  " +" (range-label new-start nl) " @@")]
    (el! "div" {"class" "hc-diff-hunk-head"}
         (el! "span" {} head)
         (when (and (:repo model) (seq path) (not deleted?))
           (el! "code" {"data-src" (str (:repo model) ":" path "#L" new-start (if (> nl 1) (str "-L" (+ new-start nl -1)) ""))}
                (str path ":" new-start))))))

(defn file-head
  "The file's path, linked to the code unless the patch deletes the file."
  [model {:keys [path deleted?]}]
  (el! "div" {"class" "hc-diff-file-head"}
       (cond
         (not (seq path)) (el! "code" {"data-lang" "text"} "(no file name)")
         deleted? (el! "code" {"data-lang" "text"} (str path " (deleted)"))
         (:repo model) (el! "code" {"data-src" (str (:repo model) ":" path)} path)
         :else (el! "code" {"data-lang" "text"} path))))

(defn choice-label [choice] (str (.toUpperCase (subs choice 0 1)) (subs choice 1)))

(defn choice-controls [id choices]
  (let [d (el! "div" {"class" "hc-hunk-choice"})]
    (doseq [c choices]
      (.append d (el! "button" {"type" "button" "data-decision" id "data-choice" c} (choice-label c))))
    (.append d (el! "button" {"type" "button" "data-clear" id "class" "hc-hunk-clear"} "Clear"))
    (.append d (el! "textarea" {"rows" "2" "data-decision" (str id "-note")
                                "placeholder" "Another answer, or a comment"}))
    d))

(defn gap
  "A \"Show N unchanged lines\" toggle for old lines [from, to] of a two-text diff:
  the first click builds those lines right after it, later clicks hide and show
  them again. nil when the range is empty."
  [model from to delta ot]
  (when (<= from to)
    (let [n (inc (- to from))
          label (fn [verb] (str verb " " n " unchanged line" (if (= 1 n) "" "s")))
          old-lines (.split (:old (:texts model)) "\n")
          g (el! "div" {"class" "hc-diff-gap"})
          btn (el! "button" {"type" "button" "class" "hc-diff-expand" "aria-expanded" "false"} (label "Show"))
          lines (atom nil)]
      (.addEventListener btn "click"
                         (fn [_]
                           (when-not @lines
                             (let [rs (mapv (fn [o] {:kind "ctx" :old o :new (+ o delta) :text (aget old-lines (dec o))
                                                     :tokens (some-> ot (aget (dec o)))})
                                            (range from (inc to)))
                                   b (body rs (fn [side r] (+ (get r side) (get model (if (= side :old) :old-offset :new-offset)))))]
                               (set! (.-hidden b) true)
                               (.after g b)
                               (reset! lines b)))
                           (let [show? (.-hidden @lines)]
                             (set! (.-hidden @lines) (not show?))
                             (.setAttribute btn "aria-expanded" (str show?))
                             (set! (.-textContent btn) (label (if show? "Hide" "Show")))
                             (when show? (build-shown-splits! (.closest g ".hc-diff"))))))
      (.append g btn)
      g)))

(defn hunk-keys
  "Keys for `hunks` of the file at `path`, in order. A key hashes the path and the
  changed lines; hunks whose changed lines repeat in the file hash their context
  too; an exact repeat then gets -2, -3, …"
  [path hunks]
  (let [hash-of (fn [lines] (fnv1a (str path "\u0000" (.join (into-array lines) "\n"))))
        changed (mapv (fn [h] (hash-of (filter #(not (.startsWith % " ")) (:lines h)))) hunks)
        counts (reduce (fn [m k] (assoc m k (inc (get m k 0)))) {} changed)
        hashes (mapv (fn [i] (if (> (get counts (get changed i)) 1) (hash-of (:lines (get hunks i))) (get changed i)))
                     (range (count hunks)))
        seen (atom {})]
    (mapv (fn [k] (let [n (get (swap! seen update k #(inc (or % 0))) k)] (if (> n 1) (str k "-" n) k)))
          hashes)))

(defn ^:async file-section
  "The rendered file: header, hunks, gaps (two texts) and, with `id`, hunk choices.
  Returns {:node :hunks {key {:file :old :new}} :problem cause-or-nil}."
  [jsdiff model file id choices]
  (let [path (:path file)
        lang-r (js-await (language model path))
        lang (:lang lang-r)
        problem (atom (:problem lang-r))
        texts (:texts model)
        ot (when texts (js-await (tokens-or-nil lang (:old texts) problem)))
        nt (when texts (js-await (tokens-or-nil lang (:new texts) problem)))
        num (fn [side r] (+ (get r side) (get model (if (= side :old) :old-offset :new-offset))))
        section (el! "div" {"class" "hc-diff-file"} (file-head model file))
        keyed {}
        hunks (:hunks file)
        ks (hunk-keys path hunks)
        old-count (when texts (dec (.-length (.split (:old texts) "\n"))))]
    (doseq [[i h] (map-indexed vector hunks)]
      (let [rs (rows h)]
        (mark-words! jsdiff rs)
        (if texts (color-text-rows! ot nt rs) (js-await (color-patch-rows! lang rs problem)))
        (when texts
          (let [prev (get hunks (dec i))
                from (if prev (+ (:old-start prev) (:old-lines prev)) 1)]
            (some->> (gap model from (dec (:old-start h)) (- (:new-start h) (:old-start h)) ot) (.append section))))
        (.append section (hunk-head model file h) (body rs num))
        (when id
          (let [k (get ks i)]
            (aset keyed k {:file path
                          :old (range-label (+ (:old-start h) (:old-offset model)) (:old-lines h))
                          :new (range-label (+ (:new-start h) (:new-offset model)) (:new-lines h))})
            (.append section (choice-controls (str id "." k) choices))))))
    (when (and texts (seq hunks))
      (let [l (last hunks)]
        (some->> (gap model (+ (:old-start l) (:old-lines l)) old-count
                      (- (+ (:new-start l) (:new-lines l)) (+ (:old-start l) (:old-lines l))) ot)
                 (.append section))))
    {:node section :hunks keyed :problem @problem}))

(defn stored-layout []
  (try (let [v (.getItem js/localStorage layout-key)] (if (.includes layouts v) v "auto"))
       (catch :default _ "auto")))

(defn set-layout! [v]
  (try (.setItem js/localStorage layout-key v) (catch :default _ nil))
  (doseq [d (js/Array.from (js/document.querySelectorAll ".hc-diff"))]
    (set! (.. d -dataset -layout) v)
    (doseq [b (js/Array.from (.querySelectorAll d ".hc-diff-layout button"))]
      (.setAttribute b "aria-pressed" (str (= v (.. b -dataset -layout))))))
  (build-shown-splits! js/document))

(defn layout-bar []
  (let [bar (el! "div" {"class" "hc-diff-layout" "role" "group" "aria-label" "Diff layout"})
        current (stored-layout)]
    (doseq [v layouts]
      (let [b (el! "button" {"type" "button" "data-layout" v "aria-pressed" (str (= v current))}
                   (choice-label v))]
        (.addEventListener b "click" (fn [_] (set-layout! v)))
        (.append bar b)))
    bar))

;; ---- one diff --------------------------------------------------------------------

(defn fail! [report! src label message]
  (.after src (el! "div" {"class" "hc-viz-error"} (str label ": " message)))
  (report! "diff" (str label ": " message) label))

(defn diff-label [src i]
  (if-let [id (not-empty (.trim (or (.getAttribute src "data-diff") "")))]
    (str "diff \"" id "\"")
    (str "diff #" i)))

(defn choices-of [src]
  (some->> (.getAttribute src "data-hunk-choices") (re-seq #"\S+") vec not-empty))

(defn ^:async render-diff!
  "Renders diff source `src` (the `i`th on the page) after it and hides it.
  `id-counts` counts each data-diff value on the page."
  [jsdiff src i id-counts report!]
  (let [id (not-empty (.trim (or (.getAttribute src "data-diff") "")))
        label (diff-label src i)
        tags (highlight/unescaped-tags src)
        model (cond
                (seq tags) {:error (str "the browser read " (.join (into-array (map #(str "<" % ">") tags)) ", ")
                                        " in it as HTML tags, so its text is cut; write < as &lt; and & as &amp;")}
                (= "figure" (.-localName src)) (texts-model jsdiff src)
                :else (try (patch-model jsdiff src)
                           (catch :default e {:error (str "the patch doesn't parse: " (.-message e)
                                                          ". Check that each @@ header's line counts match the lines under it")})))
        choices (choices-of src)]
    (cond
      (:error model) (fail! report! src label (:error model))
      (empty? (:files model)) (fail! report! src label (if (:texts model)
                                                         "the two texts are the same"
                                                         "no hunks found; is it a unified diff?"))
      :else
      (let [choice-id (when choices
                        (cond
                          (nil? id) (do (report! "diff" (str label ": data-hunk-choices needs a data-diff ID; rendered read-only") label) nil)
                          (> (get id-counts id) 1) (do (report! "diff" (str label ": its ID is used by another diff; rendered read-only") label) nil)
                          :else id))
            container (el! "div" {"class" "hc-diff" "data-layout" (stored-layout)} (layout-bar))
            diff-keys {}]
        (doseq [f (:files model)]
          (let [{:keys [node hunks problem]} (js-await (file-section jsdiff model f choice-id choices))]
            (.append container node)
            (doseq [[k v] (js/Object.entries hunks)] (aset diff-keys k v))
            (when problem
              (report! "highlight" (str label ": " problem ", so it stays plain. Set data-lang on the diff.")
                       label))))
        (.after src container)
        (set! (.-hidden src) true)
        (build-shown-splits! container)
        (.observe (js/ResizeObserver. (fn [_] (build-shown-splits! container))) container)
        (when choice-id (swap! hunk-map assoc choice-id diff-keys))
        (core/restore-within! container)))))

(defn on-done
  "Capture phase: records `hunks` before core's bubble-phase handler records `status`."
  [e]
  (when (and (seq (js/Object.keys @hunk-map))
             (.closest (.-target e) "[data-decision=status][data-choice=done]"))
    (.record js/window.htmlChannel "hunks" @hunk-map)))

(defn ^:async start!
  "Renders the page's diffs. `report!` is (fn [kind message source]).
  Pre: decide.core has started, so restored answers and htmlChannel.record work."
  [report!]
  (let [srcs (js/Array.from (js/document.querySelectorAll "pre[data-diff], figure[data-diff]"))]
    (when (seq srcs)
      (.addEventListener js/document "click" on-done true)
      (let [id-counts (reduce (fn [m s] (let [v (.trim (or (.getAttribute s "data-diff") ""))]
                                          (if (seq v) (assoc m v (inc (get m v 0))) m)))
                              {} srcs)]
        (try
          (let [jsdiff (js-await (jsdiff!))]
            (doseq [[i src] (map-indexed vector srcs)]
              (try (js-await (render-diff! jsdiff src i id-counts report!))
                   (catch :default e (fail! report! src (diff-label src i) (str "didn't render: " (.-message e)))))))
          (catch :default e
            (doseq [src srcs] (.after src (el! "div" {"class" "hc-viz-error"} "jsdiff didn't load, so this diff shows as source")))
            (report! "diff" (str "jsdiff didn't load, so " (count srcs) " diff(s) show as source: " (.-message e))
                     "jsdiff")))))))
