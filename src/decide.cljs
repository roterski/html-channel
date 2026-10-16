(ns decide
  "Generic runtime for interactive review pages.

  Conventions in the HTML (no page-specific JS needed):
    <body data-page=\"auth-review\">       page id (defaults to file name)
    <button data-decision=\"x\" data-choice=\"a\">   pick one choice per decision id
    <input|textarea|select data-decision=\"y\">     free-form value, saved on change
    <input type=checkbox data-decision=\"z\">       saved as true/false
    <div id=\"decide-offline\">...</div>            shown while the server is down

  Elements get data-state=\"pending\" until the server confirms, then \"saved\".")

(def server
  (let [u (js/URL. (.-url js/import.meta))] (.-origin u)))

(def page-id
  (or (.. js/document -body -dataset -page)
      (-> js/location .-pathname (.split "/") .pop (.replace #"\.html?$" ""))))

(def state (atom {:online nil :queue [] :flushing false}))

(defn els-for [id]
  (js/document.querySelectorAll (str "[data-decision=\"" (js/CSS.escape id) "\"]")))

(defn mark! [id s]
  (doseq [el (els-for id)] (set! (.. el -dataset -state) s)))

(defn set-online! [online?]
  (when (not= online? (:online @state))
    (swap! state assoc :online online?)
    (when-let [b (js/document.getElementById "decide-offline")]
      (set! (.-hidden b) online?))
    (.toggle (.-classList js/document.documentElement) "decide-is-offline" (not online?))))

(defn ^:async post! [entry]
  ;; no Content-Type header => "simple" CORS request, works from file:// too
  (try
    (let [r (js-await (js/fetch (str server "/_decisions")
                                {:method "POST" :body (js/JSON.stringify entry)}))]
      (.-ok r))
    (catch :default _ false)))

(defn ^:async flush! []
  (when-not (:flushing @state)
    (swap! state assoc :flushing true)
    (loop []
      (when-let [entry (first (:queue @state))]
        (if (js-await (post! entry))
          (do (swap! state update :queue #(vec (rest %)))
              (when-not (some #(= (:id %) (:id entry)) (:queue @state))
                (mark! (:id entry) "saved"))
              (recur))
          (set-online! false))))
    (swap! state assoc :flushing false)))

(defn record! [id value]
  (swap! state update :queue conj
         {:page page-id :id id :value value :at (.toISOString (js/Date.))})
  (mark! id "pending")
  (flush!))

;; ---- reading / writing element values ------------------------------------

(defn el-value [el]
  (if (= "checkbox" (.-type el)) (.-checked el) (.-value el)))

(defn restore! [decisions]
  (doseq [[id entry] (js/Object.entries decisions)]
    (let [v (:value entry)]
      (doseq [el (els-for id)]
        (set! (.. el -dataset -state) "saved")
        (cond
          (= "BUTTON" (.-tagName el))
          (.toggle (.-classList el) "chosen" (= v (.. el -dataset -choice)))

          (= "checkbox" (.-type el)) (set! (.-checked el) (boolean v))
          (= "radio" (.-type el)) (set! (.-checked el) (= v (.-value el)))
          :else (set! (.-value el) v))))))

;; ---- events --------------------------------------------------------------

(.addEventListener js/document "click"
  (fn [e]
    (when-let [btn (.closest (.-target e) "button[data-decision][data-choice]")]
      (let [id (.. btn -dataset -decision)]
        (doseq [b (els-for id)] (.remove (.-classList b) "chosen"))
        (.add (.-classList btn) "chosen")
        (record! id (.. btn -dataset -choice))))))

(.addEventListener js/document "change"
  (fn [e]
    (let [el (.-target e)]
      (when (and (.. el -dataset -decision) (not= "BUTTON" (.-tagName el)))
        (record! (.. el -dataset -decision) (el-value el))))))

;; ---- health polling ------------------------------------------------------

(defn ^:async check! []
  (let [ok (try (.-ok (js-await (js/fetch (str server "/_health") {:cache "no-store"})))
                (catch :default _ false))]
    (set-online! ok)
    (when (and ok (seq (:queue @state))) (flush!))
    ok))

(defn ^:async init! []
  (when (js-await (check!))
    (try
      (let [r (js-await (js/fetch (str server "/_decisions?page=" (js/encodeURIComponent page-id))))]
        (restore! (js-await (.json r))))
      (catch :default e (js/console.warn "decide: could not restore" e))))
  (js/setInterval check! 3000))

(init!)
