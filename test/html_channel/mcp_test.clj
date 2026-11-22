(ns html-channel.mcp-test
  "The second URL (`remote_url_base`, `remote_url`) that tools and the CLI add
  when $HTML_CHANNEL_REMOTE_BASE is set."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [html-channel.cli]
            [html-channel.mcp :as mcp]
            [html-channel.server :as server]
            [html-channel.test-support :refer [temp-dir with-state-dir write-page!]]))

(use-fixtures :each with-state-dir)

(defn- project-root []
  (let [root (str (fs/path (temp-dir) "proj" "ai" "html-channel"))]
    (write-page! root "s1" "p" "<p>v1</p>")
    root))

(defn- cli-json [f & args]
  (json/parse-string (with-out-str (apply f args)) true))

(deftest unset-adds-nothing
  (let [root (project-root)
        ctx {:root root :port 8788}]
    (is (nil? (server/remote-base)))
    (let [setup (mcp/channel-setup ctx {:session "s1"})]
      (is (re-matches #"http://127\.0\.0\.1:8788/[^/]+/s1/" (:url_base setup)))
      (is (not (contains? setup :remote_url_base))))
    (let [r (mcp/get-decisions ctx {:session "s1" :page "p"})]
      (is (re-matches #"http://127\.0\.0\.1:8788/[^/]+/s1/p\.html" (:url r)))
      (is (not (contains? r :remote_url))))
    (is (not (contains? (cli-json #'html-channel.cli/decisions "s1/p" {:root root :port 8788}) :remote_url)))))

(deftest set-adds-the-remote-url-next-to-the-local-one
  (with-redefs [server/remote-base (constantly "http://devbox:8788")]
    (let [root (project-root)
          ctx {:root root :port 8788}]
      (testing "channel_setup: url_base stays local, remote_url_base is added"
        (let [{:keys [url_base remote_url_base]} (mcp/channel-setup ctx {:session "s1"})
              path (subs url_base (count "http://127.0.0.1:8788"))]
          (is (re-matches #"http://127\.0\.0\.1:8788/[^/]+/s1/" url_base))
          (is (= (str "http://devbox:8788" path) remote_url_base))))
      (testing "get_decisions: url stays local, remote_url is added"
        (let [{:keys [url remote_url]} (mcp/get-decisions ctx {:session "s1" :page "p"})]
          (is (re-matches #"http://127\.0\.0\.1:8788/.+/s1/p\.html" url))
          (is (re-matches #"http://devbox:8788/.+/s1/p\.html" remote_url))))
      (testing "CLI decisions and session new carry it too"
        (is (re-matches #"http://devbox:8788/.+/s1/p\.html"
                        (:remote_url (cli-json #'html-channel.cli/decisions "s1/p" {:root root :port 8788}))))
        (is (re-matches #"http://devbox:8788/.+/"
                        (:remote_url_base (cli-json #'html-channel.cli/session-new {:root root :port 8788}))))))))
