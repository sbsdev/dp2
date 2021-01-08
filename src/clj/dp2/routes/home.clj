(ns dp2.routes.home
  (:require
   [dp2.layout :as layout]
   [dp2.db.core :as db]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [dp2.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn home-page [request]
  (let [version-info (-> "dp2/version.edn" io/resource slurp edn/read-string)]
    (layout/render request "home.html" version-info)))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/docs" {:get (fn [_]
                    (-> (response/ok (-> "docs/docs.md" io/resource slurp))
                        (response/header "Content-Type" "text/plain; charset=utf-8")))}]])

