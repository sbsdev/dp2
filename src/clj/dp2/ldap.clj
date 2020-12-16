(ns dp2.ldap
  (:require
   [mount.core :refer [defstate]]
   [clj-ldap.client :as ldap]
   [dp2.config :refer [env]]
   [clojure.tools.logging :as log]))

(defstate ldap-pool
  :start
  (if-let [address (env :ldap-address)]
    (ldap/connect
     {:host
      {:address address
       :port 389
       :connect-timeout (* 1000 5)
       :timeout (* 1000 30)}})
    (log/warn "LDAP bind address not found, please set :ldap-adress in the config file"))
  :stop
  (when ldap-pool
    (ldap/close ldap-pool)))

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
            (select-keys [:uid :mail :initials :displayName :telephoneNumber])))
      (finally (ldap/release-connection ldap-pool conn)))))


