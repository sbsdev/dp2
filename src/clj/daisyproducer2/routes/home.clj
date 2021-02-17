(ns daisyproducer2.routes.home
  (:require
   [daisyproducer2.layout :as layout]
   [daisyproducer2.db.core :as db]
   [clojure.java.io :as io]
   [daisyproducer2.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]
   [trptcolin.versioneer.core :as version]))

(defn home-page [request]
  (let [group "ch.sbs"
        artifact "daisyproducer2"]
    (layout/render request "home.html" {:version (version/get-version group artifact)
                                        :revision (version/get-revision group artifact)})))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/docs" {:get (fn [_]
                    (-> (response/ok (-> "docs/docs.md" io/resource slurp))
                        (response/header "Content-Type" "text/plain; charset=utf-8")))}]])

