(ns decide.edn
  "Writes JSON-shaped values as EDN that clojure.edn reads back.

  Map keys are written as strings, so any key reads back as itself: question
  IDs may hold spaces, slashes or a leading digit. Only the top-level fields of
  write-fields are keywords. A value made with `kw` is written as a keyword.
  undefined, NaN and the infinities are written as nil.")

(def kw-name (js/Symbol "edn-keyword"))

(defn kw
  "A value write prints as the keyword :`n`. Pre: `n` is a valid keyword name."
  [n]
  (let [o {}] (aset o kw-name n) o))

(defn write [v]
  (cond
    (nil? v) "nil"
    (string? v) (js/JSON.stringify v)
    (boolean? v) (str v)
    (number? v) (if (js/Number.isFinite v) (str v) "nil")
    (some? (aget v kw-name)) (str ":" (aget v kw-name))
    (array? v) (str "[" (.join (.map v write) " ") "]")
    :else (str "{" (.join (.map (js/Object.entries v)
                                (fn [[k x]] (str (js/JSON.stringify k) " " (write x))))
                          ", ")
               "}")))

(defn write-fields
  "An EDN map of `fields`, [[keyword-name value] …], one field per line."
  [fields]
  (str "{" (.join (.map fields (fn [[k v]] (str ":" k " " (write v)))) "\n ") "}"))
