(ns decide.http
  "Transport to the html-channel server the page was loaded from.
  Pre: the page URL is /<mount>/<session>/<page>.html.")

(defn page-context []
  (let [parts (-> js/location .-pathname (.split "/"))
        file (.pop parts)]
    {:base (str (.-origin (js/URL. (.-url js/import.meta))) (.join parts "/"))
     :page (.replace file #"\.html?$" "")}))

(defn ^:async get-json [url]
  (try
    (let [r (js-await (js/fetch url {:cache "no-store"}))]
      (when (.-ok r) (js-await (.json r))))
    (catch :default _ nil)))

(defn ^:async post-entry [url entry]
  ;; no Content-Type header => a "simple" CORS request, no preflight
  (try
    (.-ok (js-await (js/fetch url {:method "POST" :body (js/JSON.stringify entry)})))
    (catch :default _ false)))

(defn transport []
  (let [{:keys [base page]} (page-context)
        q (str "?page=" (js/encodeURIComponent page))]
    {:page page
     :send! (fn [entry] (post-entry (str base "/_decisions") entry))
     :load (fn [] (get-json (str base "/_decisions" q)))
     :rev (fn [] (.then (get-json (str base "/_rev" q)) #(some-> % :rev)))}))
