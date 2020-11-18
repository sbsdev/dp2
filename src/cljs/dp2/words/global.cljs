(ns dp2.words.global
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [dp2.words.grade :as grade]
    [clojure.string :as string]))

(rf/reg-event-fx
  ::fetch-words
  (fn [{:keys [db]} [_ search]]
    {:db (assoc-in db [:loading :words] true)
     :http-xhrio {:method          :get
                  :uri             "/api/words"
                  :params          {:search (if (string/blank? search) "" (str search "%"))}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::fetch-words-success]
                  :on-failure      [::fetch-words-failure :fetch-global-words]}}))

(rf/reg-event-db
 ::fetch-words-success
 (fn [db [_ words]]
   (let [words (->> words
                    (map #(assoc % :uuid (str (random-uuid)))))]
     (-> db
         (assoc-in [:words :global] (zipmap (map :uuid words) words))
         (assoc-in [:loading :words] false)))))

(rf/reg-event-db
 ::fetch-words-failure
 (fn [db [_ request-type response]]
   (-> db
       (assoc-in [:errors request-type] (get response :status-text))
       (assoc-in [:loading :words] false))))

(rf/reg-sub
  ::words
  (fn [db _]
    (->> db :words :global vals (sort-by :untranslated))))

(rf/reg-sub
  ::search
  (fn [db _]
    (-> db :words-search)))

(rf/reg-event-fx
   ::set-search
   (fn [{:keys [db]} [_ new-search-value]]
     {:dispatch [:fetch-global-words new-search-value]
      :db   (assoc db :words-search new-search-value)}))


(defn words-search []
  (let [gettext (fn [e] (-> e .-target .-value))
        emit    (fn [e] (rf/dispatch [::set-search (gettext e)]))]
    [:div.field
     [:div.control
      [:input.input {:type "text"
                     :placeholder "Search"
                     :value @(rf/subscribe [::search])
                     :on-change emit}]]]))

(defn words-filter []
  [:div.field.is-horizontal
   [:div.field-body
    [words-search]
    [grade/selector :fixme #_"Add the event that updates the global words"]]])

(defn words-page []
  [:section.section>div.container>div.content
   [words-filter]
   [:table.table.is-striped
    [:thead
     [:tr
      [:th "Untranslated"] [:th "Braille"] [:th "Grade"] [:th "Markup"] [:th "Homograph Disambiguation"]]]
    [:tbody
     (for [{:keys [id untranslated braille grade type homograph-disambiguation]} @(rf/subscribe [::words])]
       ^{:key id} [:tr [:td untranslated] [:td braille] [:td grade] [:td type] [:td homograph-disambiguation]])]]])
