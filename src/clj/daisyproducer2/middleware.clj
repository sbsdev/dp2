(ns daisyproducer2.middleware
  (:require
    [daisyproducer2.env :refer [defaults]]
    [clojure.tools.logging :as log]
    [daisyproducer2.layout :refer [error-page]]
    [daisyproducer2.i18n :refer [tr]]
    [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
    [daisyproducer2.middleware.formats :as formats]
    [muuntaja.middleware :refer [wrap-format wrap-params]]
    [daisyproducer2.config :refer [env]]
    [daisyproducer2.auth :as auth]
    [ring.middleware.flash :refer [wrap-flash]]
    [ring.util.http-response :refer [unauthorized forbidden]]
    [ring.adapter.undertow.middleware.session :refer [wrap-session]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
    [buddy.auth.accessrules :refer [restrict]]
    [buddy.auth :refer [authenticated?]]
    [buddy.auth.backends.token :refer [jws-backend]]
    [buddy.auth.backends.session :refer [session-backend]]
    [iapetos.collector.ring :as prometheus]
    [daisyproducer2.metrics :as metrics])
  )

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t (.getMessage t))
        (error-page {:status 500
                     :title (tr [:something-bad-happened])
                     :message (tr [:something-bad-happened-message])})))))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
    handler
    {:error-response
     (error-page
       {:status 403
        :title (tr [:invalid-anti-forgery-token])})}))


(defn wrap-formats [handler]
  (let [wrapped (-> handler wrap-params (wrap-format formats/instance))]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

(defn on-unauthenticated [request response]
  (unauthorized
   {:status-text (tr [:not-authenticated] [(:uri request)])}))

(defn on-unauthorized [request response]
  (forbidden
   {:status-text (tr [:not-authorized] [(:uri request)])}))

(defn wrap-restricted [handler]
  (restrict handler {:handler authenticated?
                     :on-error on-unauthenticated}))

(defn wrap-authorized [handler]
  (restrict handler {:handler auth/is-admin?
                     :on-error on-unauthorized}))

;; see https://adambard.com/blog/clojure-auth-with-buddy/ for some
;; inspiration on how to do JW* Token Authentication
(defn wrap-auth [handler]
  (let [backend (jws-backend {:secret (env :jwt-secret)})]
    (-> handler
        (wrap-authentication backend)
        (wrap-authorization backend))))

(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      wrap-auth
      wrap-flash
      (wrap-session {:cookie-attrs {:http-only true}})
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (dissoc :session)))
      (prometheus/wrap-metrics
       metrics/registry {:path "/metrics"})
      wrap-internal-error))
