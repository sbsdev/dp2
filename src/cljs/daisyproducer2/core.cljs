(ns daisyproducer2.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [goog.events :as events]
    [goog.history.EventType :as HistoryEventType]
    [markdown.core :refer [md->html]]
    [daisyproducer2.ajax :as ajax]
    [daisyproducer2.events]
    [daisyproducer2.auth :as auth]
    [daisyproducer2.words :as words]
    [daisyproducer2.words.unknown :as unknown]
    [daisyproducer2.words.local :as local]
    [daisyproducer2.words.global :as global]
    [daisyproducer2.words.confirm :as confirm]
    [daisyproducer2.words.grade :as grade]
    [daisyproducer2.i18n :refer [tr]]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [clojure.string :as string])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href   uri
    :class (when (= page @(rf/subscribe [:common/page-id])) :is-active)}
   title])

(defn navbar []
  (r/with-let [expanded? (r/atom false)]
    (let [is-admin? @(rf/subscribe [::auth/is-admin?])]
      [:nav.navbar.is-info>div.container
       [:div.navbar-brand
        [:span.navbar-item {:style {:font-weight :bold}} "daisyproducer2"]
        [:span.navbar-burger.burger
         {:data-target :nav-menu
          :on-click #(swap! expanded? not)
          :class (when @expanded? :is-active)}
         [:span][:span][:span]]]
       [:div#nav-menu.navbar-menu
        {:class (when @expanded? :is-active)}
        [:div.navbar-start
         [nav-link "#/" (tr [:documents]) :documents]
         (when is-admin? [nav-link "#/confirm" (tr [:confirm]) :confirm])
         (when is-admin? [nav-link "#/words" (tr [:words]) :words])]
        [:div.navbar-end
         [:div.navbar-item
          (auth/user-buttons)]]]])))

(def state-mapping {1 (tr [:new]) 4 (tr [:in-production]) 6 (tr [:finished])})

(defn document-summary [{:keys [title author source-publisher state-id]}]
  (let [state (state-mapping state-id state-id)]
    [:div.block
     [:table.table
      [:tbody
       [:tr [:th {:width 200} (tr [:title])] [:td title]]
       [:tr [:th (tr [:author])] [:td author]]
       [:tr [:th (tr [:source-publisher])] [:td source-publisher]]
       [:tr [:th (tr [:state])] [:td state]]]]]))

(defn document-tab-link [uri title page on-click]
  (if-let [is-active (= page @(rf/subscribe [:common/page-id]))]
    [:li.is-active [:a title]]
    [:li [:a {:href uri :on-click on-click} title]]))

(defn document-tabs [{:keys [id]}]
  [:div.block
   [:div.tabs.is-boxed
    [:ul
     [document-tab-link (str "#/documents/" id) (tr [:details]) :document]
     [document-tab-link (str "#/documents/" id "/unknown") (tr [:unknown-words]) :document-unknown (fn [_] (rf/dispatch [::unknown/fetch-words id]))]
     [document-tab-link (str "#/documents/" id "/local") (tr [:local-words]) :document-local (fn [_] (rf/dispatch [::local/fetch-words id]))]
     ]]])

(defn document-details [document]
  [:table.table.is-striped
   [:tbody
    (for [[k v] document]
      ^{:key k}
      [:tr [:th k] [:td v]])]])

(defn document-page []
  (let [document @(rf/subscribe [:current-document])]
    [:section.section>div.container>div.content
     [document-summary document]
     [document-tabs document]
     [document-details document]]))

(defn document-unknown []
  (let [document @(rf/subscribe [:current-document])]
    [:section.section>div.container>div.content
     [document-summary document]
     [document-tabs document]
     [grade/selector ::unknown/fetch-words]
     [unknown/unknown-words]]))

(defn document-local []
  (let [document @(rf/subscribe [:current-document])]
    [:section.section>div.container>div.content
     [document-summary document]
     [document-tabs document]
     [grade/selector ::local/fetch-words]
     [local/local-words]]))

(defn documents-search []
  (let [gettext (fn [e] (-> e .-target .-value))
        emit    (fn [e] (rf/dispatch [:documents-search-change (gettext e)]))]
    [:div.field
     [:div.control
      [:input.input {:type "text"
                     :placeholder (tr [:search])
                     :value @(rf/subscribe [:documents-search])
                     :on-change emit}]]]))

(defn document-link [{:keys [id title] :as document}]
  [:a {:href (str "#/documents/" id)
       :on-click (fn [_] (rf/dispatch [:set-current-document document]))}
   title])

(defn documents-page []
  [:section.section>div.container>div.content
   [documents-search]
   [:table.table.is-striped
    [:thead
     [:tr
      [:th (tr [:title])] [:th (tr [:author])] [:th (tr [:source-publisher])] [:th (tr [:state])]]]
    [:tbody
     (for [{:keys [id author source-publisher state-id] :as document} @(rf/subscribe [:documents])]
       ^{:key id} [:tr
                   [:td [document-link document]]
                   [:td author] [:td source-publisher] [:td (state-mapping state-id state-id)]])]]])

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
     ["/login" {:name :login
                :view #'auth/login-page}]
     ["/documents/:id" {:name :document
                        :view #'document-page
                        :controllers [{:parameters {:path [:id]}
                                       :start (fn [params] (rf/dispatch [:init-current-document (-> params :path :id)]))}]}]
     ["/documents/:id/unknown" {:name :document-unknown
                                :view #'document-unknown}]
     ["/documents/:id/local" {:name :document-local
                              :view #'document-local}]
     ["/confirm" {:name :confirm
                  :view #'confirm/words-page
                  :controllers [{:start (fn [_] (rf/dispatch [::confirm/fetch-words]))}]}]
     ["/words" {:name :words
                :view #'global/words-page}]]))

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
