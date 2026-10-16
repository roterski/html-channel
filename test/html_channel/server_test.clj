(ns html-channel.server-test
  (:require [babashka.fs :as fs]
            [babashka.http-client :as hc]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [html-channel.registry :as registry]
            [html-channel.server :as server]
            [html-channel.store :as store]
            [html-channel.test-support :refer [temp-dir with-state-dir write-page!]]))

(use-fixtures :each with-state-dir)

(defn- req
  ([method uri] (req method uri nil nil))
  ([method uri query body]
   (let [r (server/handler {:request-method method :uri uri :query-string query
                            :body (some-> body java.io.StringReader.)})]
     (cond-> r (instance? java.io.File (:body r)) (update :body slurp)))))

(defn- body-json [r] (json/parse-string (:body r) true))

(defn- setup []
  (let [root (str (fs/path (temp-dir) "proj" "ai" "html-channel"))
        mount (registry/register! root)]
    (write-page! root "s1" "p" "<p>v1</p>")
    {:root root :base (str "/" mount "/s1/")}))

(deftest routes
  (let [{:keys [root base]} (setup)]
    (is (= 204 (:status (req :options "/_health"))))
    (is (= true (:ok (body-json (req :get "/_health")))))
    (let [r (req :get "/_kit/decide.mjs")]
      (is (= 200 (:status r)))
      (is (= "text/javascript" (get-in r [:headers "Content-Type"]))))
    (is (= 200 (:status (req :get "/_kit/decide/core.mjs"))))
    (is (= 200 (:status (req :get "/_kit/errors.js"))))
    (is (= 200 (:status (req :get "/_kit/viz/mermaid.mjs"))))
    (is (re-find #"/s1/p\.html" (:body (req :get "/"))))
    (testing "POST _errors stores a page error at the current rev"
      (is (= 200 (:status (req :post (str base "_errors") nil
                               "{\"page\":\"p\",\"kind\":\"js\",\"message\":\"boom\"}"))))
      (is (= 400 (:status (req :post (str base "_errors") nil
                               "{\"page\":\"p\",\"kind\":\"nope\",\"message\":\"boom\"}"))))
      (is (= [["js" "boom"]] (map (juxt :kind :message) (store/errors root "s1" "p" 1)))))
    (testing "the page GET checks the rev, so it snapshots"
      (let [r (req :get (str base "p.html"))]
        (is (= 200 (:status r)))
        (is (= "<p>v1</p>" (:body r)))
        (is (fs/exists? (fs/path root "s1" "p@1.html")))))
    (is (= {:rev 1} (body-json (req :get (str base "_rev") "page=p" nil))))
    (is (= 200 (:status (req :post (str base "_decisions") nil "{\"page\":\"p\",\"id\":\"a\",\"value\":\"x\"}"))))
    (is (= {:rev 1 :done false :deeper [] :decisions {:a {:value "x" :rev 1}}}
           (update-in (body-json (req :get (str base "_decisions") "page=p" nil)) [:decisions :a] dissoc :at)))
    (is (= 404 (:status (req :get "/nope-000000/s1/p.html"))))
    (is (= 404 (:status (req :get (str base "missing.html")))))
    (is (= 400 (:status (req :get (str base "_rev") "page=Bad" nil))))))

(deftest post-rules
  (let [{:keys [root base]} (setup)
        post #(req :post (str base "_decisions") nil (json/generate-string %))]
    (testing "session from the path"
      (is (= 200 (:status (post {:page "p" :id "a" :value 1 :session "evil"}))))
      (is (= "s1" (:session (json/parse-string (last (fs/read-all-lines (fs/path root "s1" "decisions.jsonl"))) true)))))
    (testing "missing rev means current"
      (is (= 1 (:rev (body-json (post {:page "p" :id "a" :value 2}))))))
    (testing "future rev is a 400"
      (is (= 400 (:status (post {:page "p" :id "a" :value 3 :rev 5})))))))

(deftest traversal-is-404
  (let [{:keys [base]} (setup)]
    (doseq [uri ["/_kit/../html_channel/server.clj" "/_kit/%2e%2e/html_channel/server.clj"
                 (str base "../../../bb.edn") (str base "%2e%2e/%2e%2e/%2e%2e/bb.edn")]]
      (is (= 404 (:status (req :get uri))) uri))))

(defn- http-kit-stop [s] (org.httpkit.server/server-stop! s))

(deftest mount-registered-after-start-is-served
  (let [port 18731
        stop (server/try-bind port)]
    (try
      (let [{:keys [base]} (setup)]
        (is (= 200 (:status (hc/get (str "http://127.0.0.1:" port base "p.html") {:throw false})))))
      (testing "a second bind on the same port fails, so only one owner"
        (is (nil? (server/try-bind port))))
      (finally (http-kit-stop stop)))))

(defn- open-descriptor-count
  "Descriptors this process holds: /proc on Linux, lsof elsewhere."
  []
  (if (fs/exists? "/proc/self/fd")
    (count (fs/list-dir "/proc/self/fd"))
    (->> (p/shell {:out :string} "lsof" "-p" (str (.pid (java.lang.ProcessHandle/current))))
         :out str/split-lines count)))

(deftest failed-binds-hold-no-descriptors
  (let [port 18733
        owner (server/try-bind port)]
    (try
      (server/try-bind port)
      (let [before (open-descriptor-count)]
        (dotimes [_ 200] (server/try-bind port))
        (is (<= (- (open-descriptor-count) before) 5)
            "standby retries the bind every second, so any per-attempt leak exhausts descriptors"))
      (finally (http-kit-stop owner)))))

(deftest bb-version-gate
  (is (= [false false true true true]
         (map server/bb-version-ok? ["1.12.217" "1.13.219" "1.13.220" "1.13.220-SNAPSHOT" "2.0.0"])))
  (testing "missing or unparseable counts as old"
    (is (= [false false false] (map server/bb-version-ok? [nil "" "1.13"])))))

(deftest health-is-fast-with-1000-roots
  (let [d (temp-dir)]
    (dotimes [i 1000] (registry/register! (str (fs/path d (str "p" i) "ai" "html-channel"))))
    (let [port 18732
          s (server/try-bind port)]
      (try
        (dotimes [_ 20] (hc/get (str "http://127.0.0.1:" port "/_health")))
        (let [ms (sort (for [_ (range 101)]
                         (let [t (System/nanoTime)]
                           (hc/get (str "http://127.0.0.1:" port "/_health"))
                           (/ (- (System/nanoTime) t) 1e6))))]
          (is (< (nth ms 50) 5.0) (str "p50 " (nth ms 50) " ms")))
        (finally (http-kit-stop s))))))
