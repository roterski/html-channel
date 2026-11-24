(ns html-channel.answers-file
  "Reads an answers file, the .edn a page's Download gives (CONVENTION, Answering
  without the server), as the page state `decisions` prints for a served page.
  Nothing is written: each file is one respondent's answers, kept apart."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [html-channel.store :as store]))

(def report-ids
  "Top-level fields of the file that `decisions` shows as answers, as Done records them."
  ["sources" "hunks" "moot"])

(defn read-file
  "The file's map. Throws when it isn't an answers file."
  [path]
  (when-not (fs/regular-file? path)
    (throw (ex-info (str "no such file: " path) {})))
  (let [m (try (edn/read-string (slurp (fs/file path)))
               (catch Exception e
                 (throw (ex-info (str path " isn't EDN: " (ex-message e)) {}))))]
    (when-not (and (map? m) (= 1 (:html-channel/answers m)))
      (throw (ex-info (str path " isn't an html-channel answers file (no :html-channel/answers 1)") {})))
    m))

(defn- snapshot-check
  "{:checked true} when the page snapshot the answers were given at exists under
  `root`, else {:checked false :reason …}."
  [root {:keys [session page rev]}]
  (cond
    (not (and (store/valid-name? session) (store/valid-name? page)))
    {:checked false :reason "the file names no valid session and page"}

    (not (integer? rev))
    {:checked false :reason "the file has no revision: Download ran before the page first loaded its answers"}

    (not (fs/exists? (store/snapshot-file root session page rev)))
    {:checked false :reason (str "no snapshot " session "/" page "@" rev ".html under " root)}

    :else {:checked true}))

(defn page-state
  "The JSON `decisions` prints for a served page ({page rev done deeper decisions
  errors}), plus session, respondent, state, exported_at, file and the snapshot
  check. :respondent :author prints as \":author\"."
  [root path]
  (let [{:keys [session page rev respondent state exported-at decisions] :as m} (read-file path)
        reported (into {} (for [id report-ids
                                :let [v (get m (keyword id))]
                                :when (some? v)]
                            [id {:value v :rev rev :at exported-at}]))]
    (merge {:page page
            :session session
            :rev rev
            :done true
            :deeper []
            :decisions (merge decisions reported)
            :errors []
            :respondent (if (keyword? respondent) (str respondent) respondent)
            :state state
            :exported_at exported-at
            :file (str (fs/canonicalize path))}
           (snapshot-check root m))))
