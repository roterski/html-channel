(ns html-channel.session
  "Session ids: the directory and URL segment that groups one agent
  conversation's pages. Always 8 lowercase hex characters unless the agent
  passes its own name."
  (:require [clojure.string :as str]))

(defn mint []
  (let [bs (byte-array 4)]
    (.nextBytes (java.security.SecureRandom.) bs)
    (apply str (map #(format "%02x" (bit-and % 0xff)) bs))))

(defn from-env
  "First 8 characters of the client's session id, when the client exposes one."
  []
  (some-> (System/getenv "CLAUDE_CODE_SESSION_ID")
          str/lower-case
          (str/replace #"[^a-z0-9]" "")
          not-empty
          (as-> s (subs s 0 (min 8 (count s))))))

(defn pick
  "The explicit session, else the client's, else a fresh one."
  [explicit]
  (or explicit (from-env) (mint)))
