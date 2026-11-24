(ns html-channel.export
  "Offline copies: a page made into one self-contained .html that runs from disk
  (file://) with no server, its answers kept in the reader's browser and saved
  with Download (CONVENTION, Answering without the server).

  The copy is the page with its three /_kit tags replaced:
    decide.css  -> an inline <style>
    boot.js     -> the hc-offline meta, then boot.js inline
    decide.mjs  -> an import map of every kit module as a data: URL, under a bare
                   name (hc/<path>), then a module importing hc/decide.mjs
  Browsers block relative module imports from file:// and can't resolve them
  inside a data: module, so each module's relative imports are rewritten to the
  bare names. jsdiff is inlined under its CDN URL, so diffs and hunk choices
  work offline; the other libraries still load from the CDN when online."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [html-channel.store :as store])
  (:import [java.util Base64]))

(def jsdiff-url
  "Must equal runtime/viz/versions.cljs jsdiff: the import map key replaces it."
  "https://cdn.jsdelivr.net/npm/diff@9.0.0/+esm")

(def kit-dir "html_channel/kit")

(defn- kit-root
  "The kit directory on the classpath. Pre: resources are a directory, not a jar."
  []
  (let [u (io/resource kit-dir)]
    (when-not (and u (= "file" (.getProtocol u)))
      (throw (ex-info (str "can't find the kit as files on the classpath: " u) {})))
    (fs/path (io/file u))))

(def import-re
  "A relative specifier in a static import/export or a literal dynamic import()."
  #"((?:\bfrom|\bimport)\s*\(?\s*)(['\"])(\.{1,2}/[^'\"]+)\2")

(defn bare-name [kit-path] (str "hc/" kit-path))

(defn- resolve-relative
  "The kit path `spec` names, relative to the module at kit path `from`."
  [from spec]
  (let [parts (reduce (fn [acc seg]
                        (case seg
                          "." acc
                          ".." (if (seq acc)
                                 (pop acc)
                                 (throw (ex-info (str spec " in " from " leaves the kit") {})))
                          (conj acc seg)))
                      (vec (butlast (str/split from #"/")))
                      (str/split spec #"/"))]
    (str/join "/" parts)))

(defn rewrite-imports
  "`source` of the module at kit path `from`, its relative specifiers made bare."
  [from source]
  (str/replace source import-re
               (fn [[_ head q spec]]
                 (str head q (bare-name (resolve-relative from spec)) q))))

(defn- data-url [source]
  (str "data:text/javascript;base64,"
       (.encodeToString (Base64/getEncoder) (.getBytes ^String source "UTF-8"))))

(defn- kit-modules
  "{kit-path source} of every kit module but boot.js and the vendored libraries,
  imports rewritten."
  [root]
  (into (sorted-map)
        (for [f (concat (fs/glob root "**.mjs") (fs/glob root "*.js"))
              :let [rel (str/replace (str (fs/relativize root f)) "\\" "/")]
              :when (and (not= "boot.js" rel) (not (str/starts-with? rel "vendor/")))]
          [rel (rewrite-imports rel (slurp (fs/file f)))])))

(defn- import-map
  "The import map JSON: each kit module under its bare name, jsdiff under its CDN URL."
  [root]
  (json/generate-string
   {:imports (-> (into {} (for [[rel source] (kit-modules root)] [(bare-name rel) (data-url source)]))
                 (assoc jsdiff-url (data-url (slurp (fs/file root "vendor" "jsdiff.mjs")))))}))

(def kit-tags
  {:css #"<link\b[^>]*\bhref=\"/_kit/decide\.css\"[^>]*>"
   :boot #"<script\b[^>]*\bsrc=\"/_kit/boot\.js\"[^>]*>\s*</script>"
   :runtime #"<script\b[^>]*\bsrc=\"/_kit/decide\.mjs\"[^>]*>\s*</script>"})

(defn- replace-once
  "`html` with the one match of `re` replaced by `replacement` (used literally).
  Throws when `re` matches zero or several times."
  [html re what replacement]
  (let [n (count (re-seq re html))]
    (when-not (= 1 n)
      (throw (ex-info (str "the page has " n " " what " tags, not 1: is it an html-channel page (CONVENTION, Required markup)?")
                      {})))
    (str/replace-first html re (str/re-quote-replacement replacement))))

(defn offline-copy
  "The offline copy of `html`, the page `session`/`page` at revision `rev`."
  [html {:keys [session page rev]}]
  (let [root (kit-root)
        meta-tag (str "<meta name=\"hc-offline\" content=\"session=" session "; page=" page "; rev=" rev "; mode=share\">")]
    (-> html
        (replace-once (:css kit-tags) "/_kit/decide.css"
                      (str "<style>\n" (slurp (fs/file root "decide.css")) "</style>"))
        (replace-once (:boot kit-tags) "/_kit/boot.js"
                      (str meta-tag "\n<script>\n" (slurp (fs/file root "boot.js")) "</script>"))
        (replace-once (:runtime kit-tags) "/_kit/decide.mjs"
                      (str "<script type=\"importmap\">" (import-map root) "</script>\n"
                           "<script type=\"module\">import \"hc/decide.mjs\";</script>")))))

(defn default-out
  "Where export writes when no path is given: next to the page, in its session."
  [root session page rev]
  (str (fs/path (store/session-dir root session) (str page "@" rev ".offline.html"))))

(defn export!
  "Writes the offline copy of `session`/`page` at its current revision (snapshotting
  it first, as serving would) to `out`, else default-out. Returns {:path :rev :bytes}."
  [root session page out]
  (let [f (store/page-file root session page)]
    (when-not (fs/regular-file? f)
      (throw (ex-info (str "no page " session "/" page " under " root) {})))
    (let [rev (store/current-rev! root session page)
          copy (offline-copy (slurp (fs/file f)) {:session session :page page :rev rev})
          path (or out (default-out root session page rev))
          bytes (.getBytes ^String copy "UTF-8")]
      (some-> (fs/parent path) fs/create-dirs)
      (with-open [o (io/output-stream (fs/file path))] (.write o bytes))
      {:path (str (fs/canonicalize path)) :rev rev :bytes (alength bytes)})))
