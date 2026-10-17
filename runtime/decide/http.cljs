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

(defn ^:async post-entry
  "Sends one entry. {:outcome \"saved\"}; {:outcome \"rejected\" :reason text} when
  the server refuses the entry itself (a 4xx), so resending can't help; else
  {:outcome \"unreachable\"}."
  [url entry]
  (try
    (let [r (js-await (js/fetch url {:method "POST" :body (js/JSON.stringify entry)}))
          status (.-status r)]
      (cond
        (.-ok r) {:outcome "saved"}
        (and (>= status 400) (< status 500)) {:outcome "rejected" :reason (js-await (.text r))}
        :else {:outcome "unreachable"}))
    (catch :default _ {:outcome "unreachable"})))

(defn transport []
  (let [{:keys [base page]} (page-context)
        q (str "?page=" (js/encodeURIComponent page))]
    {:page page
     :send! (fn [entry] (post-entry (str base "/_decisions") entry))
     :load (fn [] (get-json (str base "/_decisions" q)))
     :rev (fn [] (.then (get-json (str base "/_rev" q)) #(some-> % :rev)))}))
