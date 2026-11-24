(ns html-channel.export-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [html-channel.export :as export]
            [html-channel.mcp :as mcp]
            [html-channel.store :as store]
            [html-channel.test-support :refer [temp-dir write-page!]])
  (:import [java.util Base64]))

(def page-html
  (str "<!doctype html><html><head><meta charset=\"utf-8\">\n"
       "<link rel=\"stylesheet\" href=\"/_kit/decide.css\">\n"
       "<script src=\"/_kit/boot.js\"></script>\n"
       "<script type=\"module\" src=\"/_kit/decide.mjs\"></script>\n"
       "</head><body><button data-decision=\"a\" data-choice=\"x\">X</button>\n"
       "<button data-decision=\"status\" data-choice=\"done\">Done</button></body></html>"))

(defn- import-map [html]
  (-> (re-find #"(?s)<script type=\"importmap\">(.*?)</script>" html) second (json/parse-string) (get "imports")))

(defn- decoded [data-url]
  (String. (.decode (Base64/getDecoder) ^String (second (str/split data-url #"," 2))) "UTF-8"))

(deftest rewrite-imports-makes-relative-specifiers-bare
  (is (= "import * as c from 'hc/squint-core.js';"
         (export/rewrite-imports "decide/core.mjs" "import * as c from '../squint-core.js';")))
  (is (= "import * as m from \"hc/decide/moot.mjs\";"
         (export/rewrite-imports "decide.mjs" "import * as m from \"./decide/moot.mjs\";")))
  (is (= "(await import(\"hc/viz/mermaid.mjs\"))"
         (export/rewrite-imports "decide/viz.mjs" "(await import(\"../viz/mermaid.mjs\"))")))
  (testing "absolute and bare specifiers stay"
    (is (= "import(versions.shiki); import x from 'https://e/x.js';"
           (export/rewrite-imports "decide/highlight.mjs" "import(versions.shiki); import x from 'https://e/x.js';")))))

(deftest export-writes-a-self-contained-copy
  (let [root (temp-dir)]
    (write-page! root "s1" "p" page-html)
    (let [{:keys [path rev bytes]} (export/export! root "s1" "p" nil)
          html (slurp path)
          imports (import-map html)]
      (testing "it stamps the revision, snapshotting the page as serving would"
        (is (= 1 rev))
        (is (fs/exists? (store/snapshot-file root "s1" "p" 1)))
        (is (= (str (fs/canonicalize (fs/path root "s1" "p@1.offline.html"))) path))
        (is (= bytes (count (.getBytes ^String html "UTF-8")))))
      (testing "the page says what it is, before boot.js reads it"
        (is (str/includes? html "<meta name=\"hc-offline\" content=\"session=s1; page=p; rev=1; mode=share\">"))
        (is (< (str/index-of html "name=\"hc-offline\"") (str/index-of html "<script>"))))
      (testing "no /_kit tag is left; the kit is inline"
        (is (not (re-find #"(src|href)=\"/_kit/" html)))
        (is (str/includes? html "<script type=\"module\">import \"hc/decide.mjs\";</script>")))
      (testing "every module is under a bare name, with no relative import left"
        (is (contains? imports "hc/decide.mjs"))
        (is (contains? imports "hc/squint-core.js"))
        (is (contains? imports "hc/viz/mermaid.mjs"))
        (is (empty? (for [[k v] imports
                          :when (str/starts-with? k "hc/")
                          :when (re-find #"(\bfrom|\bimport)\s*\(?\s*['\"]\.{1,2}/" (decoded v))]
                      k))))
      (testing "jsdiff is inlined under the URL the runtime imports"
        (is (str/includes? (decoded (get imports export/jsdiff-url)) "parsePatch"))))
    (testing "a second export of the unchanged page keeps the revision"
      (is (= 1 (:rev (export/export! root "s1" "p" nil)))))))

(deftest export-refuses-what-it-can't-copy
  (let [root (temp-dir)]
    (is (thrown-with-msg? Exception #"no page s1/missing"
                          (export/export! root "s1" "missing" nil)))
    (write-page! root "s1" "plain" "<html><body>no kit</body></html>")
    (is (thrown-with-msg? Exception #"0 /_kit/decide.css tags"
                          (export/export! root "s1" "plain" nil)))))

(deftest export-page-tool-resolves-out-against-the-project
  (let [project (temp-dir)
        root (str (fs/path project "ai" "html-channel"))]
    (write-page! root "s1" "p" page-html)
    (with-redefs [store/project-dir (constantly project)]
      (let [r (mcp/export-page {:root root :port 8788} {:session "s1" :page "p" :out "share/p.html"})]
        (is (= (str (fs/canonicalize (fs/path project "share" "p.html"))) (:path r)))
        (is (= 1 (:rev r)))))))

(deftest jsdiff-is-pinned-to-one-version
  ;; The runtime imports versions.cljs's URL, export inlines under its own constant,
  ;; and the bytes are runtime/vendor/jsdiff.mjs: a bump must change all three,
  ;; or offline copies lose diffs without internet.
  (let [version-of #(second (re-find #"diff@(\d+\.\d+\.\d+)" %))
        runtime (version-of (slurp "runtime/viz/versions.cljs"))]
    (is (some? runtime))
    (is (= runtime (version-of export/jsdiff-url)) "export/jsdiff-url")
    (is (= runtime (version-of (slurp "runtime/vendor/jsdiff.mjs"))) "runtime/vendor/jsdiff.mjs header")))
