(ns dp2.words
  (:require
       [dp2.i18n :refer [tr]]))

(def type-mapping {0 (tr [:type-none])
                   1 (tr [:type-name-hoffmann])
                   2 (tr [:type-name])
                   3 (tr [:type-place-langenthal])
                   4 (tr [:type-place])
                   5 (tr [:type-homograph])})

(defn spelling-string [spelling]
  (case spelling
    0 (tr [:spelling/old])
    1 (tr [:spelling/new])
    (tr [:spelling/unknown])))

(defn spelling-brief-string [spelling]
  (case spelling
    0 (tr [:spelling/old-brief])
    1 (tr [:spelling/new-brief])
    (tr [:spelling/unknown-brief])))
