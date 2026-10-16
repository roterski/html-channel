(ns html-channel.store
  "Pages, revision snapshots and decisions under one channel root.

    <root>/<session>/<page>.html        written by the agent
    <root>/<session>/<page>@<n>.html    snapshot of revision n
    <root>/<session>/decisions.jsonl    append-only {page session rev id value at}
    <root>/<session>/errors.jsonl       append-only {page session rev kind message source? check? at}

  Invariants:
  - decisions.jsonl and errors.jsonl are only ever appended to;
  - snapshot n+1 differs in content from snapshot n;
  - `value: null` means the answer was cleared.

  Every reader and writer goes through `current-rev!` first, so a rewritten
  page gets its new revision no matter which entry point notices it."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str])
  (:import [java.nio.file Files FileAlreadyExistsException]
           [java.security MessageDigest]))

(def default-root "ai/html-channel")

(defn root-from
  "The channel root: `opt`, else $HTML_CHANNEL_ROOT, else `default-root`.
  A relative root resolves against the project: $CLAUDE_PROJECT_DIR, else the cwd."
  ([opt] (root-from opt (System/getenv)))
  ([opt env]
   (let [project (or (not-empty (get env "CLAUDE_PROJECT_DIR")) (System/getProperty "user.dir"))]
     (str (fs/path project (or opt (not-empty (get env "HTML_CHANNEL_ROOT")) default-root))))))

(def name-re #"[a-z0-9][a-z0-9-]{0,63}")

(defn valid-name? [s] (and (string? s) (boolean (re-matches name-re s))))

(defn- check-name! [kind s]
  (when-not (valid-name? s)
    (throw (ex-info (str kind " must match " name-re ", got " (pr-str s)) {:status 400}))))

(defn session-dir [root session]
  (check-name! "session" session)
  (fs/path root session))

(defn page-file [root session page]
  (check-name! "page" page)
  (fs/path (session-dir root session) (str page ".html")))

(defn snapshot-file [root session page n]
  (fs/path (session-dir root session) (str page "@" n ".html")))

(defn decisions-file [root session]
  (fs/path (session-dir root session) "decisions.jsonl"))

(defn errors-file [root session]
  (fs/path (session-dir root session) "errors.jsonl"))

(defn- sha256 [f]
  (.digest (MessageDigest/getInstance "SHA-256") (Files/readAllBytes f)))

(defn latest-snapshot
  "Highest n with an existing `<page>@<n>.html`, or 0."
  [root session page]
  (let [dir (session-dir root session)
        re (re-pattern (str "\\Q" page "\\E@(\\d+)\\.html"))]
    (if-not (fs/directory? dir)
      0
      (transduce (keep #(some-> (re-matches re (str (fs/file-name %))) second parse-long))
                 max 0 (fs/list-dir dir)))))

(defn- link-snapshot!
  "Atomically publishes a copy of `page` as `target`: copy to a temp file, then
  hard-link it into place, which fails if `target` already exists."
  [page target]
  (let [tmp (fs/create-temp-file {:dir (fs/parent target) :prefix ".snapshot-"})]
    (try
      (fs/copy page tmp {:replace-existing true})
      (Files/createLink target tmp)
      true
      (catch FileAlreadyExistsException _ false)
      (finally (fs/delete-if-exists tmp)))))

(defn current-rev!
  "The page's current revision, snapshotting it first when its content differs
  from the latest snapshot. 0 when the page has never existed.
  Safe to call from several processes at once: one snapshot per change."
  [root session page]
  (let [f (page-file root session page)]
    (loop []
      (let [n (latest-snapshot root session page)]
        (cond
          (not (fs/regular-file? f)) n
          (and (pos? n) (MessageDigest/isEqual (sha256 f) (sha256 (snapshot-file root session page n)))) n
          (link-snapshot! f (snapshot-file root session page (inc n))) (inc n)
          :else (recur))))))

(defn- read-jsonl
  "Every parseable line of `f`, oldest first; [] when `f` doesn't exist."
  [f]
  (if (fs/exists? f)
    (keep #(try (json/parse-string % true) (catch Exception _ nil)) (fs/read-all-lines f))
    []))

(defn entries
  "Every entry in the session's decisions file, oldest first. Unparseable lines are skipped."
  [root session]
  (read-jsonl (decisions-file root session)))

(def per-revision-ids
  "Decision ids that count only at the revision they were given in, so they
  never carry over: the Done button and requests to go deeper."
  #{"status" "deeper"})

(defn effective
  "{:rev :done :deeper :decisions {id {:value :rev :at}}} for one page.
  Each id takes its latest entry across all revisions (answers carry over),
  except `per-revision-ids`, which count only when given at the current revision.
  :deeper lists every target requested at the current revision, in order."
  [root session page]
  (let [rev (current-rev! root session page)
        es (filter #(and (= page (:page %)) (string? (:id %))) (entries root session))
        latest (reduce (fn [m {:keys [id] :as e}]
                         (if (or (not (per-revision-ids id)) (= rev (:rev e)))
                           (assoc m id (select-keys e [:value :rev :at]))
                           m))
                       {} es)]
    {:rev rev
     :done (= "done" (get-in latest ["status" :value]))
     :deeper (->> es
                  (filter #(and (= "deeper" (:id %)) (= rev (:rev %)) (string? (:value %))))
                  (map :value) distinct vec)
     :decisions (dissoc latest "deeper")}))

(def ^:private write-lock (Object.))

(defn append-entry!
  "Appends one decision and returns it as stored.
  `session` comes from the caller (the URL), never from the entry.
  A missing `rev` means the current one; a rev from the future is a caller bug."
  [root session entry]
  (let [{:keys [page id rev]} entry
        _ (check-name! "page" page)
        _ (when-not (string? id) (throw (ex-info "entry needs a string id" {:status 400})))
        current (current-rev! root session page)
        rev (if (nil? rev) current rev)]
    (when-not (and (integer? rev) (<= rev current))
      (throw (ex-info (str "rev " (pr-str rev) " is not a known revision (current " current ")")
                      {:status 400})))
    (let [stored (-> (select-keys entry [:page :id :value :at])
                     (assoc :session session :rev rev)
                     (update :at #(or % (str (java.time.Instant/now)))))]
      (locking write-lock
        (fs/create-dirs (session-dir root session))
        (spit (fs/file (decisions-file root session)) (str (json/generate-string stored) "\n") :append true))
      stored)))

(def error-kinds #{"load" "js" "viz" "undeclared"})

(defn append-error!
  "Appends one page error, stamped with the page's current revision, and returns it as stored.
  `session` comes from the caller (the URL), never from the error."
  [root session error]
  (let [{:keys [page kind message]} error]
    (check-name! "page" page)
    (when-not (error-kinds kind)
      (throw (ex-info (str "error kind must be one of " (sort error-kinds) ", got " (pr-str kind)) {:status 400})))
    (when-not (string? message)
      (throw (ex-info "error needs a string message" {:status 400})))
    (let [stored (-> (select-keys error [:page :kind :message :source :check :at])
                     (assoc :session session :rev (current-rev! root session page))
                     (update :at #(or % (str (java.time.Instant/now)))))]
      (locking write-lock
        (fs/create-dirs (session-dir root session))
        (spit (fs/file (errors-file root session)) (str (json/generate-string stored) "\n") :append true))
      stored)))

(defn errors
  "The page's errors at revision `rev`, one per distinct {kind message source},
  with how often it was reported and when first. With `check`, only that check's."
  ([root session page rev] (errors root session page rev nil))
  ([root session page rev check]
   (->> (read-jsonl (errors-file root session))
        (filter #(and (= page (:page %)) (= rev (:rev %)) (or (nil? check) (= check (:check %)))))
        (group-by (juxt :kind :message :source))
        vals
        (map (fn [[e :as es]] (-> (select-keys e [:kind :message :source :at]) (assoc :count (count es)))))
        (sort-by :at)
        vec)))

(defn page-state
  "What the agent reads about a page: `effective` plus :errors at the current revision."
  [root session page]
  (let [eff (effective root session page)]
    (assoc eff :errors (errors root session page (:rev eff)))))

(defn pages
  "{session [page …]} for every page file under root, snapshots excluded."
  [root]
  (into (sorted-map)
        (for [d (when (fs/directory? root) (fs/list-dir root))
              :let [s (str (fs/file-name d))]
              :when (and (fs/directory? d) (valid-name? s))
              :let [ps (->> (fs/list-dir d)
                            (keep #(second (re-matches #"([a-z0-9][a-z0-9-]*)\.html" (str (fs/file-name %)))))
                            sort)]
              :when (seq ps)]
          [s (vec ps)])))
