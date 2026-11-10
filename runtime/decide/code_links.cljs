(ns decide.code-links
  "Links from code references on the page to the reader's checkout or GitHub.

    <meta name=\"hc-repo\" content=\"name=api; root=/abs/checkout; web=https://github.com/o/r/blob/<sha>/\">
        one per repo; `web` is optional
    <pre data-src=\"api:src/a.ts#L10-L20\">…</pre>, <pre><code data-src=…>…</code></pre>
        a block: a header line above it links to the code, with a copy button
    <code data-src=\"api:src/a.ts#L12\">…</code>
        inline: the code becomes the link, followed by a copy button;
        inside the page's own <a>, only the copy button is added
    <a data-src=\"api:src/a.ts#L12\">…</a>
        HTML or SVG: only its href (and xlink:href) is managed

  A bar at the top of the page sets the format (VS Code or GitHub) and each
  repo's root and GitHub URL. Each setting resolves to this page's value, else
  the shared default, else the agent's meta. Page values live in localStorage
  under the page path. Shared defaults change only through Save as default,
  and apply to a repo when at least one identifier (name, agent root, remote)
  is on both sides and every identifier on both sides agrees.

  PATH may contain spaces; percent-escapes in it are decoded (`%23` for `#`).
  References that appear after load, or get their data-src later, are linked
  when they appear; the bar appears with the first one. Changing or removing a
  linked reference's data-src replaces or removes its link.
  On Done, the settings in effect are recorded as the decision `sources`,
  {format, repos {name {root default overridden}}}, queued before `status`.
  Malformed metas and references are reported as kind \"code-link\" and stay
  plain text.")

(def shared-key "html-channel:code-links:shared")

(defn page-key [] (str "html-channel:code-links:" js/location.pathname))

(def formats [["vscode" "VS Code"] ["github" "GitHub"]])

(def name-re #"^[a-z0-9._-]+$")
(def web-re #"^https://github\.com/[^/\s]+/[^/\s]+/blob/[^/\s]+/?$")
(def src-re #"^([a-z0-9._-]+):([^#\s](?:[^#]*[^#\s])?)(?:#L(\d+)(?:-L?(\d+))?)?$")
(def absolute-re #"^(/|[A-Za-z]:[\\/])")

(def xlink-ns "http://www.w3.org/1999/xlink")
(def svg-ns "http://www.w3.org/2000/svg")

;; :repos   {name {:name :root :web :remote}}, from the metas at start
;; :report! (fn [kind message source])
;; :bar     the settings bar, nil until the first valid reference
(def state (atom {:repos {} :report! nil :bar nil}))

;; ---- parsing -------------------------------------------------------------------

(defn present
  "`s` trimmed, or nil when it isn't a non-blank string."
  [s]
  (when (string? s)
    (let [t (.trim s)] (when (pos? (count t)) t))))

(defn without-trailing-slash [p] (.replace p #"/+$" ""))

(defn absolute-path
  "`p` trimmed when it is an absolute path, else nil."
  [p]
  (when-let [p (present p)] (when (.test absolute-re p) p)))

(defn valid-web
  "`u` with a trailing slash when it is a GitHub blob URL, else nil."
  [u]
  (when-let [u (present u)]
    (when (.test web-re u)
      (if (.endsWith u "/") u (str u "/")))))

(defn remote-of
  "The repo URL a GitHub blob URL points into: web without /blob/<ref>/."
  [web]
  (some-> web (.replace #"/blob/[^/]+/?$" "")))

(defn meta-fields
  "{key value} from `name=api; root=/x; web=…`."
  [content]
  (reduce (fn [m part]
            (let [i (.indexOf part "=")]
              (if (pos? i)
                (assoc m (.trim (subs part 0 i)) (.trim (subs part (inc i))))
                m)))
          {} (.split (or content "") ";")))

(defn repo-problem
  "Why fields `f` don't declare a repo, or nil."
  [f]
  (let [n (get f "name") root (get f "root") web (get f "web")]
    (cond
      (not (and (string? n) (.test name-re n))) (str "name " (js/JSON.stringify n) " must match [a-z0-9._-]+")
      (not (absolute-path root)) (str "repo " n ": root must be an absolute path")
      (and (present web) (not (valid-web web))) (str "repo " n ": web must look like https://github.com/OWNER/REPO/blob/SHA/"))))

(defn read-repos
  "{name repo} declared by the page's <meta name=hc-repo>; a malformed or
  duplicate meta is reported and skipped."
  [report!]
  (reduce (fn [repos el]
            (let [content (.getAttribute el "content")
                  f (meta-fields content)
                  problem (or (repo-problem f)
                              (when (js/Object.hasOwn repos (get f "name"))
                                (str "repo " (get f "name") " is declared twice")))]
              (if problem
                (do (report! "code-link" (str "<meta name=\"hc-repo\">: " problem) content) repos)
                (let [web (valid-web (get f "web"))]
                  (assoc repos (get f "name")
                         {:name (get f "name") :root (without-trailing-slash (get f "root"))
                          :web web :remote (remote-of web)})))))
          {} (js/Array.from (js/document.querySelectorAll "meta[name=hc-repo]"))))

(defn decoded
  "`s` with its percent-escapes decoded, or nil when one is malformed."
  [s]
  (try (js/decodeURIComponent s) (catch :default _ nil)))

(defn parse-src
  "{:repo :path :start :end} from NAME:PATH[#Lstart[-Lend]], else nil.
  :path is decoded, so it names the file as it is on disk."
  [s]
  (when-let [m (.exec src-re (or s ""))]
    (let [path (decoded (aget m 2))]
      (when (and path (not (.startsWith path "/")))
        {:repo (aget m 1) :path path
         :start (some-> (aget m 3) (js/parseInt 10))
         :end (some-> (aget m 4) (js/parseInt 10))}))))

(defn declared? [repo-name] (js/Object.hasOwn (:repos @state) repo-name))

(defn declared-ref
  "The parsed reference when it is well formed and names a declared repo, else nil."
  [s]
  (when-let [ref (parse-src s)]
    (when (declared? (:repo ref)) ref)))

;; ---- settings ------------------------------------------------------------------

(defn read-json [k]
  (try (some-> (.getItem js/localStorage k) js/JSON.parse) (catch :default _ nil)))

(defn write-json! [k v]
  (try (.setItem js/localStorage k (js/JSON.stringify v)) (catch :default _ nil)))

(defn remove-key! [k]
  (try (.removeItem js/localStorage k) (catch :default _ nil)))

(defn page-settings [] (or (read-json (page-key)) {}))

(defn shared-defaults [] (or (read-json shared-key) {}))

(defn identifiers [repo] {"name" (:name repo) "root" (:root repo) "remote" (:remote repo)})

(defn same-repo?
  "A shared-default record belongs to `repo` when at least one identifier is on
  both and every identifier on both agrees."
  [record repo]
  (let [ids (identifiers repo)
        both (filter #(and (some? (get record %)) (some? (get ids %))) ["name" "root" "remote"])]
    (and (seq both) (every? #(= (get record %) (get ids %)) both))))

(defn shared-record
  "The latest saved shared default for `repo`, or nil."
  [shared repo]
  (last (filter #(same-repo? % repo) (or (get shared "repos") []))))

(defn per-repo
  "{name (f repo)} over the declared repos."
  [f]
  (reduce (fn [m repo] (assoc m (:name repo) (f repo))) {} (js/Object.values (:repos @state))))

(defn valid-format [f] (when (some #(= f (first %)) formats) f))

(defn settings
  "The settings in effect, {:format :roots :webs}: this page's value, else the
  shared default, else the agent's meta (format: vscode)."
  []
  (let [page (page-settings)
        shared (shared-defaults)]
    {:format (or (valid-format (get page "format")) (valid-format (get shared "format")) "vscode")
     :roots (per-repo (fn [repo]
                        (without-trailing-slash
                         (or (absolute-path (get (get page "roots") (:name repo)))
                             (absolute-path (get (shared-record shared repo) "savedRoot"))
                             (:root repo)))))
     :webs (per-repo (fn [repo]
                       (or (valid-web (get (get page "webs") (:name repo)))
                           (valid-web (get (shared-record shared repo) "savedWeb"))
                           (:web repo))))}))

(defn update-page-settings!
  "Sets `path` (e.g. [\"roots\" \"api\"]) in this page's settings; a blank value removes it."
  [path value]
  (let [page (page-settings)
        v (present value)]
    (write-json! (page-key)
                 (if (= 1 (count path))
                   (if v (assoc page (first path) v) (dissoc page (first path)))
                   (let [[group k] path
                         m (or (get page group) {})]
                     (assoc page group (if v (assoc m k v) (dissoc m k))))))))

(defn overridden? [s repo] (not= (get (:roots s) (:name repo)) (:root repo)))

(defn sources
  "The decision value recorded on Done."
  []
  (let [s (settings)]
    {:format (:format s)
     :repos (per-repo (fn [repo] {:root (get (:roots s) (:name repo))
                                  :default (:root repo)
                                  :overridden (overridden? s repo)}))}))

(defn save-shared-defaults!
  "Saves the format and every repo on this page as the shared defaults,
  replacing the records that belong to the same repos."
  []
  (let [s (settings)
        page (page-settings)
        shared (shared-defaults)
        records (reduce (fn [records repo]
                          (let [prior (shared-record shared repo)
                                web (or (valid-web (get (get page "webs") (:name repo)))
                                        (get prior "savedWeb"))]
                            (conj (vec (remove #(same-repo? % repo) records))
                                  (cond-> {"name" (:name repo) "root" (:root repo)
                                           "savedRoot" (get (:roots s) (:name repo))}
                                    (:remote repo) (assoc "remote" (:remote repo))
                                    web (assoc "savedWeb" web)))))
                        (vec (or (get shared "repos") []))
                        (js/Object.values (:repos @state)))]
    (write-json! shared-key {"format" (:format s) "repos" records})))

;; ---- links -----------------------------------------------------------------------

(defn encode-path
  "Percent-encodes each segment of `p`, keeping slashes and a drive letter."
  [p]
  (.join (.map (.split p "/") (fn [seg] (if (.test #"^[A-Za-z]:$" seg) seg (js/encodeURIComponent seg)))) "/"))

(defn vscode-url [root {:keys [path start]}]
  (let [abs (str root "/" path)]
    (str "vscode://file" (when-not (.startsWith abs "/") "/") (encode-path abs)
         (when start (str ":" start ":1")))))

(defn github-url [web {:keys [path start end]}]
  (str web (encode-path path)
       (when start (str "?plain=1#L" start (when (and end (not= end start)) (str "-L" end))))))

(defn href
  "The URL for `ref` under settings `s`. GitHub falls back to VS Code for a repo
  without a GitHub URL."
  [s ref]
  (let [web (get (:webs s) (:repo ref))]
    (if (and (= "github" (:format s)) web)
      (github-url web ref)
      (vscode-url (get (:roots s) (:repo ref)) ref))))

(defn copy-text [s {:keys [repo path start]}]
  (str (get (:roots s) repo) "/" path (when start (str ":" start))))

(defn label [{:keys [repo path start end]}]
  (str repo "/" path (when start (str ":" start (when (and end (not= end start)) (str "-" end))))))

(defn refresh-hrefs!
  "Points every managed link at the current settings."
  []
  (let [s (settings)]
    (doseq [a (js/Array.from (js/document.querySelectorAll "a[data-src][data-src-linked]"))]
      (when-let [ref (declared-ref (.getAttribute a "data-src"))]
        (let [h (href s ref)]
          (.setAttribute a "href" h)
          (when (= svg-ns (.-namespaceURI a))
            (.setAttributeNS a xlink-ns "xlink:href" h)))))))

;; ---- settings bar ----------------------------------------------------------------

(defn el! [tag attrs & children]
  (let [e (js/document.createElement tag)]
    (doseq [[k v] (js/Object.entries attrs)] (.setAttribute e k v))
    (doseq [c children] (.append e c))
    e))

(defn format-name [f] (second (first (filter #(= f (first %)) formats))))

(defn refresh-bar!
  "Shows the settings in effect in the bar, without touching a field being typed in."
  []
  (when-let [bar (:bar @state)]
    (let [s (settings)
          page (page-settings)
          n (count (js/Object.keys (:repos @state)))]
      (set! (.-textContent (.querySelector bar "summary"))
            (str "Code links: " (format-name (:format s)) " · " n (if (= 1 n) " repo" " repos")))
      (set! (.-value (.querySelector bar "select")) (:format s))
      (doseq [row (js/Array.from (.querySelectorAll bar "fieldset[data-repo]"))]
        (let [nm (.. row -dataset -repo)
              root-in (.querySelector row "input[name=root]")
              web-in (.querySelector row "input[name=web]")
              typed (present (get (get page "webs") nm))
              web (get (:webs s) nm)]
          (when-not (= root-in js/document.activeElement)
            (set! (.-value root-in) (get (:roots s) nm)))
          (when-not (= web-in js/document.activeElement)
            (set! (.-value web-in) (or typed web "")))
          (set! (.-hidden (.querySelector row ".hc-cl-unavailable")) (some? web))
          (set! (.-hidden (.querySelector row ".hc-cl-hint")) (or (nil? typed) (some? (valid-web typed)))))))))

(defn settings-changed! []
  (refresh-bar!)
  (refresh-hrefs!))

(defn flash! [btn text]
  (let [was (.-textContent btn)]
    (set! (.-textContent btn) text)
    (js/setTimeout #(set! (.-textContent btn) was) 1500)))

(defn repo-row [repo]
  (let [nm (:name repo)
        root-in (el! "input" {"type" "text" "name" "root" "spellcheck" "false"
                              "placeholder" (:root repo) "aria-label" (str nm ": checkout root")})
        web-in (el! "input" {"type" "text" "name" "web" "spellcheck" "false"
                             "placeholder" "https://github.com/OWNER/REPO/blob/SHA/"
                             "aria-label" (str nm ": GitHub URL")})
        root-hint (el! "span" {"class" "hc-cl-root-hint" "hidden" ""})]
    (.addEventListener root-in "change"
                       (fn [e]
                         (let [v (present (.. e -target -value))]
                           (if (and v (not (absolute-path v)))
                             (do (set! (.-textContent root-hint)
                                       (str (js/JSON.stringify v) " isn't an absolute path, so it's ignored"))
                                 (set! (.-hidden root-hint) false))
                             (do (set! (.-hidden root-hint) true)
                                 (update-page-settings! ["roots" nm] v)))
                           (settings-changed!))))
    (.addEventListener web-in "change" (fn [e] (update-page-settings! ["webs" nm] (.. e -target -value)) (settings-changed!)))
    (el! "fieldset" {"data-repo" nm}
         (el! "legend" {} nm)
         (el! "label" {} "Checkout root" root-in)
         root-hint
         (el! "label" {} "GitHub URL" web-in)
         (el! "span" {"class" "hc-cl-unavailable"} "GitHub unavailable for this repo: enter its URL to link there")
         (el! "span" {"class" "hc-cl-hint"} "Not a GitHub blob URL, so it's ignored"))))

(defn bar! []
  (let [select (el! "select" {"aria-label" "Open code in"})
        save (el! "button" {"type" "button"} "Save as default")
        reset (el! "button" {"type" "button"} "Reset")
        repos (js/Object.values (:repos @state))
        bar (el! "details" {"id" "decide-code-links"}
                 (el! "summary" {} "Code links")
                 (el! "label" {} "Open code in " select)
                 (apply el! "div" {"class" "hc-cl-repos"} (map repo-row repos))
                 (el! "div" {"class" "hc-cl-actions"} save reset))]
    (doseq [[v text] formats] (.append select (el! "option" {"value" v} text)))
    (.addEventListener select "change" (fn [e] (update-page-settings! ["format"] (.. e -target -value)) (settings-changed!)))
    (.addEventListener save "click" (fn [_] (save-shared-defaults!) (flash! save "Saved")))
    (.addEventListener reset "click" (fn [_] (remove-key! (page-key)) (settings-changed!)))
    bar))

(defn ensure-bar!
  "Adds the bar on the first valid reference. It opens by itself when a root
  differs from the agent's."
  []
  (when-not (:bar @state)
    (let [bar (bar!)
          s (settings)]
      (swap! state assoc :bar bar)
      (when (some #(overridden? s %) (js/Object.values (:repos @state)))
        (set! (.-open bar) true))
      (.prepend js/document.body bar)
      (refresh-bar!))))

;; ---- references ----------------------------------------------------------------

(defn copy-button [src]
  (el! "button" {"type" "button" "class" "hc-src-copy" "data-src-copy" src "aria-label" "Copy path"} "Copy"))

(defn link-el [src]
  (el! "a" {"class" "hc-src-link" "data-src" src "data-src-linked" ""}))

(def last-decoration-id (atom 0))

(defn block-of
  "The <pre> that reference `el` marks as a block: `el` itself, or the <pre>
  around a <code> reference; nil for an inline reference."
  [el]
  (case (.-localName el)
    "pre" el
    "code" (let [p (.-parentElement el)] (when (and p (= "pre" (.-localName p))) p))
    nil))

(defn decorate!
  "Adds the link and copy button for a valid reference `el`. Every node added
  carries data-src-of, the id stored on `el` as data-src-id, for undecorate!.
  A managed <a> gets nothing: refresh-hrefs! sets its href."
  [el src ref]
  (let [id (str (swap! last-decoration-id inc))
        own (fn [n] (.setAttribute n "data-src-of" id) n)
        block (block-of el)
        page-link (some-> (.-parentElement el) (.closest "a"))]
    (.setAttribute el "data-src-id" id)
    (cond
      (= "a" (.-localName el)) nil
      block (let [a (link-el src)]
              (set! (.-textContent a) (label ref))
              (.before block (own (el! "div" {"class" "hc-src"} a (copy-button src)))))
      page-link (.after page-link (own (copy-button src)))
      :else (let [a (own (link-el src))]
              (.before el a)
              (.append a el)
              (.after a (own (copy-button src)))))))

(defn undecorate!
  "Removes what decorate! added for `el`, putting `el` back where it was."
  [el]
  (when-let [id (.getAttribute el "data-src-id")]
    (let [sel (str "[data-src-of=\"" id "\"]")
          wrapper (.-parentElement el)]
      (when (and wrapper (.matches wrapper sel))
        (.before wrapper el))
      (doseq [n (js/Array.from (js/document.querySelectorAll sel))] (.remove n))
      (.removeAttribute el "data-src-id"))))

(defn link!
  "Processes one reference element, once. True when it got a link."
  [el]
  (.setAttribute el "data-src-linked" "")
  (let [src (.getAttribute el "data-src")
        ref (parse-src src)
        report! (:report! @state)]
    (cond
      (nil? ref)
      (do (report! "code-link" (str "data-src " (js/JSON.stringify src) " is not NAME:PATH[#Lstart[-Lend]]") src) false)
      (not (declared? (:repo ref)))
      (do (report! "code-link" (str "data-src " (js/JSON.stringify src) " names repo " (:repo ref)
                                    ", which no <meta name=\"hc-repo\"> declares") src) false)
      :else
      (do (ensure-bar!) (decorate! el src ref) true))))

(defn relink!
  "Follows a change to a processed reference's data-src: removes its old link,
  then links it again while it still has a data-src. True when it got a link."
  [el]
  (undecorate! el)
  (when (= "a" (.-localName el))
    (.removeAttribute el "href")
    (.removeAttributeNS el xlink-ns "href"))
  (if (.hasAttribute el "data-src")
    (link! el)
    (do (.removeAttribute el "data-src-linked") false)))

(defn link-within!
  "Links every unprocessed reference at or under `root`. True when any got a link."
  [root]
  (let [sel "[data-src]:not([data-src-linked])"
        els (cond-> (js/Array.from (.querySelectorAll root sel))
              (.matches root sel) (.concat [root]))]
    (pos? (count (filter identity (mapv link! els))))))

(defn on-mutations [records]
  (when (some identity
              (mapv (fn [r]
                      (if (= "attributes" (.-type r))
                        (let [t (.-target r)]
                          (cond
                            (.hasAttribute t "data-src-linked") (relink! t)
                            (.hasAttribute t "data-src") (link! t)
                            :else false))
                        (some identity (mapv #(and (= 1 (.-nodeType %)) (link-within! %))
                                             (js/Array.from (.-addedNodes r))))))
                    records))
    (refresh-hrefs!)))

;; ---- events ----------------------------------------------------------------------

(defn select-text! [btn text]
  (let [next (.-nextElementSibling btn)
        span (if (and next (.contains (.-classList next) "hc-src-path"))
               next
               (let [s (el! "span" {"class" "hc-src-path"})] (.after btn s) s))
        range (js/document.createRange)
        sel (js/window.getSelection)]
    (set! (.-textContent span) text)
    (.selectNodeContents range span)
    (.removeAllRanges sel)
    (.addRange sel range)))

(defn ^:async copy! [btn]
  (let [text (copy-text (settings) (parse-src (.. btn -dataset -srcCopy)))]
    (try
      (js-await (.writeText js/navigator.clipboard text))
      (flash! btn "Copied")
      (catch :default _ (select-text! btn text)))))

(defn on-click [e]
  (when-let [btn (.closest (.-target e) "button.hc-src-copy")]
    (copy! btn)))

(defn on-done
  "Capture phase: records `sources` before core's bubble-phase handler records
  `status`, so the agent reads the settings the answers were given under."
  [e]
  (when (and (:bar @state) (.closest (.-target e) "[data-decision=status][data-choice=done]"))
    (.record js/window.htmlChannel "sources" (sources))))

(defn start!
  "Links the page's references now and as they appear. `report!` is (fn [kind message source]).
  Pre: decide.core has started, so window.htmlChannel.record saves answers."
  [report!]
  (reset! state {:repos (read-repos report!) :report! report! :bar nil})
  (when (link-within! js/document.body) (refresh-hrefs!))
  (.observe (js/MutationObserver. on-mutations) js/document.body
            {:childList true :subtree true :attributes true :attributeFilter ["data-src"]})
  (.addEventListener js/document "click" on-done true)
  (.addEventListener js/document "click" on-click))
