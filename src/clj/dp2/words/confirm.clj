(ns dp2.words.confirm
  (:require
   [clojure.set :refer [rename-keys]]
   [clojure.string :as string]
   [dp2.db.core :as db]
   [dp2.louis :as louis]
   [dp2.hyphenate :as hyphenate]
   [dp2.words :as words]))

(def language-to-spelling
  {"de-1901" 0
   "de" 1})

(defn suggested-hyphenation [{:keys [untranslated language]}]
  (when-not (string/includes? untranslated "'")
    (hyphenate/hyphenate untranslated (language-to-spelling language))))

(defn approved-hyphenations [words]
  (->> words
       (remove (fn [word] (string/includes? (:untranslated word) "'")))
       (group-by :language)
       (mapcat (fn [[language words]]
                 (db/get-hyphenations-in
                  {:words (map :untranslated words)
                   :spelling (language-to-spelling language)})))
       (reduce (fn [acc w]
                 (assoc-in acc [(:spelling w) (:word w)] w)) {})))

(defn complement-approved-hyphenation [approved {:keys [untranslated language] :as word}]
  (let [hyphenation (get-in approved [untranslated language])]
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
                   (map complement-braille))
        approved-hyphenations (approved-hyphenations words)]
    (->> words
         ;; first add approved hyphenations
         (map (partial complement-approved-hyphenation approved-hyphenations))
         ;; then add generated hyphenations
         (map complement-hyphenation))))

