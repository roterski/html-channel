(ns html-channel.answers-file-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is testing]]
            [html-channel.answers-file :as answers-file]
            [html-channel.cli]
            [html-channel.mcp :as mcp]
            [html-channel.store :as store]
            [html-channel.test-support :refer [temp-dir write-page!]]))

(def file-text
  (str ";; html-channel answers. Read with: html-channel decisions --file <this file>\n"
       "{:html-channel/answers 1\n"
       " :session \"s1\"\n :page \"p\"\n :rev 1\n :respondent :author\n :state \"connected\"\n"
       " :exported-at \"2026-11-24T10:00:00.000Z\"\n :done true\n"
       " :decisions {\"a b\" {\"value\" \"one\", \"rev\" 1, \"at\" \"t1\"}, "
       "\"q\\\"x\" {\"value\" \"l1\\nl2\", \"rev\" 1, \"at\" \"t2\"}, "
       "\"x/y\" {\"value\" true, \"rev\" 1, \"at\" \"t3\"}, "
       "\"3fa9\" {\"value\" nil, \"rev\" 1, \"at\" \"t4\"}}\n"
       " :sources {\"format\" \"vscode\", \"repos\" {}}\n"
       " :hunks nil\n"
       " :moot {\"evict\" \"in-process evicts on its own\"}}\n"))

(defn- answers-file! [dir text]
  (let [f (fs/path dir "p@1.edn")]
    (spit (fs/file f) text)
    (str f)))

(deftest reads-a-file-as-page-state
  (let [root (temp-dir)
        _ (write-page! root "s1" "p" "<p>v1</p>")
        _ (store/current-rev! root "s1" "p")
        st (answers-file/page-state root (answers-file! (temp-dir) file-text))]
    (testing "answers come back under their own ids, any characters included"
      (is (= "one" (get-in st [:decisions "a b" "value"])))
      (is (= "l1\nl2" (get-in st [:decisions "q\"x" "value"])))
      (is (= true (get-in st [:decisions "x/y" "value"])))
      (is (contains? (:decisions st) "3fa9")))
    (testing "reports become answers as Done records them; a nil one is left out"
      (is (= {:value {"format" "vscode" "repos" {}} :rev 1 :at "2026-11-24T10:00:00.000Z"}
             (get-in st [:decisions "sources"])))
      (is (= "in-process evicts on its own" (get-in st [:decisions "moot" :value "evict"])))
      (is (not (contains? (:decisions st) "hunks"))))
    (testing "header"
      (is (= [":author" "connected" true true [] "p" "s1" 1]
             ((juxt :respondent :state :done :checked :deeper :page :session :rev) st))))
    (testing "nothing is written"
      (is (not (fs/exists? (store/decisions-file root "s1")))))))

(deftest a-missing-snapshot-still-reads-unchecked
  (let [st (answers-file/page-state (temp-dir) (answers-file! (temp-dir) file-text))]
    (is (false? (:checked st)))
    (is (re-find #"no snapshot s1/p@1.html" (:reason st)))
    (is (= "one" (get-in st [:decisions "a b" "value"])))))

(deftest a-file-without-a-revision-reads-unchecked
  (let [st (answers-file/page-state (temp-dir)
                                    (answers-file! (temp-dir) (.replace file-text " :rev 1\n" " :rev nil\n")))]
    (is (false? (:checked st)))
    (is (re-find #"no revision" (:reason st)))))

(deftest other-files-are-refused
  (let [dir (temp-dir)]
    (is (thrown-with-msg? Exception #"isn't an html-channel answers file"
                          (answers-file/page-state dir (answers-file! dir "{:a 1}"))))
    (is (thrown-with-msg? Exception #"isn't EDN"
                          (answers-file/page-state dir (answers-file! dir "{:a"))))
    (is (thrown-with-msg? Exception #"no such file"
                          (answers-file/page-state dir (str (fs/path dir "nope.edn")))))))

(deftest get-decisions-and-the-cli-read-a-file
  (let [root (temp-dir)
        _ (write-page! root "s1" "p" "<p>v1</p>")
        _ (store/current-rev! root "s1" "p")
        f (answers-file! (temp-dir) file-text)]
    (testing "get_decisions with file needs no page or session"
      (let [r (mcp/get-decisions {:root root :port 8788} {:file f})]
        (is (true? (:checked r)))
        (is (= "one" (get-in r [:decisions "a b" "value"])))))
    (testing "get_decisions with neither page nor file is refused"
      (is (thrown-with-msg? Exception #"needs a page, or a file"
            (mcp/get-decisions {:root root :port 8788} {}))))
    (testing "decisions --file prints the same as JSON"
      (let [r (json/parse-string (with-out-str (#'html-channel.cli/decisions nil {:file f :root root})))]
        (is (= true (get r "checked")))
        (is (= "one" (get-in r ["decisions" "a b" "value"])))
        (is (= ":author" (get r "respondent")))))))

(deftest get-decisions-resolves-a-relative-file-against-the-project
  (let [project (temp-dir)
        root (str (fs/path project "ai" "html-channel"))]
    (write-page! root "s1" "p" "<p>v1</p>")
    (store/current-rev! root "s1" "p")
    (spit (fs/file project "answers.edn") file-text)
    (with-redefs [store/project-dir (constantly project)]
      (let [r (mcp/get-decisions {:root root :port 8788} {:file "answers.edn"})]
        (is (true? (:checked r)))
        (is (= (str (fs/canonicalize (fs/path project "answers.edn"))) (:file r)))))))
