(ns dp2.whitelists.async
  "Asynchronous interface to writing whitelist tables. Refer
  to [[dp2.whitelists.tables]] for the synchronous API."
  (:require [chime.core :as chime]
            [chime.core-async :refer [chime-ch]]
            [clojure.core.async :as async :refer [<! >! >!! go go-loop]]
            [dp2.whitelists.tables :as tables]
            [mount.core :refer [defstate]])
  (:import [java.time Duration Instant]))

(def clock-interval
  "Time interval at which writes to the local tables happen in miliseconds"
  (* 10 1000)) ; every 10 seconds

(defstate local-dict-chan
  :start (let [input-ch (async/chan (async/buffer 20))
               clock-ch (async/chan (async/dropping-buffer 1))
               write-ch (async/chan (async/buffer 20))]

           ;; throttle the write of the local tables to once every two
           ;; minutes at most by using a clock channel to trigger the
           ;; write

           ;; The clock thread. Puts a token in the clock channel
           ;; every 2 minutes. If the bucket is full the token is
           ;; dropped since the bucket channel uses a dropping buffer.
           (go (while (>! clock-ch :token)
                 (<! (async/timeout clock-interval))))

           ;; the actual worker thread that writes the tables
           (go (loop []
                 (when-let [document-id (<! write-ch)]
                   (tables/export-local-tables document-id)
                   (recur))))

           (go-loop [document-ids #{}]
             ;; listen to both the clock and the input channel
             (let [[v ch] (async/alts! [input-ch clock-ch])]
               (condp = ch
                 ;; if we get a document-id from the input channel, we
                 ;; just add it to the set of ids to export. This also
                 ;; depupes the document-ids
                 input-ch (if (some? v)
                            (recur (conj document-ids v))
                            (do
                              (async/close! clock-ch)
                              (async/close! write-ch)))
                 ;; at regular intervals we get an event from the
                 ;; clock channel. Then it's time to actually do the
                 ;; export
                 clock-ch (do
                            (doseq [id document-ids]
                              (>! write-ch id))
                            (recur #{})))))
           input-ch)
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
