(ns dp2.words.global
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dp2.db.core :as db]
            [dp2.whitelists.async :as whitelists]
            [dp2.words :as words]))

(defn get-words [{:keys [untranslated limit offset]}]
  (-> (db/find-global-words {:untranslated (if (string/blank? untranslated) "%" untranslated)
                             :limit limit :offset offset})))

(def dictionary-keys [:untranslated :braille :type :grade :homograph-disambiguation])

(defn put-word [word]
  (log/debug "Add global word" word)
  (let [insertions
        (->> word
             words/separate-word
             (map #(db/insert-global-word (words/to-db % dictionary-keys words/dictionary-mapping)))
             (reduce +))]
    (whitelists/export-global-tables)
    insertions))

(defn delete-word [word]
  (log/debug "Delete global word" word)
  (let [deletions
        (db/delete-global-word
         (words/to-db word
                      [:untranslated :type :homograph-disambiguation]
                      words/dictionary-mapping))]
    (whitelists/export-global-tables)
    deletions))
