(ns dp2.words)

(def type-mapping {0 "None" 1 "Name (Type Hoffmann)" 2 "Name"
                   3 "Place (Type Langenthal)" 4 "Place"
                   5 "Homograph"})

(defn spelling-string [spelling]
  (case spelling
    0 "Old spelling"
    1 "New spelling"
    "Unknown spelling"))

