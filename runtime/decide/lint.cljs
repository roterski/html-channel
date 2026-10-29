(ns decide.lint
  "Structure rules for explorable pages (<meta name=\"hc-kind\" content=\"explorable\">),
  reported as kind \"explorable\" with the rule as the source:

    sections           fewer than 6 or more than 12 <section data-concept>
    no-instrument      a concept section without a [data-instrument] inside it
    no-limits          no h2/h3 heading that starts with \"Limits\"
    no-sources         no h2/h3 heading that starts with \"Sources\"
    unlabeled-control  a control inside an instrument with no accessible name

  It runs once the page has loaded, plus half a second, so instruments that
  page code builds while loading are counted. Instruments built later still
  aren't.

  Pre: decide.explore has started, so the controls it renders carry their labels.")

(def min-sections 6)
(def max-sections 12)

(defn heading? [re]
  (some #(re-find re (.trim (.-textContent %)))
        (js/Array.from (js/document.querySelectorAll "h2, h3"))))

(defn describe [el]
  (str "<" (.toLowerCase (.-tagName el))
       (if (seq (.-id el)) (str " id=\"" (.-id el) "\"") "")
       (if-let [n (not-empty (.getAttribute el "name"))] (str " name=\"" n "\"") "")
       (if-let [c (not-empty (.getAttribute el "class"))] (str " class=\"" c "\"") "")
       ">"))

(defn labeled? [el]
  (or (some-> (.getAttribute el "aria-label") .trim seq)
      (.hasAttribute el "aria-labelledby")
      (some-> (.getAttribute el "title") .trim seq)
      (pos? (or (some-> (.-labels el) .-length) 0))
      (and (= "BUTTON" (.-tagName el)) (seq (.trim (.-textContent el))))))

(defn findings []
  (let [concepts (js/Array.from (js/document.querySelectorAll "section[data-concept]"))
        n (count concepts)
        controls (js/Array.from
                  (js/document.querySelectorAll
                   "[data-instrument] :is(input:not([type=hidden]), select, textarea, button, [data-scrub])"))]
    (concat
     (when (or (< n min-sections) (> n max-sections))
       [["sections" (str n " <section data-concept> on the page; an explorable has "
                         min-sections "–" max-sections)]])
     (for [s concepts
           :when (not (.querySelector s "[data-instrument]"))]
       ["no-instrument" (str "concept section \"" (.. s -dataset -concept) "\" has no [data-instrument]")])
     (when-not (heading? #"^Limits\b")
       [["no-limits" "no h2/h3 heading starting with \"Limits\": say where the model stops being true"]])
     (when-not (heading? #"^Sources\b")
       [["no-sources" "no h2/h3 heading starting with \"Sources\": list where the facts and constants come from"]])
     (for [c controls
           :when (not (labeled? c))]
       ["unlabeled-control" (str (describe c) " in instrument \""
                                 (.. (.closest c "[data-instrument]") -dataset -instrument)
                                 "\" has no accessible name (a <label>, aria-label or title)")]))))

(defn start!
  "Reports each finding once the page has loaded. `report!` is (fn [kind message source])."
  [report!]
  (let [run #(js/setTimeout (fn [] (doseq [[rule message] (findings)]
                                     (report! "explorable" message rule)))
                            500)]
    (if (= "complete" (.-readyState js/document))
      (run)
      (.addEventListener js/window "load" run))))
