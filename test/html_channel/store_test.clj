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

(deftest deeper-counts-only-at-its-revision
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (store/append-entry! root "s1" {:page "p" :id "deeper" :value "cost"})
    (store/append-entry! root "s1" {:page "p" :id "deeper" :value "risk"})
    (store/append-entry! root "s1" {:page "p" :id "deeper" :value "cost"})
    (store/append-entry! root "s1" {:page "p" :id "a" :value 1})
    (testing "every target requested at the current revision, once, in order; not among decisions"
      (let [eff (store/effective root "s1" "p")]
        (is (= ["cost" "risk"] (:deeper eff)))
        (is (= #{"a"} (set (keys (:decisions eff)))))))
    (testing "a new revision starts with no requests, while answers carry over"
      (write-page! root "s1" "p" "v2")
      (let [eff (store/effective root "s1" "p")]
        (is (= [] (:deeper eff)))
        (is (= 1 (get-in eff [:decisions "a" :value])))))))

(deftest errors-survive-hostile-input
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (testing "the server's clock stamps errors; a client's at or a non-string source is dropped"
      (let [e (store/append-error! root "s1" {:page "p" :kind "js" :message "a" :at 5 :source {:x 1}})]
        (is (string? (:at e)))
        (is (not (contains? e :source)))))
    (testing "lines already on disk that lack string kind, message or at are skipped"
      (spit (fs/file (store/errors-file root "s1"))
            (str "{\"page\":\"p\",\"rev\":1,\"kind\":\"js\",\"message\":\"b\",\"at\":5}\n"
                 "{\"page\":\"p\",\"rev\":1,\"kind\":\"js\",\"message\":\"c\",\"at\":{\"x\":1}}\n"
                 "7\n")
            :append true)
      (is (= ["a"] (map :message (store/errors root "s1" "p" 1))))
      (is (= ["a"] (map :message (:errors (store/page-state root "s1" "p"))))))))

(deftest deeper-requests-resume-where-the-last-read-ended
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (store/append-entry! root "s1" {:page "p" :id "deeper" :value "a"})
    (store/append-entry! root "s1" {:page "q" :id "x" :value 1})
    (let [{:keys [targets upto]} (store/deeper-requests root "s1" "p" 1 0)]
      (is (= ["a"] targets))
      (is (= 2 upto))
      (store/append-entry! root "s1" {:page "p" :id "deeper" :value "b"})
      (store/append-entry! root "s1" {:page "p" :id "deeper" :value "a"})
      (is (= {:targets ["b" "a"] :upto 4} (store/deeper-requests root "s1" "p" 1 upto)))
      (is (= {:targets [] :upto 4} (store/deeper-requests root "s1" "p" 1 4)))
      (is (= {:targets [] :upto 4} (store/deeper-requests root "s1" "p" 1 99))))))

(deftest check-runs-are-per-page-and-check
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (is (false? (store/check-ran? root "s1" "p" "c1")))
    (store/append-check-run! root "s1" {:page "p" :check "c1"})
    (is (true? (store/check-ran? root "s1" "p" "c1")))
    (is (false? (store/check-ran? root "s1" "p" "c2")))
    (is (false? (store/check-ran? root "s1" "q" "c1")))
    (testing "a check id that could escape .check/ is rejected"
      (doseq [bad [nil "" "../x" "a/b" "C1"]]
        (is (thrown? clojure.lang.ExceptionInfo (store/append-check-run! root "s1" {:page "p" :check bad}))
            (pr-str bad)))
      ;; for an error, no check id means a reader's page load
      (doseq [bad ["" "../x" "a/b" "C1"]]
        (is (thrown? clojure.lang.ExceptionInfo
                     (store/append-error! root "s1" {:page "p" :kind "js" :message "m" :check bad}))
            (pr-str bad))))
    (is (thrown? clojure.lang.ExceptionInfo (store/append-check-run! root "s1" {:page "../x" :check "c"})))))

(deftest errors-are-stamped-and-stored-once
  (let [root (temp-dir)
        e {:page "p" :kind "load" :message "failed to load <script> x" :source "x"}
        lines #(count (fs/read-all-lines (store/errors-file root "s1")))]
    (write-page! root "s1" "p" "v1")
    (testing "the server stamps the current revision and the caller's session"
      (is (= {:rev 1 :session "s1"} (select-keys (store/append-error! root "s1" (assoc e :rev 9 :session "evil"))
                                                 [:rev :session]))))
    (testing "a reader's reload adds nothing: one line per distinct error and revision"
      (dotimes [_ 5] (store/append-error! root "s1" e))
      (store/append-error! root "s1" {:page "p" :kind "js" :message "boom"})
      (is (= 2 (lines)))
      (is (= ["load" "js"] (map :kind (store/errors root "s1" "p" 1)))))
    (testing "a check's errors stay out of errors.jsonl, readable by its id until discarded"
      (store/append-error! root "s1" (assoc e :check "c1"))
      (store/append-error! root "s1" {:page "p" :kind "js" :message "only in the check" :check "c1"})
      (store/append-check-run! root "s1" {:page "p" :check "c1"})
      (is (= 2 (lines)))
      (is (= ["load" "js"] (map :kind (store/errors root "s1" "p" 1 "c1"))))
      (is (= ["failed to load <script> x" "boom"] (map :message (store/errors root "s1" "p" 1))))
      (store/discard-check! root "s1" "c1")
      (is (= [] (store/errors root "s1" "p" 1 "c1")))
      (is (false? (store/check-ran? root "s1" "p" "c1")))
      (is (empty? (filter #(re-find #"c1" (str (fs/file-name %))) (fs/list-dir (store/check-dir root "s1"))))))
    (testing "a new revision stores the same error again, and page-state shows only the current one's"
      (write-page! root "s1" "p" "v2")
      (is (= [] (:errors (store/page-state root "s1" "p"))))
      (store/append-error! root "s1" e)
      (is (= 3 (lines)))
      (is (= ["load"] (map :kind (:errors (store/page-state root "s1" "p"))))))
    (testing "unknown kinds and missing messages are rejected"
      (is (thrown? clojure.lang.ExceptionInfo (store/append-error! root "s1" (assoc e :kind "other"))))
      (is (thrown? clojure.lang.ExceptionInfo (store/append-error! root "s1" (dissoc e :message)))))))
