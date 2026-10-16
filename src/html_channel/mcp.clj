(ns html-channel.mcp
  "MCP server over stdio: newline-delimited JSON-RPC 2.0.

  Tools: channel_setup, ask_human, get_decisions. The channel root is --root,
  else $HTML_CHANNEL_ROOT, else <project>/ai/html-channel (`store/root-from`).
  The process stands by for the HTTP port for as long as it runs, so pages stay
  served while any session lives.

  Only JSON-RPC messages go to stdout; everything else is logged to stderr."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [html-channel.registry :as registry]
            [html-channel.server :as server]
            [html-channel.session :as session]
            [html-channel.store :as store]))

(def version "0.1.0")

(def poll-ms 500)
(def progress-every-ms 30000)
(def default-max-wait-s 1500)

;; ---- output ----------------------------------------------------------------

(def ^:private stdout (java.io.PrintWriter. (java.io.OutputStreamWriter. System/out "UTF-8") true))

(defn- send! [msg]
  (let [line (json/generate-string (assoc msg :jsonrpc "2.0"))]
    (locking stdout (.println stdout line))))

;; ---- channel context -------------------------------------------------------

(defn convention []
  (some-> (io/resource "html_channel/CONVENTION.md") slurp))

(def ^:private default-session (atom nil))

(defn- channel
  "Registers the root and resolves the session: argument, else this process's
  default, else the client's id or a fresh one (which becomes the default)."
  [{:keys [root port]} explicit-session]
  (let [mount (registry/register! root)
        s (or explicit-session @default-session (swap! default-session #(or % (session/pick nil))))]
    (store/session-dir root s) ; validates the name
    {:root root :session s :mount mount
     :url-base (str "http://127.0.0.1:" port "/" mount "/" s "/")}))

(defn- page-result [{:keys [root session url-base]} page]
  (merge {:page page :url (str url-base page ".html")}
         (store/effective root session page)))

;; ---- tools -----------------------------------------------------------------

(defn channel-setup [ctx {:keys [session]}]
  (let [{:keys [root session url-base]} (channel ctx session)]
    (reset! default-session session)
    (fs/create-dirs (store/session-dir root session))
    {:dir (str (fs/canonicalize (store/session-dir root session)))
     :url_base url-base
     :session session
     :convention (convention)}))

(defn get-decisions [ctx {:keys [page session]}]
  (page-result (channel ctx session) page))

(defn ask-human
  "Blocks until the page's current revision has status=done, or max-wait passes."
  [{:keys [max-wait-s] :as ctx} {:keys [page session]} progress!]
  (let [ch (channel ctx session)
        f (store/page-file (:root ch) (:session ch) page)]
    (when-not (fs/regular-file? f)
      (throw (ex-info (str "no page at " f "; write it first (see channel_setup's convention)") {})))
    (let [deadline (+ (System/currentTimeMillis) (* 1000 max-wait-s))]
      (loop [next-progress (+ (System/currentTimeMillis) progress-every-ms) ticks 0]
        (let [r (page-result ch page)
              now (System/currentTimeMillis)]
          (cond
            (:done r) (assoc r :status "done")
            (>= now deadline) (assoc r :status "waiting"
                                     :hint "Not done yet. Call ask_human again to keep waiting.")
            :else
            (let [due? (>= now next-progress)]
              (when due? (progress! (inc ticks) (str "waiting for Done on " (:url r))))
              (Thread/sleep poll-ms)
              (recur (if due? (+ now progress-every-ms) next-progress)
                     (if due? (inc ticks) ticks)))))))))

(def page-arg {:type "string" :description "Page slug: the file name without .html, matching [a-z0-9-]+."})
(def session-arg {:type "string" :description "Session id from channel_setup. Defaults to this conversation's session."})

(def tools
  [{:name "channel_setup"
    :description (str "Start here before writing an html-channel page. Returns the directory to write "
                      "pages into, their URL base, the session id, and the page CONVENTION (markup rules). "
                      "Then write <dir>/<page>.html and give the user its URL.")
    :inputSchema {:type "object" :properties {:session session-arg}}}
   {:name "ask_human"
    :description (str "Wait until the user clicks the Done button on a decision page, then return their "
                      "answers. Returns status 'waiting' if they take too long; call again to keep waiting. "
                      "Rewriting the page starts a new revision and needs a new Done. "
                      "Report-only pages without a Done button don't need this.")
    :inputSchema {:type "object" :properties {:page page-arg :session session-arg} :required ["page"]}}
   {:name "get_decisions"
    :description "Current answers on a page without waiting: {done, rev, decisions}."
    :inputSchema {:type "object" :properties {:page page-arg :session session-arg} :required ["page"]}}])

;; ---- JSON-RPC --------------------------------------------------------------

(defn- tool-result [data]
  {:content [{:type "text" :text (json/generate-string data)}]
   :structuredContent data})

(defn- call-tool [ctx {:keys [name arguments _meta]}]
  (let [token (:progressToken _meta)
        progress! (fn [n message]
                    (when token
                      (send! {:method "notifications/progress"
                              :params {:progressToken token :progress n :message message}})))]
    (try
      (tool-result
       (case name
         "channel_setup" (channel-setup ctx arguments)
         "ask_human" (ask-human ctx arguments progress!)
         "get_decisions" (get-decisions ctx arguments)
         (throw (ex-info (str "unknown tool " name) {}))))
      (catch InterruptedException e (throw e))
      (catch Exception e
        {:content [{:type "text" :text (ex-message e)}] :isError true}))))

(def ^:private in-flight (atom {}))

(defn- handle! [ctx {:keys [id method params] :as _msg}]
  (case method
    "initialize"
    (send! {:id id :result {:protocolVersion (or (:protocolVersion params) "2025-06-18")
                            :capabilities {:tools {}}
                            :serverInfo {:name "html-channel" :version version}}})
    "ping" (send! {:id id :result {}})
    "tools/list" (send! {:id id :result {:tools tools}})
    "tools/call"
    (swap! in-flight assoc id
           (future
             (try (send! {:id id :result (call-tool ctx params)})
                  (catch InterruptedException _ nil)
                  (finally (swap! in-flight dissoc id)))))
    "notifications/cancelled"
    (some-> (get @in-flight (:requestId params)) future-cancel)
    (when (and id (not (re-find #"^notifications/" (str method))))
      (send! {:id id :error {:code -32601 :message (str "method not found: " method)}}))))

(defn serve-stdio!
  "Serves MCP on stdin/stdout until stdin closes, standing by for the HTTP port meanwhile."
  [{:keys [root port max-wait-s]}]
  (let [port (server/port-from port)
        ctx {:root (store/root-from root) :port port :max-wait-s (or max-wait-s default-max-wait-s)}]
    (server/run-standby! port)
    (doseq [line (line-seq (io/reader System/in))
            :when (seq line)]
      (let [msg (try (json/parse-string line true) (catch Exception _ nil))]
        (if msg
          (handle! ctx msg)
          (send! {:id nil :error {:code -32700 :message "parse error"}}))))
    (System/exit 0)))
