(ns dp2.words.confirm
  (:require [clojure.string :as string]
            [conman.core :as conman]
            [dp2.db.core :as db]
            [dp2.hyphenate :as hyphenate]
            [dp2.louis :as louis]
            [dp2.words :as words]
            [dp2.words.global :as global]
            [dp2.words.local :as local]))

(defn suggested-hyphenation [{:keys [untranslated spelling]}]
  (when-not (string/includes? untranslated "'")
    (hyphenate/hyphenate untranslated spelling)))

(defn complement-hyphenation [{:keys [hyphenated] :as word}]
  (let [hyphenation (suggested-hyphenation word)]
    (cond-> word
      (and (nil? hyphenated) hyphenation) (assoc :hyphenated hyphenation))))

(defn complement-braille [{:keys [untranslated uncontracted contracted type] :as word}]
  (let [params {:name (words/name? type) :place (words/place? type)}]
    (cond-> word
      (nil? uncontracted) (assoc :uncontracted (louis/translate untranslated (louis/get-tables 1 params)))
      (nil? contracted) (assoc :contracted (louis/translate untranslated (louis/get-tables 2 params))))))

(defn get-words [limit offset]
  (->> (db/get-confirmable-words-aggregated {:limit limit :offset offset})
       (map complement-braille)
       (map complement-hyphenation)))

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

