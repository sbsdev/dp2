(ns dp2.words.local
  (:require [dp2.db.core :as db]
            [dp2.hyphenate :as hyphenate]
            [dp2.words :as words]
            [dp2.whitelists.async :as whitelists]
            [clojure.tools.logging :as log]))

(defn get-words [id grade limit offset]
  (let [document (db/get-document {:id id})
        spelling (:spelling document)
        words (if (= grade 0)
                (db/get-local-words-aggregated {:id id :limit limit :offset offset})
                (db/get-local-words {:id id :grade grade :limit limit :offset offset}))]
    (->> words
         (map words/islocal-to-boolean)
         (map words/complement-hyphenation))))

(defn put-word
  "Persist a `word` in the db. Upsert all braille translations and the
  hyphenation. Returns the number of insertions/updates."
  [word]
  (log/debug "Add local word" word)
  (when (:hyphenated word)
    (db/insert-hyphenation
     (words/to-db word words/hyphenation-keys words/hyphenation-mapping)))
  (let [insertions
        (->> word
             words/separate-word
             (map #(db/insert-local-word
                    (words/to-db % words/dictionary-keys words/dictionary-mapping)))
             (reduce +))]
    (whitelists/export-local-tables (:document-id word))
    insertions))

(defn delete-word
  "Remove a `word` from the db. If the word contains `:uncontracted`
  remove the db record for `:grade` 1 and likewise if the word
  contains `:contracted`. If there are no more braille translations
  for this word then it is also removed from the hyphenations db.
  Returns the number of deletions."
  [word]
  (log/debug "Delete local word" word)
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
    (when (:hyphenated word)
      (db/delete-hyphenation
       (words/to-db word words/hyphenation-keys words/hyphenation-mapping)))
    (whitelists/export-local-tables (:document-id word))
    deletions))
