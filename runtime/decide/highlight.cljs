(ns decide.highlight
  "Syntax highlighting with Shiki, for every code block and the inline code that
  names its language.

  Hosts (the element whose text is replaced by colored spans):
    <pre>…</pre>, <pre><code>…</code></pre>     a block; <code> is the host when it is the only child
    <code data-lang|data-src|class=language-x>  inline code; other inline code stays plain
  Skipped: <pre data-viz>, diff sources ([data-diff]), statechart figures, kit UI,
  and hosts that hold elements (the page's own markup inside code is kept). A host
  holding elements the browser doesn't know, such as <string> from an unescaped
  Array<string>, is reported instead: its text has lost those tags.

  Language, first match wins, read from the host and its <pre>:
    data-lang=\"ts\"          a Shiki id or alias; text, plaintext, plain and txt mean plain text
    class=\"language-ts\"     the Markdown habit, same names
    data-src=\"repo:a.clj\"   the file name, then its extension, through Shiki's aliases
                            and extension-languages
  No runtime guessing: a block with no language is reported as kind \"highlight\".
  So is an unknown name, and Shiki not loading. Inline code whose data-src names a
  file of unknown type stays plain, unreported. Reports are grouped by cause.

  Post: a highlighted host keeps its attributes and its exact textContent, and
  gets data-hl=\"<shiki id>\". Code links sit beside the host, so they survive.
  Colors are var(--shiki-token-*) (Shiki's css-variables theme); decide.css maps
  them to the --hc-code-* tokens."
  (:require [viz.versions :as versions]))

(def plain-names #{"text" "plaintext" "plain" "txt"})

(def extension-languages
  "File names and extensions Shiki has no alias for, mapped to a name it has."
  {"cljs" "clojure" "cljc" "clojure" "edn" "clojure" "bb" "clojure"
   "h" "c" "hpp" "cpp" "hh" "cpp" "cc" "cpp" "cxx" "cpp"
   "htm" "html" "ex" "elixir" "exs" "elixir" "ml" "ocaml" "mli" "ocaml"
   "pl" "perl" "pm" "perl" "jsonl" "json" "rake" "ruby" "gemspec" "ruby"
   "gemfile" "ruby" "rakefile" "ruby" "zshrc" "shellscript" "bashrc" "shellscript"})

(def skipped
  "Code inside these is never a host: other renderers, diff sources and kit UI."
  (str "[data-viz], [data-diff], .hc-diff, figure[data-statechart], .hc-src, .hc-viz, .hc-viz-error,"
       " #decide-code-links, #decide-tiers, #decide-offline"))

;; ---- Shiki ----------------------------------------------------------------------

(defn language-names
  "{name -> Shiki id} for every id, alias and extension-languages entry, lower case."
  [infos]
  (let [names {}]
    (doseq [info infos]
      (aset names (.-id info) (.-id info))
      (doseq [a (or (.-aliases info) [])] (aset names (.toLowerCase a) (.-id info))))
    (doseq [[n target] (js/Object.entries extension-languages)
            :when (and (not (get names n)) (get names target))]
      (aset names n (get names target)))
    names))

(defn ^:async load-shiki []
  (let [shiki (js-await (js/import versions/shiki))
        engine (js-await (js/import versions/shiki-js-engine))
        theme (.createCssVariablesTheme shiki {:name "hc" :variablePrefix "--shiki-"})
        hl (js-await (.createHighlighter shiki {:themes [theme] :langs []
                                                :engine (.createJavaScriptRegexEngine engine)}))]
    {:highlighter hl :names (language-names (.-bundledLanguagesInfo shiki))}))

(def shiki-promise (atom nil))

(defn shiki!
  "Promise of {:highlighter :names}, created once. Rejects when Shiki doesn't load."
  []
  (or @shiki-promise (reset! shiki-promise (load-shiki))))

(defn ^:async tokenize!
  "Lines of Shiki tokens ({content color fontStyle}) for `code` in Shiki id `lang`.
  Pre: `lang` came from language-of. The lines' contents joined with \\n are `code`."
  [lang code]
  (let [{:keys [highlighter]} (js-await (shiki!))]
    (js-await (.loadLanguage highlighter lang))
    (.codeToTokensBase highlighter code {:lang lang :theme "hc"})))

;; ---- languages ------------------------------------------------------------------

(defn file-name
  "The last path segment of a data-src (\"api:src/a.ts#L1\" -> \"a.ts\"), else nil."
  [src]
  (when (seq src)
    (last (.split (-> src (.replace #"^[^:]*:" "") (.replace #"#.*$" "")) "/"))))

(defn class-language [el]
  (some->> (.-classList el) js/Array.from (some #(second (re-find #"^(?:language|lang)-(.+)$" %)))))

(defn declared
  "What `els` (host first) declare: {:plain true}, {:name n}, or nil.
  data-lang on any of them wins over a language-* class."
  [els]
  (when-let [n (or (some #(not-empty (.getAttribute % "data-lang")) els)
                   (some class-language els))]
    (if (contains? plain-names (.toLowerCase n)) {:plain true} {:name n})))

(defn request-for-file
  "A request naming the file `name` (a path's last segment), or nil."
  [name]
  (when (seq name) {:file name}))

(defn language-of
  "{:lang id}, {:plain true} or {:problem cause} for request `req` (nil = none),
  given Shiki's `names`. A cause groups the reports of every host it hits."
  [req names]
  (cond
    (nil? req) {:problem "no language"}
    (:plain req) {:plain true}
    (:name req) (if-let [id (get names (.toLowerCase (:name req)))]
                  {:lang id}
                  {:problem (str "unknown language \"" (:name req) "\"")})
    :else (let [f (.toLowerCase (:file req))
                ext (second (re-find #"\.([^.]+)$" f))]
            (if-let [id (or (get names f) (and ext (get names ext)))]
              {:lang id}
              (if (contains? plain-names ext)
                {:plain true}
                {:problem (str "no language known for files like \"" (:file req) "\"")})))))

;; ---- hosts ---------------------------------------------------------------------

(defn sole-code-child [pre]
  (let [kids (.-children pre)]
    (when (and (= 1 (.-length kids)) (= "code" (.-localName (aget kids 0))))
      (aget kids 0))))

(defn text-only? [el] (zero? (.-childElementCount el)))

(defn unescaped-tags
  "Distinct lower-case names of the elements under `el` that HTML doesn't define
  (HTMLUnknownElement): what an unescaped < in code turns into, like <string>."
  [el]
  (->> (js/Array.from (.querySelectorAll el "*"))
       (filter #(instance? js/HTMLUnknownElement %))
       (map #(.-localName %))
       distinct
       vec))

(def unescaped-cause
  "an unescaped < that the browser read as a tag, which cut the text; write < as &lt; and & as &amp;")

(defn hosts
  "[{:host el :scope [els to read the language from] :block? bool :unescaped [tags]}]
  for the page's code, in document order. A host with :unescaped tags isn't highlighted."
  []
  (let [blocks (for [pre (js/Array.from (js/document.querySelectorAll "pre"))
                     :when (not (.closest pre skipped))
                     :let [code (sole-code-child pre)
                           host (or code pre)
                           tags (unescaped-tags pre)]
                     :when (and (not (.hasAttribute host "data-hl")) (or (text-only? host) (seq tags)))]
                 {:host host :scope (if code [code pre] [pre]) :block? true :unescaped tags})
        inline (for [code (js/Array.from (js/document.querySelectorAll
                                          "code[data-lang], code[data-src], code[class*=lang]"))
                     :when (and (not (.closest code "pre")) (not (.closest code skipped))
                                (not (.hasAttribute code "data-hl"))
                                (or (declared [code]) (.hasAttribute code "data-src")))
                     :let [tags (unescaped-tags code)]
                     :when (or (text-only? code) (seq tags))]
                 {:host code :scope [code] :block? false :unescaped tags})]
    (vec (concat blocks inline))))

(defn request [{:keys [scope]}]
  (or (declared scope)
      (request-for-file (some #(file-name (.getAttribute % "data-src")) scope))))

(defn describe [el]
  (let [src (.getAttribute el "data-src")
        text (.trim (.-textContent el))]
    (str "<" (.-localName el) (if src (str " data-src=\"" src "\"") "") ">"
         (if (> (.-length text) 30) (str (subs text 0 30) "…") text))))

;; ---- rendering -------------------------------------------------------------------

(defn token-span
  "A span for Shiki token `t` holding `text` (all of t's content when omitted)."
  ([t] (token-span t (.-content t)))
  ([t text]
   (let [s (js/document.createElement "span")
         fs (or (.-fontStyle t) 0)]
     (set! (.-textContent s) text)
     (when (.-color t) (set! (.. s -style -color) (.-color t)))
     (when (pos? (bit-and fs 1)) (set! (.. s -style -fontStyle) "italic"))
     (when (pos? (bit-and fs 2)) (set! (.. s -style -fontWeight) "bold"))
     s)))

(defn ^:async highlight!
  "Replaces `host`'s text with colored spans for Shiki id `lang`."
  [host lang]
  (let [lines (js-await (tokenize! lang (.-textContent host)))
        frag (js/document.createDocumentFragment)]
    (doseq [[i line] (map-indexed vector lines)]
      (when (pos? i) (.append frag "\n"))
      (doseq [t line] (.append frag (token-span t))))
    (.replaceChildren host frag)
    (.setAttribute host "data-hl" lang)))

(defn report-groups!
  "One error per cause: the count and the first three hosts."
  [report! problems]
  (doseq [[cause hs] (group-by :cause problems)]
    (report! "highlight"
             (str (count hs) " code element(s): " cause ", so they stay plain"
                  (if (= cause unescaped-cause) "" ". Set data-lang (data-lang=\"text\" for plain text)")
                  ": " (.join (mapv #(str (describe (:host %))
                                          (if (seq (:unescaped %))
                                            (str " (read as " (.join (into-array (map (fn [t] (str "<" t ">")) (:unescaped %))) ", ") ")")
                                            ""))
                                    (take 3 hs)) ", ")
                  (if (> (count hs) 3) ", …" ""))
             cause)))

(defn ^:async start!
  "Highlights the page's code. `report!` is (fn [kind message source]).
  Loads Shiki only when some host names a language or a file."
  [report!]
  (let [all (hosts)
        unescaped (filterv #(seq (:unescaped %)) all)
        hs (mapv #(assoc % :req (request %)) (filterv #(empty? (:unescaped %)) all))
        wanted (filterv #(and (:req %) (not (:plain (:req %)))) hs)
        unnamed (filterv #(and (:block? %) (nil? (:req %))) hs)]
    (report-groups! report! (mapv #(assoc % :cause unescaped-cause) unescaped))
    (report-groups! report! (mapv #(assoc % :cause "no language") unnamed))
    (when (seq wanted)
      (try
        (let [{:keys [names]} (js-await (shiki!))
              problems (atom [])]
          (doseq [h wanted
                  :let [r (language-of (:req h) names)]]
            (if-let [lang (:lang r)]
              (try (js-await (highlight! (:host h) lang))
                   (catch :default e
                     (swap! problems conj (assoc h :cause (str "failed in " lang ": " (.-message e))))))
              ;; inline code linked to a file of unknown type is a link, not code: plain, quietly
              (when (or (:block? h) (not (:file (:req h))))
                (swap! problems conj (assoc h :cause (:problem r))))))
          (report-groups! report! @problems))
        (catch :default e
          (report! "highlight" (str "Shiki didn't load, so " (count wanted)
                                    " code element(s) stay plain: " (.-message e))
                   "shiki"))))))
