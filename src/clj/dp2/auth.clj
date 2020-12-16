(ns dp2.auth
  (:require
   [dp2.ldap :as ldap]
   [dp2.middleware :as middleware]
   [buddy.sign.jwt :as jwt]
   [dp2.config :refer [env]]))

(defn login [username password]
  (when-let [user (ldap/authenticate username password)]
    (let [claims {:user user}
          token (jwt/sign claims (env :jwt-secret))]
      {:token token
       :user user})))
