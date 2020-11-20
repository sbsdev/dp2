(ns dp2.words.confirm
  (:require [clojure.string :as string]
            [conman.core :as conman]
            [dp2.db.core :as db]
            [dp2.hyphenate :as hyphenate]
            [dp2.louis :as louis]
            [dp2.words :as words]
            [dp2.words.global :as global]
            [dp2.words.local :as local]))

(def language-to-spelling
  {"de-1901" 0
   "de" 1})

(defn suggested-hyphenation [{:keys [untranslated spelling]}]
  (when-not (string/includes? untranslated "'")
    (hyphenate/hyphenate untranslated spelling)))

(defn approved-hyphenations [words]
  (->> words
       (remove (fn [word] (string/includes? (:untranslated word) "'")))
       (group-by :spelling)
       (mapcat (fn [[spelling words]]
                 (db/get-hyphenations-in
                  {:words (map :untranslated words)
                   :spelling spelling})))
       (reduce (fn [acc w]
                 (assoc-in acc [(:word w) (:spelling w)] w)) {})))

(defn complement-spelling [{:keys [language] :as word}]
  (let [spelling (language-to-spelling language)]
    (cond-> word
      spelling (assoc :spelling spelling))))

(defn complement-approved-hyphenation [{:keys [untranslated spelling] :as word} approved]
  (let [hyphenation (get-in approved [untranslated spelling])]
    (cond-> word
      hyphenation (assoc :hyphenated hyphenation))))

(defn complement-hyphenation [{:keys [hyphenated] :as word}]
  (let [hyphenation (suggested-hyphenation word)]
    (cond-> word
      (and (nil? hyphenated) hyphenation) (assoc :hyphenated hyphenation))))

(defn complement-braille [{:keys [untranslated grade1 grade2 type] :as word}]
  (let [params {:name (words/name? type) :place (words/place? type)}]
    (cond-> word
      (nil? grade1) (assoc :grade1 (louis/translate untranslated (louis/get-tables 1 params)))
      (nil? grade2) (assoc :grade2 (louis/translate untranslated (louis/get-tables 2 params))))))

(defn get-words []
  (let [words (->> (db/get-confirmable-words)
                   words/aggregate
                   (map complement-braille)
                   (map complement-spelling))
        approved-hyphenations (approved-hyphenations words)]
    (->> words
         ;; first add approved hyphenations
         (map #(complement-approved-hyphenation % approved-hyphenations))
         ;; then add generated hyphenations
         (map complement-hyphenation))))

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

