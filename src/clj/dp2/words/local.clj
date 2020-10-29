(ns dp2.words.local
  (:require
   [clojure.set :refer [rename-keys]]
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
           (assoc word
                  :hyphenated (or (get approved-hyphenations word) suggested)
                  :spelling spelling))
         words suggested-hyphenations)))

(def to-dictionary-db
  {:homograph-disambiguation :homograph_disambiguation
   :document-id :document_id})

(def to-hyphenation-db
  {:untranslated :word
   :hyphenated :hyphenation})

(defn put-word [word]
  (db/insert-local-word
   (-> word
       (select-keys [:untranslated :braille :type :grade
                     :homograph-disambiguation :document-id :islocal])
       (rename-keys to-dictionary-db)))
  (db/insert-hyphenation
   (-> word
       (select-keys [:untranslated :hyphenated :spelling])
       (rename-keys to-hyphenation-db))))
