(ns daisyproducer2.words.global
  (:require [clojure.tools.logging :as log]
            [daisyproducer2.db.core :as db]
            [daisyproducer2.metrics :as metrics]
            [daisyproducer2.whitelists.hyphenation :as hyphenations]
            [daisyproducer2.words :as words]
            [iapetos.collector.fn :as prometheus]))

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
  (db/insert-global-word (words/to-db word dictionary-keys words/dictionary-mapping)))

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

(prometheus/instrument! metrics/registry #'get-words)
(prometheus/instrument! metrics/registry #'put-word)
(prometheus/instrument! metrics/registry #'delete-word)
