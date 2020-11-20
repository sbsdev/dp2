(ns dp2.words.local
  (:require [dp2.db.core :as db]
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

(defn put-word
  "Persist a `word` in the db. Upsert all braille translations and the
  hyphenation. Returns the number of insertions/updates."
  [word]
  (db/insert-hyphenation
   (words/to-db word words/hyphenation-keys words/hyphenation-mapping))
  (->> word
       words/separate-word
       (map #(db/insert-local-word
              (words/to-db % words/dictionary-keys words/dictionary-mapping)))
       (reduce +)))

(defn- ref-count
  "Return the number of translations for a given `word` in a given document."
  [{id :document-id untranslated :untranslated}]
  (-> (db/get-local-word-count {:id id :untranslated untranslated})
      vals first))

(defn delete-word
  "Remove a `word` from the db. If the word contains `:grade1` remove
  the db record for `:grade` 1 and likewise if the word contains
  `:grade2`. If there are no more braille translations for this word
  then it is also removed from the hyphenations db. Returns the number
  of deletions."
  [word]
  (let [deletions
        (->> word
             words/separate-word
             (map #(db/delete-local-word
                    (words/to-db % words/dictionary-keys words/dictionary-mapping)))
             (reduce +))]
    ;; delete the hyphenation for this word only if there is no other
    ;; braille entry for it (we can have multiple entries for a word
    ;; in the braille db (for the two grades, for names etc) but we
    ;; only have one entry, per spelling in the hyphenation db)
    (when (= (ref-count word) 0)
      (db/delete-hyphenation
       (words/to-db word words/hyphenation-keys words/hyphenation-mapping)))
    deletions))
