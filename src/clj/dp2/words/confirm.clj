(ns dp2.words.confirm
  (:require [conman.core :as conman]
            [dp2.db.core :as db]
            [dp2.words :as words]
            [dp2.words.global :as global]
            [dp2.words.local :as local]))

(defn get-words [limit offset]
  (->> (db/get-confirmable-words-aggregated {:limit limit :offset offset})
       (map words/complement-braille)
       (map words/complement-hyphenation)))

(defn put-word [word]
  (cond (:islocal word)
        ;; if a word is local then just save it in the local db with
        ;; confirmed = true
        (-> word (assoc :isconfirmed true) (local/put-word))
        :else
        ;; otherwise move the word to the global dict
        (conman/with-transaction [db/*db*]
          (local/delete-word word)
          (db/insert-hyphenation
           (words/to-db word words/hyphenation-keys words/hyphenation-mapping))
          (global/put-word word))))

