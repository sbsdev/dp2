(ns dp2.words.confirm
  (:require
   [clojure.set :refer [rename-keys]]
   [clojure.string :as string]
   [dp2.db.core :as db]
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

(defn get-words []
  (let [words (->> (db/get-confirmable-words) words/aggregate)
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

