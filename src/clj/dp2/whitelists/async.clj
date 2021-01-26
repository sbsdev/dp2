(ns dp2.whitelists.async
  "Asynchronous interface to writing whitelist tables. Refer
  to [[dp2.whitelists.tables]] for the synchronous API."
  (:require [clojure.core.async :as async
             :refer [<! >! >!! alts! buffer chan close! go go-loop timeout]]
            [dp2.whitelists.tables :as tables]
            [mount.core :refer [defstate]]))

(def local-clock-interval
  "Time interval at which writes to the local tables happen in miliseconds"
  (* 10 1000)) ; every 10 seconds

(def global-clock-interval
  "Time interval at which writes to the global tables happen in miliseconds"
  (* 5 60 1000)) ; every 15 minutes

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

(defstate global-dict-chan
  :start (let [input-ch (chan (async/dropping-buffer 1))
               clock-ch (chan (async/dropping-buffer 1))]

           ;; throttle the write of the global tables to once every 15
           ;; minutes at most

           ;; The clock thread.
           (go (while (>! clock-ch :token)
                 (<! (timeout global-clock-interval))))

           ;; the actual worker thread that writes the tables
           (go
             (while (<! clock-ch)
               (let [v (<! input-ch)]
                 (if (some? v)
                   (tables/export-global-tables)
                   (close! clock-ch)))))

           input-ch)
  :stop (when global-dict-chan
          (close! global-dict-chan)))

(defn export-local-tables [document-id]
  (>!! local-dict-chan document-id))

(defn export-global-tables []
  (>!! global-dict-chan true))
