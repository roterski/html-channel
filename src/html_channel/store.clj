(ns html-channel.store
  "Pages, revision snapshots and decisions under one channel root.

    <root>/<session>/<page>.html        written by the agent
    <root>/<session>/<page>@<n>.html    snapshot of revision n
    <root>/<session>/decisions.jsonl    append-only {page session rev id value at}

  Invariants:
  - decisions.jsonl is only ever appended to;
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

(defn entries
  "Every entry in the session's decisions file, oldest first. Unparseable lines are skipped."
  [root session]
  (let [f (decisions-file root session)]
    (if (fs/exists? f)
      (keep #(try (json/parse-string % true) (catch Exception _ nil)) (fs/read-all-lines f))
      [])))

(defn effective
  "{:rev :done :decisions {id {:value :rev :at}}} for one page.
  Each id takes its latest entry across all revisions (answers carry over),
  except `status`, which counts only when given at the current revision."
  [root session page]
  (let [rev (current-rev! root session page)
        latest (reduce (fn [m {:keys [id] :as e}]
                         (if (and (= page (:page e)) (string? id)
                                  (or (not= "status" id) (= rev (:rev e))))
                           (assoc m id (select-keys e [:value :rev :at]))
                           m))
                       {} (entries root session))]
    {:rev rev
     :done (= "done" (get-in latest ["status" :value]))
     :decisions latest}))

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
