(ns dp2.words)

(defn spelling [language]
  (case language
    ("de" "de-CH") 1
    ("de-1901" "de-CH-1901") 0
    1))
