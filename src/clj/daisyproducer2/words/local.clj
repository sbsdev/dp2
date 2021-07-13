(ns daisyproducer2.words.local
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [daisyproducer2.db.core :as db]
            [daisyproducer2.metrics :as metrics]
            [daisyproducer2.whitelists.async :as whitelists]
            [daisyproducer2.whitelists.hyphenation :as hyphenations]
            [daisyproducer2.words :as words]
            [iapetos.collector.fn :as prometheus]))

(defn get-words
  "Retrieve all local words for given document-id `id`, `grade` and
  a (possibly nil) `search` term. Limit the result set by `limit` and
  `offset`."
  [id grade search limit offset]
  (let [document (db/get-document {:id id})
        spelling (:spelling document)
        params (cond-> {:id id :limit limit :offset offset}
                 (not (string/blank? search)) (assoc :search (db/search-to-sql search)))
        words (db/get-local-words params)]
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
     (words/to-db word words/hyphenation-keys words/hyphenation-mapping))
    (hyphenations/export))
  (let [insertions
        (db/insert-local-word
         (words/to-db word words/dictionary-keys words/dictionary-mapping))]
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
        (db/delete-local-word
         (words/to-db word words/dictionary-keys words/dictionary-mapping))]
    (when (:hyphenated word)
      (db/delete-hyphenation
       (words/to-db word words/hyphenation-keys words/hyphenation-mapping))
      (hyphenations/export))
    (whitelists/export-local-tables (:document-id word))
    deletions))

(prometheus/instrument! metrics/registry #'get-words)
(prometheus/instrument! metrics/registry #'put-word)
(prometheus/instrument! metrics/registry #'delete-word)
