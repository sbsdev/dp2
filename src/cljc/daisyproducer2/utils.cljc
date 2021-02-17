(ns daisyproducer2.utils)

(defn is-admin? [{:keys [roles] :as user}]
  (contains? (apply hash-set roles) "mvl"))
