(ns daisyproducer2.auth
  (:require [buddy.sign.jwt :as jwt]
            [clojure.tools.logging :as log]
            [daisyproducer2.config :refer [env]]
            [daisyproducer2.ldap :as ldap]
            [daisyproducer2.utils :as utils]))

(defn login [username password]
  (if-let [user (ldap/authenticate username password)]
    (let [claims {:user user}
          token (jwt/sign claims (env :jwt-secret))]
      (log/debug "Login success" user)
      {:token token
       :user user})
    (log/debug "Login failed for" username)))

(defn is-admin? [{{user :user} :identity :as req}]
  (utils/is-admin? user))
