(ns html-channel.wait
  "`html-channel wait`: wait for the reader without holding an MCP call open.
  An agent runs it as a background command; its exit is what wakes the agent,
  so the chat stays free while the page is open."
  (:require [babashka.fs :as fs]
            [html-channel.store :as store]))

(def default-poll-ms 500)

(defn await-reader
  "Blocks until the reader acts on `page` at revision R, the revision current
  when the call starts.
  Pre: the page file exists; otherwise throws (caller bug).
  Post: `store/page-state` plus :status, one of
    \"done\"        Done at R, given before or during the call
    \"deeper\"      Deeper requested at R during the call; :deeper holds only those targets
    \"superseded\"  the page has a revision after R, so answers to R no longer count
    \"waiting\"     `max-wait-s` passed; only when it is given
  With no `max-wait-s` it waits until one of the others happens."
  [root session page {:keys [max-wait-s poll-ms] :or {poll-ms default-poll-ms}}]
  (let [f (store/page-file root session page)]
    (when-not (fs/regular-file? f)
      (throw (ex-info (str "no page at " f "; write it first") {})))
    (let [rev (store/current-rev! root session page)
          from (count (store/entries root session))
          deadline (some->> max-wait-s (* 1000) (+ (System/currentTimeMillis)))]
      (loop []
        ;; read the page state first: the Deeper read below then covers every entry it saw
        (let [st (store/page-state root session page)
              {:keys [targets]} (store/deeper-requests root session page rev from)]
          (cond
            (> (:rev st) rev) (assoc st :status "superseded")
            (:done st) (assoc st :status "done")
            (seq targets) (assoc st :status "deeper" :deeper targets)
            (and deadline (>= (System/currentTimeMillis) deadline)) (assoc st :status "waiting")
            :else (do (Thread/sleep poll-ms) (recur))))))))
