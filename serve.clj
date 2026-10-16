#!/usr/bin/env bb
;; Tiny local decision server for interactive review pages.
;;
;;   bb serve.clj                       ; serve ./ on 127.0.0.1:8787
;;   bb serve.clj --root docs/reviews   ; serve another folder
;;   bb serve.clj --port 9000 --out my-decisions.jsonl
;;
;; Idempotent: if a decision server is already answering on the port, it
;; says so and exits 0, so an agent can run it blindly.
;;
;; Endpoints
;;   GET  /_health                 -> {"ok":true,"root":...,"out":...}
;;   GET  /_decisions?page=<id>    -> {"<decision-id>": <latest entry>, ...}
;;   POST /_decisions              -> append one JSON entry to the out file
;;   GET  /_kit/<file>             -> runtime files (decide.mjs, css, squint core)
;;   GET  /                        -> list of .html pages under root
;;   GET  /<path>                  -> static files from root

(ns serve
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.http-client :as hc]
            [cheshire.core :as json]
            [clojure.string :as str]
            [org.httpkit.server :as http]))

(def opts
  (cli/parse-opts *command-line-args*
                  {:coerce {:port :long}
                   :alias {:p :port :r :root :o :out}}))

(def port (:port opts 8787))
(def root (fs/canonicalize (:root opts ".")))
(def out-file (fs/path root (:out opts "decisions.jsonl")))
(def kit-dir (fs/canonicalize (fs/path (fs/parent *file*) "kit")))

(def mime
  {"html" "text/html; charset=utf-8" "htm" "text/html; charset=utf-8"
   "js" "text/javascript" "mjs" "text/javascript" "css" "text/css"
   "json" "application/json" "jsonl" "application/x-ndjson"
   "svg" "image/svg+xml" "png" "image/png" "jpg" "image/jpeg"
   "md" "text/markdown; charset=utf-8" "txt" "text/plain; charset=utf-8"})

(def base-headers
  {"Access-Control-Allow-Origin" "*"        ; lets file:// pages talk to us
   "Access-Control-Allow-Methods" "GET, POST, OPTIONS"
   "Access-Control-Allow-Headers" "Content-Type"
   "Access-Control-Allow-Private-Network" "true"
   "Cache-Control" "no-store"})             ; regenerated pages always fresh

(defn respond
  ([status] (respond status nil nil))
  ([status content-type body]
   {:status status
    :headers (cond-> base-headers content-type (assoc "Content-Type" content-type))
    :body body}))

(defn json-resp [data] (respond 200 "application/json" (json/generate-string data)))

(defn query-param [query-string k]
  (some (fn [kv]
          (let [[a b] (str/split kv #"=" 2)]
            (when (= a k) (java.net.URLDecoder/decode (or b "") "UTF-8"))))
        (some-> query-string (str/split #"&"))))

(defn read-entries []
  (when (fs/exists? out-file)
    (keep #(try (json/parse-string % true) (catch Exception _ nil))
          (fs/read-all-lines out-file))))

(defn latest-for-page [page]
  (reduce (fn [m e] (if (= page (:page e)) (assoc m (:id e) e) m))
          {} (read-entries)))

(def write-lock (Object.))

(defn append-entry! [body]
  (let [entry (-> (json/parse-string body true)
                  (update :at #(or % (str (java.time.Instant/now)))))]
    (when-not (and (string? (:page entry)) (string? (:id entry)))
      (throw (ex-info "entry needs string :page and :id" {})))
    (locking write-lock
      (spit (fs/file out-file) (str (json/generate-string entry) "\n") :append true))
    entry))

(defn file-under [dir rel]
  (let [f (fs/canonicalize (fs/path dir rel))]
    (when (and (str/starts-with? (str f) (str dir)) (fs/regular-file? f)) f)))

(defn serve-file [f]
  (respond 200 (get mime (fs/extension f) "application/octet-stream") (fs/file f)))

(defn index-page []
  (let [pages (->> (fs/glob root "**.{html,htm}")
                   (map #(str (fs/relativize root %)))
                   (remove #(str/starts-with? % "node_modules"))
                   sort)]
    (respond 200 "text/html; charset=utf-8"
             (str "<!doctype html><meta charset=utf-8><title>Review pages</title>"
                  "<body style='font:15px system-ui;max-width:40rem;margin:3rem auto'>"
                  "<h1>Review pages</h1><p><code>" root "</code></p><ul>"
                  (apply str (for [p pages] (str "<li><a href='/" p "'>" p "</a>")))
                  "</ul>"))))

(defn handler [{:keys [request-method uri query-string body]}]
  (let [uri (java.net.URLDecoder/decode uri "UTF-8")]
    (try
      (cond
        (= request-method :options) (respond 204)

        (= uri "/_health")
        (json-resp {:ok true :root (str root) :out (str out-file)})

        (and (= uri "/_decisions") (= request-method :get))
        (json-resp (latest-for-page (query-param query-string "page")))

        (and (= uri "/_decisions") (= request-method :post))
        (let [e (append-entry! (slurp body))]
          (println "decision" (:page e) (:id e) "=>" (pr-str (:value e)))
          (json-resp {:ok true}))

        (str/starts-with? uri "/_kit/")
        (if-let [f (file-under kit-dir (subs uri 6))] (serve-file f) (respond 404))

        (= uri "/") (index-page)

        :else
        (if-let [f (file-under root (subs uri 1))] (serve-file f) (respond 404)))
      (catch Exception e
        (respond 400 "text/plain" (ex-message e))))))

(defn already-running? []
  (try (= 200 (:status (hc/get (str "http://127.0.0.1:" port "/_health")
                               {:timeout 500 :throw false})))
       (catch Exception _ false)))

(if (already-running?)
  (println (str "Decision server already running on http://127.0.0.1:" port))
  (do
    (http/run-server handler {:ip "127.0.0.1" :port port})
    (println (str "Serving " root " on http://127.0.0.1:" port))
    (println (str "Decisions -> " out-file))
    @(promise)))
