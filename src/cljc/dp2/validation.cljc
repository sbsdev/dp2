(ns dp2.validation
  (:require
   [struct.core :as st]
   [clojure.string :as string]))

(def valid-braille-re
  "RegExp to validate Braille"
  #"-?[A-Z0-9&%\[^\],;:/?+=\(*\).\\@#\"!>$_<\'àáâãåæçèéêëìíîïðñòóôõøùúûýþÿœāăąćĉċčďđēėęğģĥħĩīįıĳĵķĺļľŀłńņňŋōŏőŕŗřśŝşšţťŧũūŭůűųŵŷźżžǎẁẃẅỳ┊]+")

(def valid-hyphenation-re
  "RegExp to validate a hyphenation. Allow a set of lowercase characters
  and hyphens, but require that the hyphenation starts and ends with
  at least two chars"
  #"[a-z\xC0-\xFF\u0100-\u017F]{2,}[a-z\xC0-\xFF\u0100-\u017F-]*[a-z\xC0-\xFF\u0100-\u017F]{2,}")

(defn braille-valid?
  "Return true if `s` is valid ascii braille."
  [s]
  (and (not (string/blank? s))
       (some? (re-matches valid-braille-re s))))

(defn hyphenation-valid?
  "Return true if the `hyphenation` is not blank and contains at least
  one of the letters 'a-z', '\u00DF-\u00FF' or '-'. Also each '-' in
  the hyphenation should be surrounded by letters. If `word` is also
  given then `hyphenation` must be equal to `word` (modulo the
  hyphenation marks)."
  ([hyphenation]
   (and (not (string/blank? hyphenation))
        (not (string/starts-with? hyphenation "-"))
        (not (string/ends-with? hyphenation "-"))
        (not (string/includes? hyphenation "--"))
        (some? (re-matches valid-hyphenation-re hyphenation))))
  ([hyphenation word]
   (and (hyphenation-valid? hyphenation)
        (= word (string/replace hyphenation "-" "")))))

(defn word-valid?
  [{:keys [uncontracted contracted hyphenated untranslated]}]
  (and (or (nil? uncontracted) (braille-valid? uncontracted))
       (or (nil? contracted) (braille-valid? contracted))
       (or (nil? hyphenated) (hyphenation-valid? hyphenated untranslated))))

