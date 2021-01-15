(ns dp2.pagination
  (:require [dp2.i18n :refer [tr]]
            [re-frame.core :as rf]))

(def page-size 20)

(defn offset [db id]
  (get-in db [:pagination id :offset] 0))

(defn update-next-prev [db id next? prev?]
  (-> db
      (assoc-in [:pagination id :next?] next?)
      (assoc-in [:pagination id :prev?] prev?)))

(defn- inc-offset [db id]
  (update-in db [:pagination id :offset] (fnil inc 0)))

(defn- dec-offset [db id]
  (update-in db [:pagination id :offset] (fnil dec 0)))

(defn- has-next? [db id]
  (get-in db [:pagination id :next?]))

(defn- has-previous? [db id]
  (get-in db [:pagination id :prev?]))

(rf/reg-event-db
 ::reset
 (fn [db [_ id]]
   (assoc-in db [:pagination id :offset] 0)))

(rf/reg-event-fx
 ::next-page
 (fn [{:keys [db]} [_ id fetch-event]]
   (when (has-next? db id)
     {:db (inc-offset db id)
      :dispatch fetch-event})))

(rf/reg-event-fx
 ::previous-page
 (fn [{:keys [db]} [_ id fetch-event]]
   (when (has-previous? db id)
     {:db (dec-offset db id)
      :dispatch fetch-event})))

(rf/reg-sub
 ::has-next?
 (fn [db [_ id]]
   (has-next? db id)))

(rf/reg-sub
 ::has-previous?
 (fn [db [_ id]]
   (has-previous? db id)))

(defn pagination [id event]
  (let [has-previous? @(rf/subscribe [::has-previous? id])
        has-next? @(rf/subscribe [::has-next? id])]
    [:nav.pagination.is-right {:role "navigation" :arial-label "pagination"}
     [:a.pagination-previous
      {:disabled (not has-previous?)
       :on-click (fn [e] (rf/dispatch [::previous-page id event]))}
      (tr [:previous])]
     [:a.pagination-next
      {:disabled (not has-next?)
       :on-click (fn [e] (rf/dispatch [::next-page id event]))}
      (tr [:next])]
     ;; we have to add an empty pagination list to make the rest of the pagination nav work
     [:ul.pagination-list]]))

