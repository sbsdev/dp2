(ns dp2.words.local
  (:require
   [clojure.set :refer [rename-keys]]
   [dp2.db.core :as db]
   [dp2.hyphenate :as hyphenate]
   [dp2.words :as words]))

(defn get-words [id grade]
  (let [document (db/get-document {:id id})
        spelling (words/spelling (:language document))
        grades (words/grades grade)
        words (mapcat #(db/get-local-words {:id id :grade %}) grades)
        words (words/aggregate words)
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

(defn- to-db [word keys mapping]
  (-> word
      (select-keys keys)
      (rename-keys mapping)))

(def dictionary-keys [:untranslated :braille :type :grade :homograph-disambiguation
                      :document-id :islocal])
(def dictionary-mapping {:homograph-disambiguation :homograph_disambiguation
                         :document-id :document_id})
(def hyphenation-keys [:untranslated :hyphenated :spelling])
(def hyphenation-mapping {:untranslated :word
                          :hyphenated :hyphenation})

(defn put-word [word]
  (db/insert-local-word
   (to-db word dictionary-keys dictionary-mapping))
  (db/insert-hyphenation
   (to-db word hyphenation-keys hyphenation-mapping)))

(defn delete-word [word]
  ;; delete the hyphenation for this word only if there is no other
  ;; braille entry for it (we can have multiple entries for a word in
  ;; the braille db (for the two grades, for names etc) but we only
  ;; have one entry, per spelling in the hyphenation db)
  (let [{id :document-id untranslated :untranslated} word
        ref-count (-> {:id id :untranslated untranslated}
                      db/get-local-word-count
                      vals first)]
    (when (= ref-count 0)
      (db/delete-hyphenation (to-db word hyphenation-keys hyphenation-mapping))))
  (db/delete-local-word
   (to-db word dictionary-keys dictionary-mapping)))
