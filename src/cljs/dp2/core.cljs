(ns dp2.core
  (:require
    [reagent.core :as r]
    [reagent.dom :as rdom]
    [goog.events :as events]
    [goog.history.EventType :as HistoryEventType]
    [markdown.core :refer [md->html]]
    [dp2.ajax :as ajax]
    [ajax.core :refer [GET POST]]
    [reitit.core :as reitit]
    [clojure.string :as string])
  (:import goog.History))

(defonce session (r/atom {:page :home
                          :search ""}))

(def search (r/cursor session [:search]))

(def state-mapping {1 "New"
                    4 "In Production"
                    6 "Finished"})

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href   uri
    :class (when (= page (:page @session)) "is-active")}
   title])

(defn navbar [] 
  (r/with-let [expanded? (r/atom false)]
    [:nav.navbar.is-info>div.container
     [:div.navbar-brand
      [:a.navbar-item {:href "/" :style {:font-weight :bold}} "dp2"]
      [:span.navbar-burger.burger
       {:data-target :nav-menu
        :on-click #(swap! expanded? not)
        :class (when @expanded? :is-active)}
       [:span][:span][:span]]]
     [:div#nav-menu.navbar-menu
      {:class (when @expanded? :is-active)}
      [:div.navbar-start
       [nav-link "#/" "Home" :home]
       [nav-link "#/documents" "Documents" :documents]]]]))

(defn search-ui []
  [:div.field.is-horizontal.is-pulled-right
    [:div.field-label.is-normal
     [:label.label "Search:"]]
    [:div.field-body
     [:div.control
      [:input.input
       {:type "text"
        :placeholder "Title"
        :value @search
        :on-change (fn [e]
                     (let [new-search-term (-> e .-target .-value)]
                       (reset! search new-search-term)
                       (fetch-documents! new-search-term)))}]]]])

(defn documents-page []
  [:section.section>div.container>div.content
   [search-ui]
   [:table.table.is-striped
    [:thead
     [:tr
      [:th "Title"] [:th "Author"] [:th "Source Publisher"] [:th "State"]]]
    [:tbody
     (for [{:keys [id title author source_publisher state_id]} (:documents @session)]
       ^{:key id} [:tr
                   [:td [:a {:href (str "#/documents/" id)
                             :on-click #(fetch-document! id)} title]]
                   [:td author] [:td source_publisher] [:td (state-mapping state_id state_id)]])]]])

(defn horizontal-field [label value]
  [:div.field.is-horizontal
      [:div.field-label
       [:label.label label]]
      [:div.field-body
       [:div.field
        [:div.control
         [:input.input {:type "text" :readOnly true :value value}]]]]])

(defn document-page []
  (let [{:keys [title author source_publisher state_id] :as document} (:document @session)
        state (state-mapping state_id state_id)]
    [:section.section>div.container>div.content
     [horizontal-field "Title" title]
     [horizontal-field "Author" author]
     [horizontal-field "Source Publisher" source_publisher]
     [horizontal-field "State" state]
     ]))


(defn home-page []
  [:section.section>div.container>div.content
   (when-let [docs (:docs @session)]
     [:div {:dangerouslySetInnerHTML {:__html (md->html docs)}}])])

(def pages
  {:home #'home-page
   :documents #'documents-page
   :document #'document-page})

(defn page []
  [(pages (:page @session))])

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :home]
    ["/documents" :documents]
    ["/documents/:id" :document]]))

(defn match-route [uri]
  (->> (or (not-empty (string/replace uri #"^.*#" "")) "/")
       (reitit/match-by-path router)
       :data
       :name))
;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      HistoryEventType/NAVIGATE
      (fn [event]
        (swap! session assoc :page (match-route (.-token event)))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn fetch-docs! []
  (GET "/docs" {:handler #(swap! session assoc :docs %)}))

(defn fetch-documents! [search-term]
  (GET "/api/documents" {:params {:search (str "%" search-term "%")}
                         :handler #(swap! session assoc :documents %)}))

(defn fetch-document! [id]
  (GET (str "/api/documents/" id) {:handler (fn [doc] (swap! session assoc :document doc))}))

(defn mount-components []
  (rdom/render [#'navbar] (.getElementById js/document "navbar"))
  (rdom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (ajax/load-interceptors!)
  (fetch-docs!)
  (fetch-documents! "")
  (hook-browser-navigation!)
  (mount-components))
