(ns html-channel.store-test
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [html-channel.store :as store]
            [html-channel.test-support :refer [temp-dir write-page!]]))

(deftest root-from-prefers-opt-then-env-then-default
  (let [cwd (System/getProperty "user.dir")]
    (is (= (str (fs/path cwd "ai/html-channel")) (store/root-from nil {})))
    (is (= "/p/ai/html-channel" (store/root-from nil {"CLAUDE_PROJECT_DIR" "/p"})))
    (is (= "/p/x" (store/root-from nil {"CLAUDE_PROJECT_DIR" "/p" "HTML_CHANNEL_ROOT" "x"})))
    (is (= "/abs" (store/root-from nil {"CLAUDE_PROJECT_DIR" "/p" "HTML_CHANNEL_ROOT" "/abs"})))
    (is (= "/p/y" (store/root-from "y" {"CLAUDE_PROJECT_DIR" "/p" "HTML_CHANNEL_ROOT" "x"})))
    (is (= "/p/ai/html-channel" (store/root-from nil {"CLAUDE_PROJECT_DIR" "/p" "HTML_CHANNEL_ROOT" ""})))))

(deftest current-rev-snapshots-each-change-once
  (let [root (temp-dir)]
    (testing "no page yet"
      (is (= 0 (store/current-rev! root "s1" "p"))))
    (write-page! root "s1" "p" "<p>v1</p>")
    (testing "first call creates @1"
      (is (= 1 (store/current-rev! root "s1" "p")))
      (is (fs/exists? (store/snapshot-file root "s1" "p" 1))))
    (testing "unchanged file keeps its rev"
      (is (every? #{1} (repeatedly 100 #(store/current-rev! root "s1" "p")))))
    (testing "one rewrite gives exactly rev 2"
      (write-page! root "s1" "p" "<p>v2</p>")
      (is (= [2 2] [(store/current-rev! root "s1" "p") (store/current-rev! root "s1" "p")]))
      (is (= "<p>v2</p>" (slurp (fs/file (store/snapshot-file root "s1" "p" 2))))))))

(deftest concurrent-snapshots-make-one-revision
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "<p>v1</p>")
    (store/current-rev! root "s1" "p")
    (write-page! root "s1" "p" "<p>v2</p>")
    (let [other (p/process ["bb" "-cp" "src:resources" "-e"
                            (str "(require '[html-channel.store :as s])"
                                 "(prn (set (pmap (fn [_] (s/current-rev! " (pr-str root) " \"s1\" \"p\")) (range 50))))")]
                           {:out :string :err :inherit})
          here (set (pmap (fn [_] (store/current-rev! root "s1" "p")) (range 50)))]
      (is (= #{2} here))
      (is (= "#{2}" (str/trim (:out @other))))
      (is (= 2 (store/latest-snapshot root "s1" "p")))
      (is (not (fs/exists? (store/snapshot-file root "s1" "p" 3)))))))

(deftest effective-carries-answers-but-not-done
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (store/append-entry! root "s1" {:page "p" :id "eviction" :value "ttl"})
    (store/append-entry! root "s1" {:page "p" :id "notes" :value "keep"})
    (store/append-entry! root "s1" {:page "p" :id "status" :value "done"})
    (store/append-entry! root "s1" {:page "other" :id "eviction" :value "x"})
    (is (:done (store/effective root "s1" "p")))
    (write-page! root "s1" "p" "v2")
    (store/append-entry! root "s1" {:page "p" :id "notes" :value nil})
    (let [{:keys [rev done decisions]} (store/effective root "s1" "p")]
      (is (= 2 rev))
      (testing "a rev-1 done doesn't count at rev 2"
        (is (false? done))
        (is (not (contains? decisions "status"))))
      (testing "a rev-1 answer carries over with its rev"
        (is (= {:value "ttl" :rev 1} (select-keys (get decisions "eviction") [:value :rev]))))
      (testing "null at rev 2 overrides the rev-1 value"
        (is (= {:value nil :rev 2} (select-keys (get decisions "notes") [:value :rev])))))
    (store/append-entry! root "s1" {:page "p" :id "status" :value "done"})
    (is (:done (store/effective root "s1" "p")))))

(deftest append-entry-rules
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (testing "session comes from the caller, missing rev means current"
      (is (= {:session "s1" :rev 1}
             (select-keys (store/append-entry! root "s1" {:page "p" :id "a" :value 1 :session "evil"})
                          [:session :rev]))))
    (testing "a rev from the future is rejected"
      (is (= 400 (:status (ex-data (try (store/append-entry! root "s1" {:page "p" :id "a" :rev 2})
                                        (catch clojure.lang.ExceptionInfo e e)))))))
    (testing "an old rev is kept"
      (write-page! root "s1" "p" "v2")
      (is (= 1 (:rev (store/append-entry! root "s1" {:page "p" :id "a" :value 2 :rev 1})))))
    (testing "names that could escape the session dir are rejected"
      (doseq [bad ["../x" "A" "" "a/b" nil]]
        (is (thrown? clojure.lang.ExceptionInfo (store/append-entry! root "s1" {:page bad :id "a"})))
        (is (thrown? clojure.lang.ExceptionInfo (store/session-dir root bad)))))
    (testing "lines are only appended"
      (let [before (fs/read-all-lines (store/decisions-file root "s1"))]
        (store/append-entry! root "s1" {:page "p" :id "b" :value 3})
        (is (= before (butlast (fs/read-all-lines (store/decisions-file root "s1")))))))
    (testing "two sessions write two files"
      (write-page! root "s2" "p" "v1")
      (store/append-entry! root "s2" {:page "p" :id "a" :value 1})
      (is (= 1 (count (store/entries root "s2"))))
      (is (not= (store/decisions-file root "s1") (store/decisions-file root "s2"))))))

(deftest pages-lists-pages-without-snapshots
  (let [root (temp-dir)]
    (write-page! root "s1" "a" "x")
    (write-page! root "s1" "b" "x")
    (store/current-rev! root "s1" "a")
    (is (= {"s1" ["a" "b"]} (store/pages root)))))
