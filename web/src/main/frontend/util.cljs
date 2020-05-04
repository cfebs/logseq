(ns frontend.util
  (:require [goog.object :as gobj]
            [goog.dom :as gdom]
            [promesa.core :as p]
            [clojure.walk :as walk]
            [clojure.string :as string]
            [cljs-bean.core :as bean]
            [clojure.string :as string]
            ["/frontend/caret_pos" :as caret-pos]
            ["/frontend/caret_range" :as caret-range]
            [goog.string :as gstring]
            [goog.string.format]
            [dommy.core :as d]))

(defn format
  [fmt & args]
  (apply gstring/format fmt args))

(defn evalue
  [event]
  (gobj/getValueByKeys event "target" "value"))

(defn p-handle
  ([p ok-handler]
   (p-handle p ok-handler (fn [error]
                            (js/console.error error))))
  ([p ok-handler error-handler]
   (-> p
       (p/then (fn [result]
                 (ok-handler result)))
       (p/catch (fn [error]
                  (error-handler error))))))

(defn get-width
  []
  (gobj/get js/window "innerWidth"))

(defn indexed
  [coll]
  (map-indexed vector coll))

(defn find-first
  [pred coll]
  (first (filter pred coll)))

(defn get-local-date
  []
  (let [date (js/Date.)
        year (.getFullYear date)
        month (inc (.getMonth date))
        day (.getDate date)
        hour (.getHours date)
        minute (.getMinutes date)]
    {:year year
     :month month
     :day day
     :hour hour
     :minute minute}))

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

;; (defn format
;;   [fmt & args]
;;   (apply gstring/format fmt args))

(defn raw-html
  [content]
  [:div {:dangerouslySetInnerHTML
         {:__html content}}])

(defn span-raw-html
  [content]
  [:span {:dangerouslySetInnerHTML
          {:__html content}}])

(defn json->clj
  [json-string]
  (-> json-string
      (js/JSON.parse)
      (js->clj :keywordize-keys true)))

(defn remove-nils
  "remove pairs of key-value that has nil value from a (possibly nested) map. also transform map to nil if all of its value are nil"
  [nm]
  (walk/postwalk
   (fn [el]
     (if (map? el)
       (not-empty (into {} (remove (comp nil? second)) el))
       el))
   nm))

(defn index-by
  [col k]
  (->> (map (fn [entry] [(get entry k) entry])
         col)
       (into {})))

;; ".lg:absolute.lg:inset-y-0.lg:right-0.lg:w-1/2"
(defn hiccup->class
  [class]
  (some->> (string/split class #"\.")
           (string/join " ")
           (string/trim)))

(defn fetch-raw
  ([url on-ok on-failed]
   (fetch-raw url #js {} on-ok on-failed))
  ([url opts on-ok on-failed]
   (-> (js/fetch url opts)
       (.then (fn [resp]
                (if (>= (.-status resp) 400)
                  (on-failed resp)
                  (if (.-ok resp)
                    (-> (.text resp)
                        (.then bean/->clj)
                        (.then #(on-ok %)))
                    (on-failed resp))))))))

(defn fetch
  ([url on-ok on-failed]
   (fetch url #js {} on-ok on-failed))
  ([url opts on-ok on-failed]
   (-> (js/fetch url opts)
       (.then (fn [resp]
                (if (>= (.-status resp) 400)
                  (on-failed resp)
                  (if (.-ok resp)
                    (-> (.json resp)
                        (.then bean/->clj)
                        (.then #(on-ok %)))
                    (on-failed resp))))))))

(defn upload
  [url file on-ok on-failed]
  (-> (js/fetch url (clj->js {:method "put"
                              :body file}))
      (.then #(if (.-ok %)
                (on-ok %)
                (on-failed %)))))

(defn post
  [url body on-ok on-failed]
  (fetch url (clj->js {:method "post"
                       :headers {:Content-Type "application/json"}
                       :body (js/JSON.stringify (clj->js body))})
         on-ok
         on-failed))

(defn delete
  [url on-ok on-failed]
  (fetch url (clj->js {:method "delete"
                       :headers {:Content-Type "application/json"}})
         on-ok
         on-failed))

(defn get-weekday
  [date]
  (.toLocaleString date "en-us" (clj->js {:weekday "long"})))

(defn get-date
  ([]
   (get-date (js/Date.)))
  ([date]
   {:year (.getFullYear date)
    :month (inc (.getMonth date))
    :day (.getDate date)
    :weekday (get-weekday date)}))

(defn journals-path
  [year month]
  (let [month (if (< month 10) (str "0" month) month)]
    (str "journals/" year "_" month ".org")))

(defn current-journal-path
  []
  (let [{:keys [year month]} (get-date)]
    (journals-path year month)))

(defn zero-pad
  [n]
  (if (< n 10)
    (str "0" n)
    (str n)))

(defn year-month-day-padded
  ([]
   (year-month-day-padded (get-date)))
  ([date]
   (let [{:keys [year month day]} date]
     {:year year
      :month (zero-pad month)
      :day (zero-pad day)})))

(defn mdy
  ([]
   (mdy (js/Date.)))
  ([date]
   (let [{:keys [year month day]} (year-month-day-padded (get-date date))]
     (str month "/" day "/" year))))

(defn ymd
  ([]
   (ymd (js/Date.)))
  ([date]
   (let [{:keys [year month day]} (year-month-day-padded (get-date date))]
     (str year "/" month "/" day))))

(defn journal-name
  ([]
   (journal-name (js/Date.)))
  ([date]
   (str (get-weekday date) ", " (mdy date))))

(defn today
  []
  (journal-name))

(defn tomorrow
  []
  (let [d (js/Date.)
        _ (.setDate d (inc (.getDate (js/Date.))))]
    (journal-name d)))

(defn get-current-time
  []
  (let [d (js/Date.)]
    (.toLocaleTimeString
     d
     (gobj/get js/window.navigator "language")
     (bean/->js {:hour "2-digit"
                 :minute "2-digit"
                 :hour12 false}))))

(defn get-month-last-day
  []
  (let [today (js/Date.)
        date (js/Date. (.getFullYear today) (inc (.getMonth today)) 0)]
    (.getDate date)))

(defn parse-int
  [x]
  (if (string? x)
    (js/parseInt x)
    x))

(defn debounce
  "Returns a function that will call f only after threshold has passed without new calls
  to the function. Calls prep-fn on the args in a sync way, which can be used for things like
  calling .persist on the event object to be able to access the event attributes in f"
  ([threshold f] (debounce threshold f (constantly nil)))
  ([threshold f prep-fn]
   (let [t (atom nil)]
     (fn [& args]
       (when @t (js/clearTimeout @t))
       (apply prep-fn args)
       (reset! t (js/setTimeout #(do
                                   (reset! t nil)
                                   (apply f args))
                                threshold))))))

(def caret-range caret-range/getCaretRange)

(defn set-caret-pos!
  [input pos]
  (.setSelectionRange input pos pos))

(defn get-caret-pos
  [input]
  (bean/->clj ((gobj/get caret-pos "position") input)))

(defn minimize-html
  [s]
  (->> s
       (string/split-lines)
       (map string/trim)
       (string/join "")))

(defn stop [e]
  (doto e (.preventDefault) (.stopPropagation)))

(defn get-fragment
  []
  (when-let [hash js/window.location.hash]
    (when (> (count hash) 2)
      (-> (subs hash 1)
          (string/split #"\?")
          (first)))))

(defn scroll-into-view
  [element]
  (let [scroll-top (gobj/get element "offsetTop")
        scroll-top (if (zero? scroll-top)
                     (-> (gobj/get element "parentElement")
                         (gobj/get "offsetTop"))
                     scroll-top)]

    (when-let [main (first (array-seq (gdom/getElementsByTagName "main")))]
      (.scroll main #js {:top scroll-top
                         ;; :behavior "smooth"
                         }))))

(defn scroll-to-element
  [fragment]
  (when fragment
    (when-not (string/blank? fragment)
      (when-let [element (gdom/getElement fragment)]
        (scroll-into-view element)))))

(defn url-encode
  [string]
  (some-> string str (js/encodeURIComponent) (.replace "+" "%20")))

(defn url-decode
  [string]
  (some-> string str (js/decodeURIComponent)))

(defn link?
  [node]
  (contains?
   #{"A" "BUTTON"}
   (gobj/get node "tagName")))

(defn input?
  [node]
  (contains?
   #{"INPUT"}
   (gobj/get node "tagName")))

(defn journal?
  [path]
  (string/starts-with? path "journals/"))

(defn drop-first-line
  [s]
  (let [lines (string/split-lines s)
        others (some->> (next lines)
                        (string/join "\n"))]
    [(first lines)]))

(defn distinct-by
  [f col]
  (reduce
   (fn [acc x]
     (if (some #(= (f x) (f % )) acc)
       acc
       (vec (conj acc x))))
   []
   col))

(defn distinct-by-last-wins
  [f col]
  (reduce
   (fn [acc x]
     (if (some #(= (f x) (f %)) acc)
       (mapv
        (fn [v]
          (if (= (f x) (f v))
            x
            v))
        acc)
       (vec (conj acc x))))
   []
   col))

(defn get-git-owner-and-repo
  [repo-url]
  (take-last 2 (string/split repo-url #"/")))

(defn get-textarea-height
  [input]
  (some-> input
          (d/style)
          (gobj/get "height")
          (string/split #"\.")
          first
          (parse-int)))

(defn get-textarea-line-height
  [input]
  (try
    (some-> input
            (d/style)
            (gobj/get "lineHeight")
            ;; TODO: is this cross-platform?
            (string/replace "px" "")
            (parse-int))
    (catch js/Error _e
      24)))

(defn textarea-cursor-first-row?
  [input line-height]
  (< (:top (get-caret-pos input)) line-height))

(defn textarea-cursor-end-row?
  [input line-height]
  (> (+ (:top (get-caret-pos input)) line-height)
     (get-textarea-height input)))

(defn split-last [pattern s]
  (when-let [last-index (string/last-index-of s pattern)]
    [(subs s 0 last-index)
     (subs s (+ last-index (count pattern)) (count s))]))

(defn replace-last [pattern s new-value]
  (when-let [last-index (string/last-index-of s pattern)]
    (str (subs s 0 last-index)
         new-value)))

(defn move-cursor-to-end [input]
  (let [n (count (.-value input))]
    (set! (.-selectionStart input) n)
    (set! (.-selectionEnd input) n)))

(defn cursor-move-back [input n]
  (let [{:keys [pos]} (get-caret-pos input)]
    (set! (.-selectionStart input) (- pos n))
    (set! (.-selectionEnd input) (- pos n))))

(defn cursor-move-forward [input n]
  (let [{:keys [pos]} (get-caret-pos input)]
    (set! (.-selectionStart input) (+ pos n))
    (set! (.-selectionEnd input) (+ pos n))))

(defn move-cursor-to [input n]
  (set! (.-selectionStart input) n)
  (set! (.-selectionEnd input) n))
