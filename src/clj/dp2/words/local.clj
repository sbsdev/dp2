(ns dp2.words.local
  (:require
   [dp2.db.core :as db]
   [dp2.hyphenate :as hyphenate]
   [dp2.words :as words]))

(defn get-words [id grade]
  (let [document (db/get-document {:id id})
        spelling (words/spelling (:language document))
        words (db/get-local-words {:id id :grade grade})
        untranslated (map :untranslated words)
        approved-hyphenations (->>
                               (db/get-hyphenations-in
                                {:spelling spelling :words untranslated})
                               (map (juxt :word :hyphenation))
                               (into {}))
        suggested-hyphenations (map (fn [word] (hyphenate/hyphenate word spelling)) untranslated)]
    (map (fn [word suggested]
           ;; add the hyphenation to the word. If no hyphenation is
           ;; given in the hyphenation database, i.e. in the approved
           ;; hyphenations then just use the suggestion given by
           ;; libhyphen
           (assoc word :hyphenated (or (get approved-hyphenations word) suggested)))
         words suggested-hyphenations)))
