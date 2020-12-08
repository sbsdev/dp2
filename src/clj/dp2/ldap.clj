(ns dp2.ldap
  (:require
   [mount.core :refer [defstate]]
   [clj-ldap.client :as ldap]
   [dp2.config :refer [env]]))

(defstate ldap-pool :start
  (ldap/connect
   {:host
    {:address         (env :ldap-adress)
     :port            389
     :connect-timeout (* 1000 5)
     :timeout         (* 1000 30)}}))

(defn authenticate [username password & [attributes]]
  (let [conn           (ldap/get-connection ldap-pool)
        qualified-name (str "uid=" username ",cn=users,cn=accounts,dc=sbszh,dc=ch")]
    (try
      (if (ldap/bind? conn qualified-name password)
        (first (ldap/search conn
                            "cn=users,cn=accounts,dc=sbszh,dc=ch"
                            {:filter (str "uid=" username)
                             :attributes (or attributes [])})))
      (finally (ldap/release-connection ldap-pool conn)))))


