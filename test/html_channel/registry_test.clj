(ns html-channel.registry-test
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [html-channel.registry :as registry]
            [html-channel.test-support :refer [temp-dir with-state-dir]]))

(use-fixtures :each with-state-dir)

(defn- registry-lines [] (fs/read-all-lines (registry/registry-file)))

(deftest mount-is-stable-across-processes
  (let [root (str (fs/path (temp-dir) "proj" "ai" "html-channel"))
        here (registry/register! root)
        there (-> (p/shell {:out :string :extra-env {"HTML_CHANNEL_HOME" (temp-dir)}}
                           "bb" "-cp" "src:resources" "-e"
                           (str "(require '[html-channel.registry :as r]) (print (r/register! " (pr-str root) "))"))
                  :out str/trim)]
    (is (re-matches #"proj-[0-9a-f]{6}" here))
    (is (= here there))))

(deftest project-name-strips-the-default-layout
  (is (= "proj" (registry/project-name "/x/proj/ai/html-channel")))
  (is (= "proj" (registry/project-name "/x/proj/ai/html-channel/")))
  (is (= "decisions" (registry/project-name "/x/proj/docs/decisions")))
  (is (= "html-channel" (registry/project-name "/x/proj/xai/html-channel")))
  (is (= ".ai" (registry/project-name "/x/proj/.ai"))))

(deftest registration-is-idempotent
  (let [a (str (fs/path (temp-dir) "a" "ai" "html-channel"))
        b (str (fs/path (temp-dir) "b" "ai" "html-channel"))]
    (dotimes [_ 3] (registry/register! a))
    (is (= 1 (count (registry-lines))))
    (registry/register! b)
    (is (= 2 (count (registry-lines))))
    (is (= #{(registry/register! a) (registry/register! b)} (set (keys (registry/roots)))))))

(deftest deleted-root-is-skipped
  (let [a (str (fs/path (temp-dir) "a" "ai" "html-channel"))
        b (str (fs/path (temp-dir) "b" "ai" "html-channel"))
        ma (registry/register! a)
        mb (registry/register! b)]
    (fs/delete-tree a)
    (is (nil? (registry/root-for ma)))
    (is (some? (registry/root-for mb)))
    (testing "a fresh read drops it entirely"
      (registry/register! (str (fs/path (temp-dir) "c" "ai" "html-channel")))
      (is (not (contains? (registry/roots) ma))))))
