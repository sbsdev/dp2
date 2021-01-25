(ns dp2.auth
  (:require [buddy.sign.jwt :as jwt]
            [clojure.tools.logging :as log]
            [dp2.config :refer [env]]
            [dp2.ldap :as ldap]
            [dp2.utils :as utils]))

(defn login [username password]
  (when-let [user (ldap/authenticate username password)]
    (let [claims {:user user}
          token (jwt/sign claims (env :jwt-secret))]
      (log/debug "User logged in" user)
      {:token token
       :user user})))

(defn is-admin? [{{user :user} :identity :as req}]
  (utils/is-admin? user))
