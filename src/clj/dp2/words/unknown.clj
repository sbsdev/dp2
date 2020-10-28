(ns dp2.words.unknown
  (:require
   [dp2.db.core :as db]
   [clojure.set :refer [difference]]
   [clojure.string :as string]
   [sigel.xslt.core :as xslt]
   [sigel.xpath.core :as xpath]
   [dp2.louis :as louis]
   [dp2.words :as words]
   [dp2.hyphenate :as hyphenate]))

(def compiler
  (-> (xpath/compiler)
      (xpath/set-default-namespace! "http://www.daisy.org/z3986/2005/dtbook/")
      (xpath/declare-namespace! "brl" "http://www.daisy.org/z3986/2009/braille/")))

(defn name? [type] (#{1 2} type))
(defn place? [type] (#{3 4} type))

(defn compare-with-known-words
  "Given a set of `words` return the ones that are unknown."
  ([words document-id grade]
   (compare-with-known-words words document-id grade db/get-all-known-words))
  ([words document-id grade query-fn]
   (if (empty? words)
     ;; if there are no words then none of them can be unknown.
     ; The db query doesn't like an empty word list
     #{}
     (let [known-words
           (->>
            (query-fn {:document_id document-id :grade grade :words words})
            (map :untranslated)
            set)]
       (difference words known-words)))))

(defn compare-with-known-homographs
  "Given a set of `homographs` return the ones that are unknown."
  [homographs document-id grade]
  (compare-with-known-words homographs document-id grade db/get-all-known-homographs))

(defn compare-with-known-names
  "Given a set of `names` return the ones that are unknown."
  [names document-id grade]
  (compare-with-known-words names document-id grade db/get-all-known-names))

(defn compare-with-known-places
  "Given a set of `places` return the ones that are unknown."
  [places document-id grade]
  (compare-with-known-words places document-id grade db/get-all-known-places))

(defn filter-braille
  [xml]
  (xslt/transform (xslt/compile-xslt "resources/xslt/filter.xsl") xml))

(defn filter-braille-and-names
  [xml]
  (let [xslt [(xslt/compile-xslt "resources/xslt/filter.xsl")
              (xslt/compile-xslt "resources/xslt/filter_names.xsl")
              (xslt/compile-xslt "resources/xslt/to_string.xsl")]]
    (xslt/transform xslt xml)))

(def valid-character-types
  #{Character/LOWERCASE_LETTER Character/UPPERCASE_LETTER
    Character/SPACE_SEPARATOR Character/LINE_SEPARATOR Character/PARAGRAPH_SEPARATOR
    Character/DASH_PUNCTUATION Character/OTHER_PUNCTUATION})

(defn- valid-char? [char]
  (or (contains? valid-character-types (Character/getType ^Character char))
      (contains? #{\newline \return} char)))

(defn filter-text [text]
  (->>
   (sequence text)
   ;; drop everything that is not a letter, space or punctuation
   (filter valid-char?)
   (string/join)))

(defn- extract-xpath [xml xpath]
  (->>
   (xpath/select compiler xml xpath {})
   (map (comp str string/lower-case))
   set))

(defn extract-homographs [xml]
  (extract-xpath xml "for $h in (//brl:homograph) return string-join($h/text(),'|')"))

(defn extract-names [xml]
  (extract-xpath xml "//brl:name/text()"))

(defn extract-places [xml]
  (extract-xpath xml "//brl:place/text()"))

(defn extract-words [text]
  (->>
   (string/split (filter-text text) #"(?U)\W")
   ;; drop words shorter than 3 chars
   (remove (fn [word] (< (count word) 3)))
   (map string/lower-case)
   set))

(defn embellish-words [words document-id grade type spelling]
  (let [template {:document-id document-id
                  :type type
                  :grade grade
                  :homograph-disambiguation ""
                  :spelling spelling}
        tables (louis/get-tables grade {:name (name? type) :place (place? type)})
        brailles (map #(louis/translate % tables) words)
        hyphenations (map #(hyphenate/hyphenate % spelling) words)]
    (map (fn [untranslated braille hyphenated]
           (assoc template
                  :untranslated untranslated
                  :braille braille
                  :hyphenated hyphenated))
         words brailles hyphenations)))

(defn embellish-homograph [words document-id grade type spelling]
  (let [template {:document-id document-id
                  :type type
                  :grade grade}
        untranslated (map #(string/replace % "|" "") words)
        hyphenations (map #(hyphenate/hyphenate % spelling) untranslated)
        tables (louis/get-tables grade)
        brailles (map #(louis/translate (string/replace % "|" "┊") tables) words)]

    (map (fn [untranslated braille hyphenated homograph]
           (assoc template
                  :untranslated untranslated
                  :braille braille
                  :hyphenated hyphenated
                  :homograph-disambiguation homograph))
         untranslated brailles hyphenations words)))

(defn get-names
  [xml document-id grade spelling]
  (-> (filter-braille xml)
      extract-names
      (compare-with-known-names document-id grade)
      (embellish-words document-id grade 1 spelling)))

(defn get-places
  [xml document-id grade spelling]
  (-> (filter-braille xml)
      extract-places
      (compare-with-known-places document-id grade)
      (embellish-words document-id grade 3 spelling)))

(defn get-homographs
  [xml document-id grade spelling]
  (-> (filter-braille xml)
      extract-homographs
      (compare-with-known-homographs document-id grade)
      (embellish-homograph document-id grade 5 spelling)))

(defn get-plain
  [xml document-id grade spelling]
  (-> (filter-braille-and-names xml)
      str
      extract-words
      (compare-with-known-words document-id grade)
      (embellish-words document-id grade 0 spelling)))

(defn get-words
  [xml document-id grade]
  (let [document (db/get-document {:id document-id})
        language (:language document)
        spelling (words/spelling language)]
    (sort-by
     :untranslated
     (concat
      (get-names xml document-id grade spelling)
      (get-places xml document-id grade spelling)
      (get-homographs xml document-id grade spelling)
      (get-plain xml document-id grade spelling)))))

(comment
  ;; extract words from an dtbook file
  (require '[clojure.java.io :as io])
  
  (let [f (io/file "/home/eglic/tmp/6747.xml")
        content (str (filter-braille-and-names f))]
    (extract-words content))
  
  ;; extract unknown words from an dtbook file
  (let [f (io/file "/home/eglic/tmp/6747.xml")
        content (str (filter-braille-and-names f))
        words (extract-words content)
        document-id 644
        grade 2]
    (compare-with-known-words words document-id grade))
  
  )
