(ns dp2.words.confirm
  (:require [conman.core :as conman]
            [dp2.db.core :as db]
            [dp2.words :as words]
            [dp2.words.global :as global]
            [dp2.words.local :as local]))

(defn get-words [limit offset]
  (->> (db/get-confirmable-words-aggregated {:limit limit :offset offset})
       (map words/islocal-to-boolean)
       (map words/complement-braille)
       (map words/complement-ellipsis-braille)
       (map words/complement-hyphenation)))

(defn put-word [word]
  (if (:islocal word)
    ;; if a word is local then just save it in the local db with
    ;; confirmed = true
    (-> word (assoc :isconfirmed true) (local/put-word))
    ;; otherwise move the word to the global dict
    (conman/with-transaction [db/*db*]
      ;; drop the hyphenation, otherwise the hyphenation is removed
      ;; and right after added again
      (let [word (dissoc word :hyphenated)]
        (local/delete-word word)
        (global/put-word word)))))

