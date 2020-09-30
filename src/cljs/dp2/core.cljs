(ns dp2.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [goog.events :as events]
    [goog.history.EventType :as HistoryEventType]
    [markdown.core :refer [md->html]]
    [dp2.ajax :as ajax]
    [dp2.events]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [clojure.string :as string])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href   uri
    :class (when (= page @(rf/subscribe [:common/page])) :is-active)}
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
                 [nav-link "#/words" "Words" :words]]]]))

(defn words-page []
  [:section.section>div.container>div.content
   [:table.table.is-striped
    [:thead
     [:tr
      [:th "Untranslated"] [:th "Braille"] [:th "Grade"] [:th "Markup"] [:th "Homograph Disambiguation"]]]
    [:tbody
     (for [{:keys [id untranslated braille grade type homograph_disambiguation]} @(rf/subscribe [:words/global])]
       ^{:key id} [:tr [:td untranslated] [:td braille] [:td grade] [:td type] [:td homograph_disambiguation]])]]])

(def state-mapping {1 "New" 4 "In Production" 6 "Finished"})

(defn documents-page []
  [:section.section>div.container>div.content
   [:table.table.is-striped
    [:thead
     [:tr
      [:th "Title"] [:th "Author"] [:th "Source Publisher"] [:th "State"]]]
    [:tbody
     (for [{:keys [id title author source_publisher state_id]} @(rf/subscribe [:documents])]
       ^{:key id} [:tr
                   [:td [:a title]]
                   [:td author] [:td source_publisher] [:td (state-mapping state_id state_id)]])]]])

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    [:div
     [navbar]
     [page]]))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(def router
  (reitit/router
    [["/" {:name        :documents
           :view        #'documents-page
           :controllers [{:start (fn [_] (rf/dispatch [:init-documents]))}]}]
     ["/words" {:name :words
                :view #'words-page
                :controllers [{:start (fn [_] (rf/dispatch [:init-global-words]))}]}]]))

(defn start-router! []
  (rfe/start!
    router
    navigate!
    {}))

;; -------------------------
;; Initialize app
(defn mount-components []
  (rf/clear-subscription-cache!)
  (rdom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (start-router!)
  (ajax/load-interceptors!)
  (mount-components))
