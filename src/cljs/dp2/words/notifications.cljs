(ns dp2.words.notifications
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::loading?
  (fn [db [_ id]]
    (->> db :loading id)))

(rf/reg-event-db
 ::ack-error
 (fn [db [_ error-id]]
   (update db :errors dissoc error-id)))

(rf/reg-sub
 ::errors
 (fn [db _]
   (->> db :errors)))

(rf/reg-sub
 ::errors?
 :<- [::errors]
  (fn [errors _]
    (seq errors)))

(defn loading-spinner []
  [:div.block
   [:p.has-text-centered.has-text-weight-semibold "Loading..."]
   [:button.button.is-large.is-fullwidth.is-dark.is-loading "Loading..."]])

(defn error-notification []
  (let [errors @(rf/subscribe [::errors])]
    (when errors
      [:div.block
       (for [[k v] errors]
         ^{:key k}
         [:div.notification.is-danger
          [:button.delete
           {:on-click (fn [e] (rf/dispatch [::ack-error k]))}]
          [:strong (str k ":")] (str " " v)])])))
