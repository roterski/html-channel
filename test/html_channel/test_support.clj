(ns html-channel.test-support
  (:require [babashka.fs :as fs]
            [html-channel.registry :as registry]))

(defn temp-dir [] (str (fs/create-temp-dir {:prefix "html-channel-test-"})))

(defn with-state-dir
  "Runs f with the registry pointed at a fresh directory."
  [f]
  (let [d (temp-dir)]
    (with-redefs [registry/state-dir (constantly d)]
      (try (f) (finally (fs/delete-tree d))))))

(defn write-page! [root session page html]
  (let [f (fs/path root session (str page ".html"))]
    (fs/create-dirs (fs/parent f))
    (spit (fs/file f) html)
    f))
