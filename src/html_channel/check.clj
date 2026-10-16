(ns html-channel.check
  "Loads a page in headless Chrome, once light and once dark, and returns what
  the load reported: the errors /_kit/errors.js sent, tagged with this check's
  id, and a screenshot per color scheme.

  Pre: an html-channel server is serving `url`.
  Without Chrome, returns the errors readers' own page loads reported instead."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [html-channel.store :as store]))

(def mac-apps
  ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
   "/Applications/Chromium.app/Contents/MacOS/Chromium"])

(def path-names ["google-chrome" "google-chrome-stable" "chromium" "chromium-browser"])

(defn find-chrome
  "$HTML_CHANNEL_CHROME, else a standard macOS app, else a Chrome on PATH; nil when none."
  ([] (find-chrome (System/getenv "HTML_CHANNEL_CHROME")))
  ([env]
   (or (not-empty env)
       (first (filter fs/executable? mac-apps))
       (some #(some-> (fs/which %) str) path-names))))

(def schemes
  "Chrome flags per color scheme. Both are explicit: headless Chrome otherwise
  follows the system's appearance."
  (array-map "light" ["--blink-settings=preferredColorScheme=1"]
             "dark" ["--force-dark-mode" "--blink-settings=preferredColorScheme=0"]))

(def settle-ms
  "Virtual time the page gets before the screenshot: the runtime, adapters and
  CDN libraries run within it."
  3000)

(def timeout-ms 45000)

(defn- screenshot!
  "Runs Chrome once; throws when it writes no screenshot.
  The screenshot alone decides success: Chrome has been seen to stall in
  teardown and exit non-zero after writing it."
  [chrome url png flags]
  (let [profile (str (fs/create-temp-dir {:prefix "html-channel-chrome-"}))]
    (fs/delete-if-exists png)
    (try
      (let [proc (p/process (concat [chrome "--headless" "--disable-gpu" "--hide-scrollbars"
                                     "--no-first-run" "--no-default-browser-check"
                                     (str "--user-data-dir=" profile)
                                     (str "--virtual-time-budget=" settle-ms)
                                     "--window-size=900,1600" (str "--screenshot=" png)]
                                    flags [url])
                            {:in "" :out :string :err :string})
            r (deref proc timeout-ms ::timeout)]
        (when (= ::timeout r) (p/destroy-tree proc))
        (when-not (fs/exists? png)
          (throw (ex-info (str "Chrome wrote no screenshot of " url
                               (if (= ::timeout r)
                                 (str " within " (/ timeout-ms 1000) " s")
                                 (str ": " (str/join "\n" (take-last 5 (str/split-lines (str (:err r))))))))
                          {}))))
      (finally (fs/delete-tree profile)))))

(defn- check-dir!
  "<session>/.check/, ignored by git."
  [root session]
  (let [d (fs/path (store/session-dir root session) ".check")]
    (fs/create-dirs d)
    (spit (fs/file d ".gitignore") "*\n")
    d))

(defn check-page
  "{:page :rev :chrome :errors :screenshots {scheme path}} for the page's current revision.
  `url` is the page's served URL."
  ([root session page url] (check-page root session page url (find-chrome)))
  ([root session page url chrome]
   (let [f (store/page-file root session page)]
     (when-not (fs/regular-file? f)
       (throw (ex-info (str "no page at " f "; write it first") {})))
     (let [rev (store/current-rev! root session page)]
       (if-not chrome
         {:page page :rev rev :chrome false
          :errors (store/errors root session page rev)
          :hint (str "No Chrome found (set HTML_CHANNEL_CHROME). These errors come only from "
                     "readers' own page loads, and there are no screenshots.")}
         (let [id (subs (str (random-uuid)) 0 8)
               dir (check-dir! root session)
               shots (into (array-map)
                           (for [[scheme flags] schemes
                                 :let [png (str (fs/absolutize (fs/path dir (str page "@" rev "-" scheme ".png"))))]]
                             (do (screenshot! chrome (str url "?_check=" id) png flags)
                                 [scheme png])))]
           {:page page :rev rev :chrome true
            :errors (store/errors root session page rev id)
            :screenshots shots}))))))
