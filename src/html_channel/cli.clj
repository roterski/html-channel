(ns html-channel.cli
  "html-channel serve|mcp|session new|decisions|check|health|version"
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.http-client :as hc]
            [cheshire.core :as json]
            [clojure.string :as str]
            [html-channel.check :as check]
            [html-channel.mcp :as mcp]
            [html-channel.registry :as registry]
            [html-channel.server :as server]
            [html-channel.session :as session]
            [html-channel.store :as store]))

(def usage
  "html-channel — interactive HTML pages as an output channel for agents

  serve [--root ai/html-channel] [--port 8787]   serve registered roots; stand by if the port is taken
  mcp [--root] [--max-wait 1500] [--port]        MCP server on stdio (channel_setup, ask_human, get_decisions, check_page)
      [--channel]                                also a Claude Code channel: Done/Deeper clicks wake the session
  session new [--root]                           print {session, dir, url_base} for a new page session
  decisions <session>/<page> [--root]            print {done, rev, deeper, decisions, errors} as JSON
  check <session>/<page> [--root]                render in headless Chrome, light and dark; print {errors, screenshots}

  --root defaults to $HTML_CHANNEL_ROOT, else ai/html-channel; relative to $CLAUDE_PROJECT_DIR, else the cwd
  health [--port]                     print the running server's /_health; exit 1 if none
  version")

(def spec {:coerce {:port :long :max-wait :long :channel :boolean}
           :alias {:p :port :r :root}})

(defn- print-json [data] (println (json/generate-string data {:pretty true})))

(defn- url-base [port mount s] (str "http://127.0.0.1:" port "/" mount "/" s "/"))

(defn- serve [{:keys [root port]}]
  (let [root (store/root-from root)
        port (server/port-from port)
        mount (registry/register! root)]
    (server/log "root" (str (fs/canonicalize root)) "->" (str "http://127.0.0.1:" port "/" mount "/"))
    @(server/run-standby! port)
    @(promise)))

(defn- session-new [{:keys [root port]}]
  (let [root (store/root-from root)
        mount (registry/register! root)
        s (session/pick nil)
        dir (store/session-dir root s)]
    (fs/create-dirs dir)
    (print-json {:session s :dir (str (fs/canonicalize dir))
                 :url_base (url-base (server/port-from port) mount s)})))

(defn- session-page
  "[session page] from \"<session>/<page>\"."
  [cmd target]
  (let [[s page] (some-> target (str/split #"/" 2))]
    (when-not (and s page)
      (throw (ex-info (str "usage: html-channel " cmd " <session>/<page>") {})))
    [s page]))

(defn- serving? [port]
  (= 200 (:status (try (hc/get (str "http://127.0.0.1:" port "/_health") {:timeout 1000 :throw false})
                       (catch Exception _ nil)))))

(defn- check-page [target {:keys [root port]}]
  (let [[s page] (session-page "check" target)
        root (store/root-from root)
        port (server/port-from port)
        mount (registry/register! root)]
    (when-not (serving? port)
      (server/run-standby! port)
      (loop [n 0]
        (when-not (serving? port)
          (when (> n 50) (throw (ex-info (str "no html-channel server came up on port " port) {})))
          (Thread/sleep 100)
          (recur (inc n)))))
    (print-json (check/check-page root s page (str (url-base port mount s) page ".html")))))

(defn- decisions [target {:keys [root port]}]
  (let [[s page] (session-page "decisions" target)
        root (store/root-from root)
        mount (registry/register! root)]
    (print-json (merge {:page page :url (str (url-base (server/port-from port) mount s) page ".html")}
                       (store/page-state root s page)))))

(defn- health [{:keys [port]}]
  (let [r (try (hc/get (str "http://127.0.0.1:" (server/port-from port) "/_health")
                       {:timeout 1000 :throw false})
               (catch Exception _ nil))]
    (if (= 200 (:status r))
      (println (:body r))
      (do (binding [*out* *err*] (println "html-channel: no server on port" (server/port-from port)))
          (System/exit 1)))))

(defn -main [& args]
  (let [[cmd & more] args
        {:keys [opts args]} (cli/parse-args more spec)]
    (try
      (case cmd
        "serve" (serve opts)
        "mcp" (mcp/serve-stdio! {:root (:root opts) :port (:port opts) :max-wait-s (:max-wait opts)
                                 :channel? (:channel opts)})
        "session" (if (= "new" (first args)) (session-new opts) (throw (ex-info usage {})))
        "decisions" (decisions (first args) opts)
        "check" (do (check-page (first args) opts) (System/exit 0))
        "health" (health opts)
        "version" (println "html-channel" mcp/version)
        (do (println usage) (System/exit (if cmd 1 0))))
      (catch clojure.lang.ExceptionInfo e
        (binding [*out* *err*] (println (ex-message e)))
        (System/exit 1)))))
