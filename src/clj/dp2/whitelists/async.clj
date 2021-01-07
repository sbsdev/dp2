(ns dp2.whitelists.async
  "Asynchronous interface to writing whitelist tables. Refer
  to [[dp2.whitelists.tables]] for the synchronous API."
  (:require [chime.core :as chime]
            [chime.core-async :refer [chime-ch]]
            [clojure.core.async :as async :refer [<! >!! go-loop]]
            [dp2.whitelists.tables :as tables]
            [mount.core :refer [defstate]])
  (:import [java.time Duration Instant]))

(defstate local-dict-chan
  ;; throttle the write of the local tables to once every two minutes at most
  :start (let [times (rest (chime/periodic-seq (Instant/now) (Duration/ofMinutes 2)))
               cron (chime-ch times {:ch (async/chan (async/sliding-buffer 1))})
               ch (async/chan (async/sliding-buffer 1))]
           (go-loop []
             (let [document-id (<! ch)]
               (if (nil? document-id)
                 (async/close! cron)
                 (when-let [_ (<! cron)]
                   (tables/export-local-tables document-id)
                   (recur)))))
           ch)
  :stop (when local-dict-chan
          (async/close! local-dict-chan)))

(defstate global-dict-chan
  ;; throttle the write of the global tables to once every two hours at most
  :start (let [times (rest (chime/periodic-seq (Instant/now) (Duration/ofHours 2)))
               cron (chime-ch times {:ch (async/chan (async/sliding-buffer 1))})
               ch (async/chan (async/sliding-buffer 1))]
           (go-loop []
             (let [v (<! ch)]
               (if (nil? v)
                 (async/close! cron)
                 (when-let [_ (<! cron)]
                   (tables/export-global-tables)
                   (recur)))))
           ch)
  :stop (when global-dict-chan
          (async/close! global-dict-chan)))

(defn export-local-tables [document-id]
  (>!! local-dict-chan document-id))

(defn export-global-tables []
  (>!! global-dict-chan true))
