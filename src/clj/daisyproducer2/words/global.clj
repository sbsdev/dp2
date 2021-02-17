(ns daisyproducer2.words.global
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [daisyproducer2.db.core :as db]
            [daisyproducer2.whitelists.hyphenation :as hyphenations]
            [daisyproducer2.words :as words]))

(defn get-words [{:keys [untranslated limit offset]}]
  (-> (db/find-global-words {:untranslated (db/search-to-sql untranslated)
                             :limit limit :offset offset})))

(def dictionary-keys [:untranslated :braille :type :grade :homograph-disambiguation])

(defn put-word [word]
  (log/debug "Add global word" word)
  (when (:hyphenated word)
    (db/insert-hyphenation
     (words/to-db word words/hyphenation-keys words/hyphenation-mapping))
    (hyphenations/export))
  (->> word
       words/separate-word
       (map #(db/insert-global-word (words/to-db % dictionary-keys words/dictionary-mapping)))
       (reduce +)))

(defn delete-word [word]
  (log/debug "Delete global word" word)
  (let [deletions
        (db/delete-global-word
         (words/to-db word [:untranslated :type :homograph-disambiguation] words/dictionary-mapping))]

    (when (:hyphenated word)
      (db/insert-hyphenation
       (words/to-db word words/hyphenation-keys words/hyphenation-mapping))
      (hyphenations/export))
    deletions))
