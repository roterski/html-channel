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
  (let [root (str (fs/path (temp-dir) "proj" ".ai"))
        here (registry/register! root)
        there (-> (p/shell {:out :string :extra-env {"HTML_CHANNEL_HOME" (temp-dir)}}
                           "bb" "-cp" "src:resources" "-e"
                           (str "(require '[html-channel.registry :as r]) (print (r/register! " (pr-str root) "))"))
                  :out str/trim)]
    (is (re-matches #"proj-[0-9a-f]{6}" here))
    (is (= here there))))

(deftest registration-is-idempotent
  (let [a (str (fs/path (temp-dir) "a" ".ai"))
        b (str (fs/path (temp-dir) "b" ".ai"))]
    (dotimes [_ 3] (registry/register! a))
    (is (= 1 (count (registry-lines))))
    (registry/register! b)
    (is (= 2 (count (registry-lines))))
    (is (= #{(registry/register! a) (registry/register! b)} (set (keys (registry/roots)))))))

(deftest deleted-root-is-skipped
  (let [a (str (fs/path (temp-dir) "a" ".ai"))
        b (str (fs/path (temp-dir) "b" ".ai"))
        ma (registry/register! a)
        mb (registry/register! b)]
    (fs/delete-tree a)
    (is (nil? (registry/root-for ma)))
    (is (some? (registry/root-for mb)))
    (testing "a fresh read drops it entirely"
      (registry/register! (str (fs/path (temp-dir) "c" ".ai")))
      (is (not (contains? (registry/roots) ma))))))
