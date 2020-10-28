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
    [clojure.java.io :as io]
    [clojure.string :refer [blank?]]
    [dp2.documents :as docs]
    [dp2.words.unknown :as unknown]
    [dp2.words.local :as local]))

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
        :swagger {:info {:title "Daisyproducer"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url "/api/swagger.json"
              :config {:validator-url nil}})}]]

   ["/documents"
    {:swagger {:tags ["Documents"]}}

    [""
     {:get {:summary "Get all documents"
            :description "Get all documents. Optionally limit the result set using a `search` term, a `limit` and an `offset`."
            :parameters {:query {(spec/opt :search) string?
                                 (spec/opt :limit) int?
                                 (spec/opt :offset) int?}}
            :handler (fn [{{{:keys [limit offset search] :or {limit 200 offset 0}} :query} :parameters}]
                       (ok (if (blank? search)
                             (db/get-documents {:limit limit :offset offset})
                             (db/find-documents {:limit limit :offset offset :search search}))))}}]

    ["/:id"
     {:get {:summary "Get a document by ID"
            :parameters {:path {:id int?}}
            :handler (fn [{{{:keys [id]} :path} :parameters}]
                       (if-let [doc (db/get-document {:id id})]
                         (ok doc)
                         (not-found)))}}]

    ["/:id/images"
     {:get {:summary "Get all images for a given document"
            :parameters {:path {:id int?}}
            :handler (fn [{{{:keys [id]} :path} :parameters}]
                       (if-let [doc (db/get-images {:id id})]
                         (ok doc)
                         (not-found)))}}]]

   ["/words"
    {:swagger {:tags ["Global Words"]}}

    [""
     {:get {:summary "Get global words. Optionally filter the results by using a search string, a grade, a type and a limit and an offset."
            :parameters {:query {(spec/opt :search) string?
                                 (spec/opt :grade) int?
                                 (spec/opt :type) int?
                                 (spec/opt :limit) int?
                                 (spec/opt :offset) int?}}
            :handler (fn [{{{:keys [search grade type limit offset]
                             :or {limit 200 offset 0}} :query} :parameters}]
                       (ok (if (blank? search)
                             (db/get-global-words {:limit limit :offset offset})
                             (db/find-global-words {:search search :grade grade :type type
                                                    :limit limit :offset offset}))))}}]

    ["/:untranslated"
     {:get {:summary "Get global words by untranslated"
            :parameters {:path {:untranslated string?}
                         :query {(spec/opt :grade) int?
                                 (spec/opt :type) int?}}
            :handler (fn [{{{:keys [untranslated]} :path {:keys [grade type]} :query} :parameters}]
                       (if-let [words (db/get-global-word {:untranslated untranslated :grade grade :type type})]
                         (ok words)
                         (not-found)))}}]]

   ["/documents/:id"

    ["/words"
     {:swagger {:tags ["Local Words"]}
      :get {:summary "Get all local words for a given document"
            :parameters {:path {:id int?}
                         :query {(spec/opt :grade) int?}}
            :handler (fn [{{{:keys [id]} :path {:keys [grade]} :query} :parameters}]
                       (if-let [words (local/get-words id grade)]
                         (ok words)
                         (not-found)))}

      :put {:summary "Update or create a local word for a given document"
            :parameters {:body {:untranslated string? :braille string?
                                :type int? :grade int? :homograph-disambiguation string?
                                :document-id int? :islocal boolean?
                                :hyphenated string? :spelling int?}}
            :handler (fn [{{word :body} :parameters}]
                       (local/put-word word)
                       (no-content))}

      :delete {:summary "Delete a local word for a given document"
               :parameters {:body {:untranslated string? :braille string?
                                   :type int? :grade int? :homograph-disambiguation string?
                                   :document-id int? :islocal boolean?}}
               :handler (fn [{{{:keys [untranslated type grade homograph-disambiguation document-id]} :body} :parameters}]
                          (let [deleted
                                (db/delete-local-word {:untranslated untranslated :type type :grade grade
                                                       :homograph_disambiguation homograph-disambiguation :document_id document-id})]
                            (if (> deleted 0)
                              (no-content) ; we found something and deleted it
                              (not-found))))}}] ; couldn't find and delete the requested resource

    ["/unknown-words"
     {:swagger {:tags ["Local Words"]}
      :get {:summary "Get all unknown words for a given document"
            :parameters {:path {:id int?}
                         :query {:grade int?}}
            :handler (fn [{{{:keys [id]} :path {:keys [grade]} :query} :parameters}]
                       (let [version (docs/get-latest-version id)
                             unknown (unknown/get-words version id grade)]
                         (ok unknown)))}}]

    ["/versions"
     {:swagger {:tags ["Versions"]}}

     [""
      {:get {:summary "Get all versions of a given document"
             :parameters {:path {:id int?}}
             :handler (fn [{{{:keys [id]} :path} :parameters}]
                        (if-let [doc (db/get-versions {:document_id id})]
                          (ok doc)
                          (not-found)))}}]

     ["/latest"
      {:get {:summary "Get the latest version of a given document"
             :parameters {:path {:id int?}}
             :handler (fn [{{{:keys [id]} :path} :parameters}]
                        (if-let [doc (db/get-latest-version {:document_id id})]
                          (ok doc)
                          (not-found)))}}]]]

   ["/hyphenations"
    {:swagger {:tags ["Hyphenations"]}}

    [""
     {:get {:summary "Get hyphenations by spelling. Optionally filter the results by using a search string, a limit and an offset."
            :parameters {:query {:spelling int?
                                 (spec/opt :search) string?
                                 (spec/opt :limit) int?
                                 (spec/opt :offset) int?}}
            :handler (fn [{{{:keys [spelling search limit offset]
                             :or {limit 200 offset 0}} :query} :parameters}]
                       (ok (db/get-hyphenation {:spelling spelling :search search
                                                :limit limit :offset offset})))}

      :put {:summary "Update or create a hyphenation"
            :parameters {:body {:word string? :hyphenation string? :spelling int?}}
            :handler (fn [{{{:keys [word hyphenation spelling]} :body} :parameters}]
                       (db/insert-hyphenation {:word word :hyphenation hyphenation :spelling spelling})
                       (no-content))}

      :delete {:summary "Delete a hyphenation"
               :parameters {:body {:word string? :spelling int? :hyphenation string?}}
               :handler (fn [{{{:keys [word spelling]} :body} :parameters}]
                          (let [deleted (db/delete-hyphenation {:word word :spelling spelling})]
                            (if (> deleted 0)
                              (no-content) ; we found something and deleted it
                              (not-found))))}}]] ; couldn't find and delete the requested resource
   ])
