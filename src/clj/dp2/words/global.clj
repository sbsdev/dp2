(ns dp2.words.global
  (:require
   [clojure.set :refer [rename-keys]]
   [dp2.db.core :as db]
   [dp2.hyphenate :as hyphenate]
   [dp2.words :as words]
   [clojure.string :as string]))

(defn get-words [{:keys [untranslated grade type limit offset]
                  :or {limit 200 offset 0}}]
  (-> (db/find-global-words {:untranslated (if (string/blank? untranslated) "%" untranslated)
                             :grade grade :type type
                             :limit limit :offset offset})
      words/aggregate))

(def dictionary-keys [:untranslated :braille :type :grade :homograph-disambiguation])

(defn put-word [word]
  (->> word
       words/separate-word
       (map #(db/insert-global-word (words/to-db % dictionary-keys words/dictionary-mapping)))
       (reduce +)))

(defn delete-word [word]
  (db/delete-global-word
   (words/to-db word [:untranslated :type :homograph-disambiguation] words/dictionary-mapping)))
