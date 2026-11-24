(ns decide.download
  "Download: the page's answers as an .edn file, offered next to Done on every
  decision page (CONVENTION, Answering without the server).

  The page is in one of three states:
    connected     the server answers: Done and Download; the file's respondent is :author
    disconnected  a served page whose server went away: Download, no Done (decide.css),
                  the banner says Download keeps the answers, and an optional name field
    share         an offline copy (decide.offline): Download and the name field only

  Download is a pure read: it records and sends nothing, so Done stays the only
  signal to the agent. The file holds every answer the page shows, unsent ones
  included (decide.core/answers), plus the reports Done would record
  (decide.core/done-reports). It is read back with
  `html-channel decisions --file`, which its first line names.

  While not connected, leaving the page after an answer changed since the last
  Download asks first."
  (:require [decide.core :as core]
            [decide.edn :as edn]))

(def name-key "html-channel:respondent")

;; true once an answer changed after load or after the last Download
(def unsaved (atom false))

(defn done-button []
  (js/document.querySelector "[data-decision=status][data-choice=done]"))

(defn page-state []
  (cond
    (= "share" (:mode @core/transport)) "share"
    (false? (:online @core/state)) "disconnected"
    :else "connected"))

(defn typed-name []
  (some-> (js/document.querySelector ".hc-download-name") .-value .trim not-empty))

(defn respondent
  "The keyword :author when connected (the reader is the person the agent works
  with); else the typed name, or nil."
  []
  (if (= "connected" (page-state)) (edn/kw "author") (typed-name)))

(defn file-text []
  (let [t @core/transport
        reports (core/done-reports)
        decisions (reduce (fn [m [id entry]]
                            (if (.has core/report-only-ids id) m (assoc m id entry)))
                          {} (js/Object.entries @core/answers))]
    (str ";; html-channel answers. Read with: html-channel decisions --file <this file>\n"
         (edn/write-fields
          [["html-channel/answers" 1]
           ["session" (:session t)]
           ["page" (:page t)]
           ["rev" (:rev @core/state)]
           ["respondent" (respondent)]
           ["state" (page-state)]
           ["exported-at" (.toISOString (js/Date.))]
           ["done" true]
           ["decisions" decisions]
           ["sources" (get reports "sources")]
           ["hunks" (get reports "hunks")]
           ["moot" (get reports "moot")]])
         "\n")))

(defn slug [s]
  (-> (.toLowerCase s)
      (.replace (js/RegExp. "[^a-z0-9]+" "g") "-")
      (.replace (js/RegExp. "^-+|-+$" "g") "")))

(defn file-name
  "<page>@<rev>.edn, with -<name> when a name was typed and the page isn't connected."
  []
  (let [rev (:rev @core/state)
        who (when (not= "connected" (page-state)) (some-> (typed-name) slug not-empty))]
    (str (:page @core/transport) (when (some? rev) (str "@" rev)) (when who (str "-" who)) ".edn")))

(defn download! []
  (let [url (js/URL.createObjectURL (js/Blob. [(file-text)] {:type "application/octet-stream"}))
        a (js/document.createElement "a")]
    (set! (.-href a) url)
    (set! (.-download a) (file-name))
    (.append js/document.body a)
    (.click a)
    (.remove a)
    ;; revoked late: some browsers fail a download whose URL is revoked at once
    (js/setTimeout #(js/URL.revokeObjectURL url) 60000)
    (reset! unsaved false)))

(defn controls []
  (let [box (js/document.createElement "span")
        input (js/document.createElement "input")
        btn (js/document.createElement "button")]
    (set! (.-className box) "hc-download")
    (set! (.-className input) "hc-download-name")
    (set! (.-placeholder input) "Your name (optional)")
    (.setAttribute input "aria-label" "Your name, optional, saved in the downloaded file")
    (set! (.-value input) (or (try (.getItem js/localStorage name-key) (catch :default _ nil)) ""))
    (.addEventListener input "change"
                       (fn [_] (try (.setItem js/localStorage name-key (.trim (.-value input)))
                                    (catch :default _ nil))))
    (set! (.-className btn) "hc-download-button")
    (set! (.-type btn) "button")
    (set! (.-textContent btn) "Download answers")
    (.addEventListener btn "click" (fn [_] (download!)))
    (.append box input btn)
    box))

(defn add-banner-hint! []
  (when-let [b (js/document.getElementById "decide-offline")]
    (let [s (js/document.createElement "span")]
      (set! (.-className s) "hc-download-hint")
      (set! (.-textContent s) " To send them another way, Download answers saves them to a file.")
      (.append b s))))

(defn start!
  "Adds Download next to Done; a page without Done (report-only) gets nothing.
  Pre: decide.core has started."
  []
  (when-let [done (done-button)]
    (.after done (controls))
    (add-banner-hint!)
    (core/on-record! (fn [_ _] (reset! unsaved true)))
    (.addEventListener js/window "beforeunload"
                       (fn [e]
                         (when (and @unsaved (not= "connected" (page-state)))
                           (.preventDefault e)
                           (set! (.-returnValue e) ""))))))
