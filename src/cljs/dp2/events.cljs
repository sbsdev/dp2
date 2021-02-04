(ns dp2.events
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [reitit.frontend.easy :as rfe]
    [reitit.frontend.controllers :as rfc]
    [clojure.string :as string]))

;;;;;;;;;;;;;;;;;
;; Dispatchers ;;
;;;;;;;;;;;;;;;;;

(rf/reg-event-db
  :common/navigate
  (fn [db [_ match]]
    (let [old-match (:common/route db)
          new-match (assoc match :controllers
                                 (rfc/apply-controllers (:controllers old-match) match))]
      (assoc db :common/route new-match))))

(rf/reg-fx
  :common/navigate-fx!
  (fn [[k & [params query]]]
    (rfe/push-state k params query)))

(rf/reg-event-fx
  :common/navigate!
  (fn [_ [_ url-key params query]]
    {:common/navigate-fx! [url-key params query]}))

(rf/reg-event-db
  :common/set-error
  (fn [db [_ error]]
    (assoc db :common/error error)))

;; Documents

(rf/reg-event-fx
  :fetch-documents
  (fn [{:keys [db]} [_ search]]
    {:db (assoc-in db [:loading :documents] true)
     :http-xhrio {:method          :get
                  :uri             "/api/documents"
                  :response-format (ajax/json-response-format {:keywords? true})
                  :params          {:search (if (nil? search) "" search)}
                  :on-success      [:fetch-documents-success]
                  :on-failure      [:fetch-documents-failure :fetch-documents]}}))

(rf/reg-event-db
 :fetch-documents-success
 (fn [db [_ documents]]
   (-> db
       (assoc-in [:loading :documents] false)
       (assoc-in [:documents] documents))))

(rf/reg-event-db
 :fetch-documents-failure
 (fn [db [_ request-type response]]
   (-> db
       (assoc-in [:errors request-type] (get response :status-text))
       (assoc-in [:loading :documents] false))))

(rf/reg-event-fx
  :init-documents
  (fn [{:keys [db]} _]
    (let [search (:documents-search db)]
      {:dispatch [:fetch-documents search]})))

(rf/reg-event-fx
   :documents-search-change
   (fn [{:keys [db]} [_ new-search-value]]
     {:dispatch [:fetch-documents new-search-value]
      :db   (assoc db :documents-search new-search-value)}))

;; Single Document

(rf/reg-event-db
  :set-current-document
  (fn [db [_ document]]
    (assoc db :current-document document)))

(rf/reg-event-fx
  :fetch-current-document
  (fn [_ [_ id]]
    {:http-xhrio {:method          :get
                  :uri             (str "/api/documents/" id)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:set-current-document]}}))

(rf/reg-event-fx
  :init-current-document
  (fn [{:keys [db]} [_ id]]
    {:dispatch [:fetch-current-document id]}))

;;;;;;;;;;;;;;;;;;;
;; Subscriptions ;;
;;;;;;;;;;;;;;;;;;;

(rf/reg-sub
  :common/route
  (fn [db _]
    (-> db :common/route)))

(rf/reg-sub
  :common/page-id
  :<- [:common/route]
  (fn [route _]
    (-> route :data :name)))

(rf/reg-sub
  :common/page
  :<- [:common/route]
  (fn [route _]
    (-> route :data :view)))

(rf/reg-sub
  :docs
  (fn [db _]
    (:docs db)))

(rf/reg-sub
  :common/error
  (fn [db _]
    (:common/error db)))

(rf/reg-sub
  :documents
  (fn [db _]
    (-> db :documents)))

(rf/reg-sub
  :documents-search
  (fn [db _]
    (-> db :documents-search)))

(rf/reg-sub
 :current-document
 (fn [db _]
   (-> db :current-document)))
