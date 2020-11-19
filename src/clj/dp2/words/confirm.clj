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

(defn complement-braille [{:keys [untranslated grade1 grade2 type] :as word}]
  (let [params {:name (words/name? type) :place (words/place? type)}
        tables {:grade1 (louis/get-tables 1 params)
                :grade2 (louis/get-tables 2 params)}]
    (cond-> word
      (nil? grade1) (assoc :grade1 (louis/translate untranslated (:grade1 tables)))
      (nil? grade2) (assoc :grade2 (louis/translate untranslated (:grade2 tables))))))

(defn get-words []
  (let [words (->> (db/get-confirmable-words)
                   words/aggregate
                   (map complement-braille))
        suggested-hyphenations (->> words (map suggested-hyphenation))
        approved-hyphenations (approved-hyphenations words)]
    (map (fn [{:keys [untranslated language] :as word} suggested]
           ;; add the hyphenation to the word. If no hyphenation is
           ;; given in the hyphenation database, i.e. in the approved
           ;; hyphenations then just use the suggestion given by
           ;; libhyphen
           (assoc word :hyphenated
                  (or (get-in approved-hyphenations [untranslated language])
                      suggested)))
           words suggested-hyphenations)))

