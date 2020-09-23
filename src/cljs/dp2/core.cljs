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

(defonce session (r/atom {:page :documents
                          :document-search ""
                          :word-search ""}))

(def document-search (r/cursor session [:document-search]))
(def word-search (r/cursor session [:word-search]))

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
       [nav-link "#/" "Documents" :documents]
       [nav-link "#/words" "Global Words" :global-words]]]]))

(defn search-ui [search placeholder fetch-fn]
  [:div.field.is-horizontal.is-pulled-right
    [:div.field-label.is-normal
     [:label.label "Search:"]]
    [:div.field-body
     [:div.control
      [:input.input
       {:type "text"
        :placeholder placeholder
        :value @search
        :on-change (fn [e]
                     (let [new-search-term (-> e .-target .-value)]
                       (reset! search new-search-term)
                       (fetch-fn new-search-term)))}]]]])

(declare fetch-documents! fetch-document! fetch-local-words! fetch-global-words!)

(defn documents-page []
  [:section.section>div.container>div.content
   [search-ui document-search "Title" fetch-documents!]
   [:table.table.is-striped
    [:thead
     [:tr
      [:th "Title"] [:th "Author"] [:th "Source Publisher"] [:th "State"]]]
    [:tbody
     (for [{:keys [id title author source_publisher state_id]} (:documents @session)]
       ^{:key id} [:tr
                   [:td [:a {:href (str "#/documents/" id)
                             :on-click (fn []
                                         (fetch-document! id)
                                         (fetch-local-words! id))}
                         title]]
                   [:td author] [:td source_publisher] [:td (state-mapping state_id state_id)]])]]])

(defn horizontal-field [label value]
  [:div.field.is-horizontal
      [:div.field-label
       [:label.label label]]
      [:div.field-body
       [:div.field
        [:div.control
         [:input.input {:type "text" :readOnly true :value value}]]]]])

(def type-mapping {0 "None" 1 "Name (Type Hoffmann)" 2 "Name"
                   3 "Place (Type Langenthal)" 4 "Place"
                   5 "Homograph"})

(defn document-page []
  (let [{:keys [title author source_publisher state_id] :as document} (:document @session)
        state (state-mapping state_id state_id)]
    [:section.section>div.container>div.content
     [:div
      [horizontal-field "Title" title]
      [horizontal-field "Author" author]
      [horizontal-field "Source Publisher" source_publisher]
      [horizontal-field "State" state]]
     [:div
      [:table.table.is-striped
       [:thead
        [:tr
         [:th "Untranslated"] [:th "Braille"] [:th "Hyphenated"] [:th "Type"] [:th ""]]]
       [:tbody
        (for [{:keys [id untranslated braille type hyphenated]} (:local-words @session)]
          ^{:key id} [:tr [:td untranslated]
                      [:td [:input.input {:type "text" :value braille}]]
                      [:td [:input.input {:type "text" :value hyphenated}]]
                      [:td (get type-mapping type "Unknown")]
                      [:td [:div.field.is-grouped
                            [:p.control [:button.button.is-success [:span.icon.is-small [:i.mi.mi-done]] [:span "Save"]]]
                            [:p.control [:button.button.is-warning [:span.icon.is-small [:i.mi.mi-description]] [:span "Local"]]]
                            [:p.control [:button.button.is-danger.is-outlined [:span.icon.is-small [:i.mi.mi-clear]] [:span "Ignore"]]]
                            ]]])]]]]))

(defn words-page []
  [:section.section>div.container>div.content
   [search-ui word-search "Word" fetch-global-words!]
   [:table.table.is-striped
    [:thead
     [:tr
      [:th "Untranslated"] [:th "Braille"] [:th "Grade"] [:th "Markup"] [:th "Homograph Disambiguation"]]]
    [:tbody
     (for [{:keys [id untranslated braille grade type homograph_disambiguation]} (:global-words @session)]
       ^{:key id} [:tr [:td untranslated] [:td braille] [:td grade] [:td type] [:td homograph_disambiguation]])]]])

(def pages
  {:documents #'documents-page
   :document #'document-page
   :global-words #'words-page})

(defn page []
  [(pages (:page @session))])

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :documents]
    ["/documents/:id" :document]
    ["/words" :global-words]]))

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
(defn fetch-documents! [search-term]
  (GET "/api/documents" {:params {:search (str "%" search-term "%")}
                         :handler #(swap! session assoc :documents %)}))

(defn fetch-document! [id]
  (GET (str "/api/documents/" id) {:handler (fn [doc] (swap! session assoc :document doc))}))

(defn fetch-global-words! [search-term]
  (GET "/api/words" {:params {:search search-term}
                         :handler #(swap! session assoc :global-words %)}))

(defn fetch-local-words! [id]
  (GET (str "/api/documents/" id "/unknown-words") {:handler #(swap! session assoc :local-words %)}))

(defn mount-components []
  (rdom/render [#'navbar] (.getElementById js/document "navbar"))
  (rdom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (ajax/load-interceptors!)
  (fetch-documents! "")
  (fetch-global-words! "")
  (hook-browser-navigation!)
  (mount-components))
