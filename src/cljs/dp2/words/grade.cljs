(ns dp2.words.grade
  (:require
    [re-frame.core :as rf]))

(rf/reg-event-fx
  ::set-grade
  (fn [{:keys [db]} [_ grade dispatch]]
    (let [id (-> db :current-document :id)]
      {:db (assoc db :current-grade (js/parseInt grade))
       :dispatch [dispatch id]})))

(rf/reg-sub
 ::grade
 (fn [db _] (get db :current-grade)))

(defn selector [dispatch-event]
  (let [current @(rf/subscribe [::grade])
        getvalue (fn [e] (-> e .-target .-value))
        emit     (fn [e] (rf/dispatch [::set-grade (getvalue e) dispatch-event]))]
    [:div.field
     [:div.control
      [:div.select.is-fullwidth
       [:select
        {:on-change emit}
        (for [[v s] [[1 "Grade 1"]
                     [2 "Grade 2"]
                     [0 "Both"]]]
          ^{:key v}
          [:option (if (not= current v) {:value v} {:selected "selected" :value v}) s])]]]]))
