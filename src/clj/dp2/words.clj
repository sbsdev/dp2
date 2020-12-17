(ns dp2.words
  (:require [clojure.set :refer [rename-keys]]
            [clojure.string :as string]
            [dp2.hyphenate :as hyphenate]
            [dp2.louis :as louis]
            [dp2.validation :as validation]))

(defn name? [type] (#{1 2} type))
(defn place? [type] (#{3 4} type))

(defn suggested-hyphenation [{:keys [untranslated spelling]}]
  (when (re-matches validation/valid-hyphenation-re untranslated)
    (hyphenate/hyphenate untranslated spelling)))

(defn complement-hyphenation [{:keys [hyphenated] :as word}]
  (let [hyphenation (suggested-hyphenation word)]
    (cond-> word
      (and (nil? hyphenated) hyphenation) (assoc :hyphenated hyphenation))))

(def braille-dummy-text "┊")

(defn complement-ellipsis-braille
  [{:keys [untranslated uncontracted contracted] :as word}]
  (cond-> word
    (string/starts-with? untranslated braille-dummy-text)
    (assoc :uncontracted (str braille-dummy-text uncontracted)
           :contracted (str braille-dummy-text contracted))
    (string/ends-with? untranslated braille-dummy-text)
    (assoc :uncontracted (str uncontracted braille-dummy-text)
           :contracted (str contracted braille-dummy-text))))

(defn complement-braille
  [{:keys [untranslated uncontracted contracted type homograph-disambiguation] :as word}]
  (let [params {:name (name? type) :place (place? type)}
        ;; for homographs we have to use the homograph-disambiguation
        ;; to get the braille
        untranslated (if (string/blank? homograph-disambiguation)
                       untranslated
                       (string/replace homograph-disambiguation "|" braille-dummy-text))]
    (cond-> word
      (nil? uncontracted)
      (assoc :uncontracted (louis/translate untranslated (louis/get-tables 1 params)))
      (nil? contracted)
      (assoc :contracted (louis/translate untranslated (louis/get-tables 2 params))))))

(defn grades [grade]
  (case grade ; convert grade into a list of grades
    (1 2) [grade] ; for grade 1 and 2 the list contains just that grade
    0 [1 2])) ; grade 0 really means both grades

(def grade-to-keyword
  {1 :uncontracted
   2 :contracted})

(defn separate-word
  "Separate a `word` into multiple individual word maps where the
  original `:uncontracted` and `:contracted` keys are mapped to
  `:grade` and `:braille` values in each of the new maps."
  [word]
  (->>
   (map (fn [w grade]
          (let [k (get grade-to-keyword grade)
                braille (get w k)]
            (-> w
                (assoc :braille braille)
                (assoc :grade grade)
                (dissoc :uncontracted :contracted))))
        (repeat word) [1 2])
   (filter #(:braille %))))

(defn merge-words
  "Merge a seq of `words` into one. Presumably the words are for
  different grades. All keys are merged as usual except for `:braille`
  which will be merged to `:uncontracted` or `:contracted`, depending
  on the grade of the original words."
  [words]
  (reduce (fn [m {:keys [grade uncontracted contracted islocal] :as word}]
            (-> m
                ;; Merge everything but grade, braille and islocal.
                ;; These will be added separately
                (merge (dissoc word :grade :braille :islocal))
                (assoc (get grade-to-keyword grade)
                       (case grade 1 uncontracted 2 contracted))
                ;; we assume that if any of the grades are local then
                ;; the whole word is local
                (update :islocal #(or %1 %2) islocal)))
          {:contracted nil :uncontracted nil}
          words))

(defn aggregate
  "Given a seq of `words` with distinct entries for each `:untranslated`
  and `:grade,` aggregate them into a seq with distinct entries for
  each `:untranslated` but aggregate the grades into that one entry.
  So
  `[{:untranslated \"foo\" :grade 1 :braille \"FOO\"} {:untranslated \"foo\" :grade 2 :braille \"F4\"}]`
  becomes
  [{:untranslated \"foo\" :uncontracted \"FOO\" :contracted \"F4\"]"
  [words]
  (->> words
       (group-by (juxt :untranslated :type :homograph-disambiguation))
       vals
       (map merge-words)))

(def hyphenation-keys [:untranslated :hyphenated :spelling])

(def hyphenation-mapping {:untranslated :word
                          :hyphenated :hyphenation})

(def dictionary-keys [:untranslated :braille :type :grade :homograph-disambiguation
                      :document-id :islocal :isconfirmed])

(def dictionary-mapping {:homograph-disambiguation :homograph_disambiguation
                         :document-id :document_id})

(defn to-db [word keys mapping]
  (-> word
      (select-keys keys)
      (rename-keys mapping)))

