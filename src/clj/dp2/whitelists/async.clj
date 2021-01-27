(ns dp2.whitelists.async
  "Asynchronous interface to writing whitelist tables. Refer
  to [[dp2.whitelists.tables]] for the synchronous API."
  (:require [chime.core :as chime]
            [clojure.core.async :as async
             :refer [<! >! >!! alts! buffer chan close! go go-loop timeout]]
            [dp2.whitelists.tables :as tables]
            [mount.core :refer [defstate]])
  (:import [java.time LocalTime Period ZonedDateTime ZoneId]))

(def local-clock-interval
  "Time interval at which writes to the local tables happen in miliseconds"
  (* 10 1000)) ; every 10 seconds

(defstate local-dict-chan
  :start (let [input-ch (chan (buffer 20))
               clock-ch (chan (async/dropping-buffer 1))
               write-ch (chan (buffer 20))]

           ;; throttle the write of the local tables to once every two
           ;; minutes at most by using a clock channel to trigger the
           ;; write

           ;; The clock thread. Puts a token in the clock channel
           ;; every 2 minutes. If the bucket is full the token is
           ;; dropped since the bucket channel uses a dropping buffer.
           (go (while (>! clock-ch :token)
                 (<! (timeout local-clock-interval))))

           ;; the actual worker thread that writes the tables
           (go (loop []
                 (when-let [document-id (<! write-ch)]
                   (tables/export-local-tables document-id)
                   (recur))))

           (go-loop [document-ids #{}]
             ;; listen to both the clock and the input channel
             (let [[v ch] (alts! [input-ch clock-ch])]
               (condp = ch
                 ;; if we get a document-id from the input channel, we
                 ;; just add it to the set of ids to export. This also
                 ;; depupes the document-ids
                 input-ch (if (some? v)
                            (recur (conj document-ids v))
                            (doseq [c [clock-ch write-ch]] (close! c)))
                 ;; at regular intervals we get an event from the
                 ;; clock channel. Then it's time to actually do the
                 ;; export
                 clock-ch (do (doseq [id document-ids] (>! write-ch id))
                              (recur #{})))))
           input-ch)
  :stop (when local-dict-chan
          (close! local-dict-chan)))

(defstate global-dict-cron
  ;; write the global tables every day at 21:00
  :start (chime/chime-at
          (chime/periodic-seq
           (-> (LocalTime/of 21 0 0)
               (.adjustInto (ZonedDateTime/now (ZoneId/of "UTC")))
               .toInstant)
           (Period/ofDays 1))
          (fn [_] (tables/export-global-tables)))
  :stop (when global-dict-cron
          (.close global-dict-cron)))

(defn export-local-tables [document-id]
  (>!! local-dict-chan document-id))

