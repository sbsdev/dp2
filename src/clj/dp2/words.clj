(ns dp2.words)

(defn spelling [language]
  (case language
    ("de" "de-CH") 1
    ("de-1901" "de-CH-1901") 0
    1))

(defn grades [grade]
  (case grade ; convert grade into a list of grades
    (1 2) [grade] ; for grade 1 and 2 the list contains just that grade
    0 [1 2])) ; grade 0 really means both grades
