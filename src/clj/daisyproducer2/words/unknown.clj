(ns daisyproducer2.words.unknown
  (:require [clojure.java.io :as io]
            [clojure.set :refer [union]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [daisyproducer2.db.core :as db]
            [daisyproducer2.words :as words]
            [sigel.xpath.core :as xpath]
            [sigel.xslt.core :as xslt]))

(def compiler
  (-> (xpath/compiler)
      (xpath/set-default-namespace! "http://www.daisy.org/z3986/2005/dtbook/")
      (xpath/declare-namespace! "brl" "http://www.daisy.org/z3986/2009/braille/")))

(defn filter-braille
  [xml]
  (xslt/transform (xslt/compile-xslt (io/resource "xslt/filter.xsl")) xml))

(defn filter-braille-and-names
  [xml]
  (let [xslt [(xslt/compile-xslt (io/resource "xslt/filter.xsl"))
              (xslt/compile-xslt (io/resource "xslt/filter_names.xsl"))
              (xslt/compile-xslt (io/resource "xslt/to_string.xsl"))]]
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
   (map #(if (valid-char? %) % " "))
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

(def ellipsis-re #"(?U)\.{3}[\p{Alpha}']{2,}|[\p{Alpha}']{2,}\.{3}")

(defn extract-re [xml re to-replace]
  (->> xml
   str
   (re-seq re)
   (map string/lower-case)
   (map #(string/replace % to-replace words/braille-dummy-text))
   set))

(defn extract-ellipsis-words [xml]
  (extract-re xml ellipsis-re "..."))

(def supplement-hyphen-re #"(?U)\B-[\w']{2,}|[\w']{2,}-\B")

(defn extract-hyphen-words [xml]
  (extract-re xml supplement-hyphen-re "-"))

(defn extract-special-words [xml]
  (union
   (extract-ellipsis-words xml)
   (extract-hyphen-words xml)))

(defn filter-special-words [text]
  (-> text
      (string/replace ellipsis-re "")
      (string/replace supplement-hyphen-re "")))

(defn extract-words [xml]
  (-> xml
   str
   filter-special-words
   filter-text
   (string/split #"(?U)[^\w']")
   (->>
    ;; drop words shorter than 3 chars
    (remove (fn [word] (< (count word) 3)))
    (map string/lower-case)
    set)))

(defn get-names
  [xml document-id]
  (let [words (-> xml filter-braille extract-names)
        tuples (map (fn [w] [w 2 "" document-id]) words)]
    tuples))

(defn get-places
  [xml document-id]
  (let [words (-> xml filter-braille extract-places)
        tuples (map (fn [w] [w 4 "" document-id]) words)]
    tuples))

(defn get-homographs
  [xml document-id]
  (let [words (-> xml filter-braille extract-homographs)
        tuples (map (fn [w] [(string/replace w "|" "") 5 w document-id]) words)]
    tuples))

(defn get-plain
  [xml document-id]
  (let [filtered (-> xml filter-braille-and-names)
        special-words (-> filtered extract-special-words) ; ellipsis and hyphen
        plain-words (-> filtered extract-words)
        all-words (union plain-words special-words)
        tuples (map (fn [w] [w 0 "" document-id]) all-words)]
    tuples))

(defn get-words
  [xml document-id grade limit offset]
  (db/delete-unknown-words)
  (db/insert-unknown-words
   {:words (concat
            (get-names xml document-id)
            (get-places xml document-id)
            (get-homographs xml document-id)
            (get-plain xml document-id))})
  (when (= offset 0)
    (let [deleted (db/delete-non-existing-unknown-words-from-local-words {:document-id document-id})]
      (log/infof "Deleted %s local words that were not in unknown words for book %s" deleted document-id)))
  (->>
   (db/get-all-unknown-words {:document-id document-id :grade grade :limit limit :offset offset})
   (map words/islocal-to-boolean)
   (map words/complement-braille)
   (map words/complement-ellipsis-braille)
   (map words/complement-hyphenation)))
