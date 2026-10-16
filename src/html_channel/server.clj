(ns html-channel.server
  "HTTP server for every registered channel root, plus the standby loop.

  Routes
    GET  /_health                        {ok, owner_pid, mounts}
    GET  /_kit/<file>                    runtime files from the classpath
    GET  /                               index of pages
    GET  /<mount>/<session>/<file>       static file (pages get their rev checked first)
    GET  /<mount>/<session>/_rev?page=   {rev}; the runtime's health poll
    GET  /<mount>/<session>/_decisions?page=   effective answers
    POST /<mount>/<session>/_decisions   append one entry

  Ownership: whichever process binds 127.0.0.1:<port> serves; every other
  html-channel process retries the bind every second (`run-standby!`)."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [html-channel.registry :as registry]
            [html-channel.store :as store]
            [org.httpkit.server :as http]))

(def default-port 8787)

(defn port-from [opt]
  (or opt (some-> (System/getenv "HTML_CHANNEL_PORT") parse-long) default-port))

(defn log [& xs] (binding [*out* *err*] (apply println "html-channel:" xs)))

(def mime
  {"html" "text/html; charset=utf-8" "htm" "text/html; charset=utf-8"
   "js" "text/javascript" "mjs" "text/javascript" "css" "text/css"
   "json" "application/json" "jsonl" "application/x-ndjson"
   "svg" "image/svg+xml" "png" "image/png" "jpg" "image/jpeg"
   "md" "text/markdown; charset=utf-8" "txt" "text/plain; charset=utf-8"})

(def base-headers
  {"Access-Control-Allow-Origin" "*"
   "Access-Control-Allow-Methods" "GET, POST, OPTIONS"
   "Access-Control-Allow-Headers" "Content-Type"
   "Access-Control-Allow-Private-Network" "true"
   "Cache-Control" "no-store"})

(defn- respond
  ([status] (respond status nil nil))
  ([status content-type body]
   {:status status
    :headers (cond-> base-headers content-type (assoc "Content-Type" content-type))
    :body body}))

(defn- json-resp [data] (respond 200 "application/json" (json/generate-string data)))

(defn- not-found [] (respond 404 "text/plain" "not found"))

(defn- content-type [name] (get mime (fs/extension name) "application/octet-stream"))

(defn- query-param [query-string k]
  (some (fn [kv]
          (let [[a b] (str/split kv #"=" 2)]
            (when (= a k) (java.net.URLDecoder/decode (or b "") "UTF-8"))))
        (some-> query-string (str/split #"&"))))

(defn- kit-file [name]
  (when (re-matches #"[A-Za-z0-9_-][A-Za-z0-9._-]*(/[A-Za-z0-9_-][A-Za-z0-9._-]*)*" name)
    (when-not (str/includes? name "..")
      (io/resource (str "html_channel/kit/" name)))))

(defn- file-under
  "`rel` resolved under `dir`, or nil when it escapes `dir` or isn't a regular file."
  [dir rel]
  (let [dir (fs/canonicalize dir)
        f (fs/canonicalize (fs/path dir rel))]
    (when (and (str/starts-with? (str f) (str dir "/")) (fs/regular-file? f)) f)))

(defn- index-page []
  (let [rows (for [[mount root] (sort (registry/roots))
                   [session ps] (store/pages root)
                   p ps]
               (str "<li><a href='/" mount "/" session "/" p ".html'>"
                    mount " / " session " / " p "</a>"))]
    (respond 200 "text/html; charset=utf-8"
             (str "<!doctype html><meta charset=utf-8><title>Pages</title>"
                  "<body style='font:15px system-ui;max-width:44rem;margin:3rem auto;padding:0 1rem'>"
                  "<h1>Pages</h1><ul>" (apply str rows) "</ul>"))))

(defn- session-route [{:keys [request-method query-string body]} root session rest]
  (let [page (query-param query-string "page")]
    (cond
      (and (= rest "_rev") (= request-method :get))
      (json-resp {:rev (store/current-rev! root session page)})

      (and (= rest "_decisions") (= request-method :get))
      (json-resp (store/effective root session page))

      (and (= rest "_decisions") (= request-method :post))
      (let [e (store/append-entry! root session (json/parse-string (slurp body) true))]
        (log "decision" (:page e) (str "rev " (:rev e)) (:id e) "=>" (pr-str (:value e)))
        (json-resp {:ok true :rev (:rev e)}))

      (= request-method :get)
      (do (when-let [[_ page] (re-matches #"([a-z0-9][a-z0-9-]*)\.html" rest)]
            (store/current-rev! root session page))
          (if-let [f (file-under (store/session-dir root session) rest)]
            (respond 200 (content-type rest) (fs/file f))
            (not-found)))

      :else (not-found))))

(defn handler [{:keys [request-method uri] :as req}]
  (try
    (let [uri (java.net.URLDecoder/decode uri "UTF-8")]
      (cond
        (= request-method :options) (respond 204)
        (= uri "/_health") (json-resp {:ok true
                                       :owner_pid (.pid (java.lang.ProcessHandle/current))
                                       :mounts (sort (keys (registry/roots)))})
        (str/starts-with? uri "/_kit/")
        (if-let [r (kit-file (subs uri 6))]
          (respond 200 (content-type (subs uri 6)) (slurp r))
          (not-found))
        (= uri "/") (index-page)
        :else
        (if-let [[_ mount session rest] (re-matches #"/([^/]+)/([^/]+)/(.+)" uri)]
          (if-let [root (and (store/valid-name? session) (registry/root-for mount))]
            (session-route req root session rest)
            (not-found))
          (not-found))))
    (catch clojure.lang.ExceptionInfo e
      (respond (:status (ex-data e) 400) "text/plain" (ex-message e)))
    (catch Exception e
      (respond 400 "text/plain" (str (.getSimpleName (class e)) ": " (ex-message e))))))

(defn try-bind
  "Starts serving on 127.0.0.1:`port`; nil when another process owns it."
  [port]
  (try (http/run-server #'handler {:ip "127.0.0.1" :port port :legacy-return-value? false})
       (catch Exception _ nil)))

(defn run-standby!
  "Binds the port, or retries every second until the owner goes away.
  Returns a future that runs as long as this process does."
  [port]
  (future
    (loop [announced? false]
      (if (try-bind port)
        (log "serving on" (str "http://127.0.0.1:" port))
        (do (when-not announced? (log "port" port "is owned by another process; standing by"))
            (Thread/sleep 1000)
            (recur true))))))
