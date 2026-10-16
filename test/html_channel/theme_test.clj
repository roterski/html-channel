(ns html-channel.theme-test
  "The base theme's text colors reach WCAG AA contrast (4.5:1) in both schemes."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def css (slurp "runtime/decide.css"))

(defn- tokens
  "{\"fg\" \"#14161a\" …} from the first `:root { … }` block in `s`."
  [s]
  (let [block (second (re-find #"(?s):root\s*\{(.*?)\}" s))]
    (into {} (map (fn [[_ k v]] [k v]) (re-seq #"--hc-([a-z-]+):\s*(#[0-9a-fA-F]{6})" block)))))

(def light (tokens css))
(def dark (tokens (subs css (str/index-of css "@media (prefers-color-scheme: dark)"))))

(defn- luminance [hex]
  (let [lin (fn [c] (let [c (/ c 255.0)] (if (<= c 0.03928) (/ c 12.92) (Math/pow (/ (+ c 0.055) 1.055) 2.4))))
        [r g b] (map #(lin (Integer/parseInt (subs hex % (+ % 2)) 16)) [1 3 5])]
    (+ (* 0.2126 r) (* 0.7152 g) (* 0.0722 b))))

(defn contrast [a b]
  (let [[hi lo] (sort > [(luminance a) (luminance b)])]
    (/ (+ hi 0.05) (+ lo 0.05))))

(def text-pairs
  "[text background] token pairs that pages and the kit put text on."
  [["fg" "bg"] ["muted" "bg"] ["fg" "card"] ["muted" "card"]
   ["accent" "bg"] ["on-accent" "accent"]
   ["good" "bg"] ["warn" "bg"] ["bad" "bg"] ["bad" "card"]])

(deftest every-text-pair-reaches-aa
  (doseq [[scheme ts] [["light" light] ["dark" dark]]
          [fg bg] text-pairs]
    (testing (str scheme " " fg " on " bg)
      (is (and (ts fg) (ts bg)) "token defined")
      (when (and (ts fg) (ts bg))
        (is (>= (contrast (ts fg) (ts bg)) 4.5)
            (format "%.2f:1" (contrast (ts fg) (ts bg))))))))

(deftest schemes-define-the-same-tokens
  (is (= (set (keys light)) (set (keys dark)))))
