(ns dp2.words.notifications
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::loading?
  (fn [db [_ id]]
    (->> db :loading id)))

(defn loading-spinner []
  [:div.box
   [:p.has-text-centered.has-text-weight-semibold "Loading..."]
   [:button.button.is-large.is-fullwidth.is-dark.is-loading "Loading..."]])


