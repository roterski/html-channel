(ns html-channel.wait-test
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is testing]]
            [html-channel.store :as store]
            [html-channel.test-support :refer [temp-dir write-page!]]
            [html-channel.wait :as wait]))

(def ^:private fast {:poll-ms 20})

(defn- await-async [root page & [opts]]
  (let [started (promise)
        result (future (deliver started true) (wait/await-reader root "s1" page (merge fast opts)))]
    @started
    (Thread/sleep 100)                  ; let it read R and the entry count first
    result))

(defn- settled [fut] (deref fut 3000 ::timeout))

(deftest done-before-or-during-the-wait
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (testing "Done already given at R returns at once"
      (store/append-entry! root "s1" {:page "p" :id "pick" :value "a"})
      (store/append-entry! root "s1" {:page "p" :id "status" :value "done"})
      (let [r (wait/await-reader root "s1" "p" fast)]
        (is (= ["done" 1 "a"] [(:status r) (:rev r) (get-in r [:decisions "pick" :value])]))))
    (testing "Done given during the wait"
      (write-page! root "s1" "q" "v1")
      (let [fut (await-async root "q")]
        (is (not (realized? fut)))
        (store/append-entry! root "s1" {:page "q" :id "status" :value "done"})
        (is (= "done" (:status (settled fut))))))))

(deftest deeper-returns-only-targets-requested-during-the-wait
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (store/append-entry! root "s1" {:page "p" :id "deeper" :value "old"})
    (let [fut (await-async root "p")]
      (is (not (realized? fut)) "a Deeper from before the wait doesn't end it")
      (store/append-entry! root "s1" {:page "p" :id "deeper" :value "new"})
      (let [r (settled fut)]
        (is (= "deeper" (:status r)))
        (is (= ["new"] (:deeper r)))))))

(deftest a-new-revision-supersedes-the-wait
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (let [fut (await-async root "p")]
      (write-page! root "s1" "p" "v2")
      (let [r (settled fut)]
        (is (= ["superseded" 2 false] [(:status r) (:rev r) (:done r)]))))
    (testing "Done on the old revision after a rewrite never yields done"
      (write-page! root "s1" "p" "v3")
      (store/current-rev! root "s1" "p")
      (let [fut (await-async root "p" {:max-wait-s 1})]
        (store/append-entry! root "s1" {:page "p" :id "status" :value "done" :rev 2})
        (is (= "waiting" (:status (settled fut))))))))

(deftest max-wait-is-opt-in
  (let [root (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (testing "with max-wait-s it returns waiting"
      (is (= "waiting" (:status (wait/await-reader root "s1" "p" (assoc fast :max-wait-s 0))))))
    (testing "without it, it keeps waiting"
      (let [fut (await-async root "p")]
        (is (= ::timeout (deref fut 500 ::timeout)))
        (future-cancel fut)))))

(deftest a-missing-page-is-a-caller-error
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no page at"
                        (wait/await-reader (temp-dir) "s1" "nope" fast))))

(defn- cli [root home & args]
  (apply p/shell {:out :string :err :string :continue true
                  :extra-env {"HTML_CHANNEL_HOME" home}}
         "bb" "-cp" "src:resources" "-m" "html-channel.cli" (concat args ["--root" root])))

(deftest cli-prints-the-state-with-status
  (let [root (temp-dir) home (temp-dir)]
    (write-page! root "s1" "p" "v1")
    (store/append-entry! root "s1" {:page "p" :id "status" :value "done"})
    (let [{:keys [exit out]} (cli root home "wait" "s1/p")
          r (json/parse-string out true)]
      (is (= 0 exit))
      (is (= ["p" "done" true] [(:page r) (:status r) (:done r)]))
      (is (re-find #"/s1/p\.html$" (:url r))))
    (testing "an unknown page exits 1"
      (let [{:keys [exit err]} (cli root home "wait" "s1/nope")]
        (is (= 1 exit))
        (is (re-find #"no page at" err))))
    (fs/delete-tree home)))
