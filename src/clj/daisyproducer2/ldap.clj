(ns daisyproducer2.ldap
  (:require [clj-ldap.client :as ldap]
            [clojure.tools.logging :as log]
            [daisyproducer2.config :refer [env]]
            [mount.core :refer [defstate]]))

(defstate ldap-pool
  :start
  (if-let [address (env :ldap-address)]
    (ldap/connect
     {:host
      {:address address
       :port 389
       :connect-timeout (* 1000 5)
       :timeout (* 1000 30)}})
    (log/warn "LDAP bind address not found, please set :ldap-address in the config file"))
  :stop
  (when ldap-pool
    (ldap/close ldap-pool)))

(defn- extract-group [s]
  (->> s
   (re-matches #"cn=(\w+),cn=groups,cn=accounts,dc=sbszh,dc=ch")
   second))

(defn- add-roles [{groups :memberOf :as user}]
  (let [roles (->> groups
                   (map extract-group)
                   (remove nil?))]
    (assoc user :roles roles)))

(defn authenticate [username password & [attributes]]
  (let [conn           (ldap/get-connection ldap-pool)
        qualified-name (str "uid=" username ",cn=users,cn=accounts,dc=sbszh,dc=ch")]
    (try
      (if (ldap/bind? conn qualified-name password)
        (-> conn
            (ldap/search "cn=users,cn=accounts,dc=sbszh,dc=ch"
                         {:filter (str "uid=" username)
                          :attributes (or attributes [])})
            first
            add-roles
            (select-keys [:uid :mail :initials :displayName :telephoneNumber :roles])))
      (finally (ldap/release-connection ldap-pool conn)))))
