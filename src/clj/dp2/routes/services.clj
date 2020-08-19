(ns dp2.routes.services
  (:require
    [dp2.db.core :as db]
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.coercion.spec :as spec-coercion]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.parameters :as parameters]
    [spec-tools.data-spec :as spec]
    [dp2.middleware.formats :as formats]
    [dp2.middleware.exception :as exception]
    [ring.util.http-response :refer :all]
    [clojure.java.io :as io]))

(defn service-routes []
  ["/api"
   {:coercion spec-coercion/coercion
    :muuntaja formats/instance
    :swagger {:id ::api}
    :middleware [;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 exception/exception-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodys
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart
                 multipart/multipart-middleware]}

   ;; swagger documentation
   ["" {:no-doc true
        :swagger {:info {:title "my-api"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url "/api/swagger.json"
              :config {:validator-url nil}})}]]

   ["/ping"
    {:get (constantly (ok {:message "pong"}))}]

   ["/documents"
    {:swagger {:tags ["documents"]}}

    [""
     {:get {:summary "Get all documents"
            :description "Get all documents. Optionally limit the result set using `limit` and `offset`."
            :parameters {:query {(spec/opt :limit) int?, (spec/opt :offset) int?}}
            :handler (fn [{{{:keys [limit offset] :or {limit 200 offset 0}} :query} :parameters}]
                       {:status 200
                        :body (db/get-documents {:limit limit :offset offset})})}}]

    ["/:id"
     {:get {:summary "Get a document by ID"
            :parameters {:path {:id int?}}
            :handler (fn [{{{:keys [id]} :path} :parameters}]
                       (if-let [doc (db/get-document {:id id})]
                         {:status 200 :body doc}
                         {:status 404}))}}]

    ["/:id/images"
     {:get {:summary "Get all images for a given document"
            :parameters {:path {:id int?}}
            :handler (fn [{{{:keys [id]} :path} :parameters}]
                       (if-let [doc (db/get-images {:id id})]
                         {:status 200 :body doc}
                         {:status 404}))}}]]

   ["/words"
    {:get {:summary "Get global words. Optionally filter the results by using a search string, a grade, a type and a limit and an offset."
           :tags ["words"]
           :parameters {:query {(spec/opt :search) string?
                                (spec/opt :grade) int?
                                (spec/opt :type) int?
                                (spec/opt :limit) int?
                                (spec/opt :offset) int?}}
           :handler (fn [{{{:keys [search grade type limit offset]
                            :or {limit 200 offset 0}} :query} :parameters}]
                      {:status 200
                       :body (db/search-global-words {:search search :grade grade :type type
                                                      :limit limit :offset offset})})}}]

   ["/words/:untranslated"
    {:get {:summary "Get global words by untranslated"
           :tags ["words"]
           :parameters {:path {:untranslated string?}}
           :handler (fn [{{{:keys [untranslated]} :path} :parameters}]
                      (if-let [doc (db/get-global-word {:untranslated untranslated})]
                        {:status 200 :body doc}
                        {:status 404}))}}]

   ["/documents/:id/words"
    {:get {:summary "Get all local words for a given document"
           :tags ["words"]
           :parameters {:path {:id int?}}
           :handler (fn [{{{:keys [id]} :path} :parameters}]
                      (if-let [doc (db/get-local-words {:id id})]
                        {:status 200 :body doc}
                        {:status 404}))}}]
   ])
