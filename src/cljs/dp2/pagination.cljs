(ns dp2.pagination
  (:require [dp2.i18n :refer [tr]]
            [re-frame.core :as rf]))

(def page-size 20)

(defn- has-next? [db id]
  (->> db :words id count (= page-size)))

(defn- has-previous? [db id]
  ;; clojurescript doesn't seem to mind (pos? nil), so (fnil pos? 0) is not needed
  (->> db :pagination id pos?))

(rf/reg-event-db
 ::reset
 (fn [db [_ id]]
   (assoc-in db [:pagination id] 0)))

(rf/reg-event-fx
 ::next-page
 (fn [{:keys [db]} [_ id fetch-event]]
   (when (has-next? db id)
     {:db (update-in db [:pagination id] (fnil inc 0))
      :dispatch fetch-event})))

(rf/reg-event-fx
 ::previous-page
 (fn [{:keys [db]} [_ id fetch-event]]
   (when (has-previous? db id)
     {:db (update-in db [:pagination id] (fnil dec 0))
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
    [:div.field.is-grouped
     [:p.control
      [:button.button
       {:disabled (not has-previous?)
        :on-click (fn [e] (rf/dispatch [::previous-page id event]))}
       (tr [:previous])]]
     [:p.control
      [:button.button
       {:disabled (not has-next?)
        :on-click (fn [e] (rf/dispatch [::next-page id event]))}
       (tr [:next])]]]))

