(ns decide.offline
  "Transport for an offline copy (share mode): no server; answers are kept in the
  browser's localStorage, under the page's session, page and revision.

  The copy says what it is with
    <meta name=\"hc-offline\" content=\"session=…; page=…; rev=…; mode=share\">
  Copies are meant to come from an `html-channel export` that stamps it (not
  built yet); until then only test/offline.py writes the meta, by hand.
  Agents never write it: the revision exists only once the server has served the page.
  Storage that throws (blocked, full) answers \"rejected\": retrying can't help,
  and \"unreachable\" would flip the page offline until the next poll, then online,
  every few seconds. The answer stays shown, marked rejected, and Download still
  has it (decide.core/answers); only a reload loses it.")

(defn share-meta
  "{:session :page :rev} from the page's hc-offline meta when its mode is share; else nil."
  []
  (when-let [m (js/document.querySelector "meta[name=hc-offline]")]
    (let [fields (reduce (fn [acc part]
                           (let [i (.indexOf part "=")]
                             (if (pos? i)
                               (assoc acc (.trim (.slice part 0 i)) (.trim (.slice part (inc i))))
                               acc)))
                         {} (.split (or (.getAttribute m "content") "") ";"))]
      (when (= "share" (get fields "mode"))
        {:session (get fields "session")
         :page (get fields "page")
         :rev (js/parseInt (get fields "rev") 10)}))))

(defn storage-key [{:keys [session page rev]}]
  (str "html-channel:" session "/" page "@" rev))

(defn stored
  "id -> {:value :rev :at} saved for this copy; {} when none or unreadable."
  [k]
  (or (try (some-> (.getItem js/localStorage k) js/JSON.parse) (catch :default _ nil)) {}))

(defn transport
  "Pre: `copy` is share-meta's result."
  [{:keys [session page rev] :as copy}]
  (let [k (storage-key copy)]
    {:page page
     :session session
     :mode "share"
     :send! (fn [entry]
              (js/Promise.resolve
               (try
                 (let [m (stored k)]
                   (aset m (:id entry) {:value (:value entry) :rev rev :at (:at entry)})
                   (.setItem js/localStorage k (js/JSON.stringify m))
                   {:outcome "saved"})
                 (catch :default e
                   {:outcome "rejected"
                    :reason (str "localStorage refused it (" (.-message e) "); Download answers still has it")}))))
     :load (fn [] (js/Promise.resolve {:rev rev :done false :deeper [] :decisions (stored k)}))
     :rev (fn [] (js/Promise.resolve rev))}))
