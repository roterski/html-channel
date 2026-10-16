(ns html-channel.registry
  "User-level list of channel roots, shared by every html-channel process.

  Each line of roots.jsonl is {mount, root, at}. Whichever process owns the
  port serves every root listed here, so pages survive the death of the
  process that registered them.

  Invariant: one mount names one root (the mount is derived from the root's
  absolute path), so an entry is never rewritten, only appended once."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [html-channel.store :as store]))

(defn state-dir []
  (or (System/getenv "HTML_CHANNEL_HOME")
      (some-> (System/getenv "XDG_STATE_HOME") (fs/path "html-channel") str)
      (str (fs/path (fs/home) ".local" "state" "html-channel"))))

(defn registry-file [] (fs/path (state-dir) "roots.jsonl"))

(defn- sha1-hex [^String s]
  (->> (.digest (java.security.MessageDigest/getInstance "SHA-1") (.getBytes s "UTF-8"))
       (map #(format "%02x" (bit-and % 0xff)))
       (apply str)))

(defn project-name
  "The directory a root belongs to: `<project>` for `<project>/ai/html-channel`,
  else the root itself."
  [root]
  (let [root (fs/absolutize root)]
    (str (fs/file-name (if (fs/ends-with? root store/default-root)
                         (nth (iterate fs/parent root) (count (fs/components store/default-root)))
                         root)))))

(defn mount-name
  "`<project-dir-name>-<first 6 hex of sha1(abs root)>`, sanitized for URLs.
  Pre: `root` is canonical, so every process derives the same mount."
  [root]
  (let [abs (str (fs/absolutize root))
        base (-> (project-name root) str/lower-case (str/replace #"[^a-z0-9-]+" "-"))]
    (str base "-" (subs (sha1-hex abs) 0 6))))

(defn- read-entries [f]
  (keep #(try (json/parse-string % true) (catch Exception _ nil)) (fs/read-all-lines f)))

(def ^:private cache (atom {:stamp nil :roots {}}))

(defn roots
  "{mount root} for every registered root that still exists on disk.
  Re-reads the file only when its mtime or size changes."
  []
  (let [f (registry-file)]
    (if-not (fs/exists? f)
      {}
      (let [stamp [(str f) (.toMillis (fs/last-modified-time f)) (fs/size f)]]
        (if (= stamp (:stamp @cache))
          (:roots @cache)
          (let [rs (into {} (for [{:keys [mount root]} (read-entries f)
                                  :when (fs/directory? root)]
                              [mount root]))]
            (reset! cache {:stamp stamp :roots rs})
            rs))))))

(defn root-for
  "The root a mount serves, or nil when unknown or deleted since registration."
  [mount]
  (when-let [r (get (roots) mount)]
    (when (fs/directory? r) r)))

(def ^:private write-lock (Object.))

(defn register!
  "Records `root` (created if missing) and returns its mount. Idempotent."
  [root]
  (fs/create-dirs root)
  (let [root (str (fs/canonicalize root))
        mount (mount-name root)
        f (registry-file)]
    (locking write-lock
      (when-not (and (fs/exists? f)
                     (some #(and (= mount (:mount %)) (= root (:root %))) (read-entries f)))
        (fs/create-dirs (fs/parent f))
        (spit (fs/file f)
              (str (json/generate-string {:mount mount :root root :at (str (java.time.Instant/now))}) "\n")
              :append true)))
    mount))
