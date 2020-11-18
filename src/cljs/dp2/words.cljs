(ns dp2.words
  (:require
   [clojure.string :as string]))

(def type-mapping {0 "None" 1 "Name (Type Hoffmann)" 2 "Name"
                   3 "Place (Type Langenthal)" 4 "Place"
                   5 "Homograph"})

(def valid-braille-re
  #"-?[A-Z0-9&%\[^\],;:/?+=\(*\).\\@#\"!>$_<\'àáâãåæçèéêëìíîïðñòóôõøùúûýþÿœāăąćĉċčďđēėęğģĥħĩīįıĳĵķĺļľŀłńņňŋōŏőŕŗřśŝşšţťŧũūŭůűųŵŷźżžǎẁẃẅỳ┊]+")

(defn braille-valid?
  "Return true if `s` is valid ascii braille."
  [s]
  (and (not (string/blank? s))
       (some? (re-matches valid-braille-re s))))

(defn hyphenation-valid?
  "Return true if the `hyphenation` is not blank, is equal to
  `word` (modulo the hyphenation marks) and contains at least one of
  the letters 'a-z', '\u00DF-\u00FF' or '-'. Also each '-' in the
  hyphenation should be surrounded by letters."
  [hyphenation word]
  (and (not (string/blank? hyphenation))
       (= word (string/replace hyphenation "-" ""))
       (not (string/starts-with? hyphenation "-"))
       (not (string/ends-with? hyphenation "-"))
       (not (string/includes? hyphenation "--"))
       (some? (re-matches #"[a-z\xC0-\xFF\u0100-\u017F-]+" hyphenation))))

(defn valid?
  [{:keys [grade1 grade2 hyphenated untranslated]}]
  (and (or (nil? grade1) (braille-valid? grade1))
       (or (nil? grade2) (braille-valid? grade2))
       (or (nil? hyphenated) (hyphenation-valid? hyphenated untranslated))))

(defn spelling-string [spelling]
  (case spelling
    0 "Old spelling"
    1 "New spelling"
    "Unknown spelling"))

