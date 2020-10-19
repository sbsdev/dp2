(ns dp2.words.unknown
  (:require
   [re-frame.core :as rf]
   [clojure.set :refer [rename-keys]]
   [ajax.core :as ajax]
   [dp2.words :as words]))

(rf/reg-event-db
  ::set-words
  (fn [db [_ words]]
    (let [words (->> words
                     (map #(assoc % :uuid (str (random-uuid)))))]
      (assoc-in db [:words :unknown] (zipmap (map :uuid words) words)))))

(rf/reg-event-fx
  ::fetch-words
  (fn [_ [_ id]]
    {:http-xhrio {:method          :get
                  :uri             (str "/api/documents/" id "/unknown-words")
                  :params          {:grade 2}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::set-words]}}))

(rf/reg-event-fx
  ::save-word
  (fn [_ [_ word]]
    (let [uuid (:uuid word)
          word (-> word
                   (select-keys [:untranslated :braille :grade :type :homograph-disambiguation
                                 :document-id])
                   (assoc :islocal false))
          document-id (:document-id word)]
      {:http-xhrio {:method          :put
                    :format          (ajax/json-request-format)
                    :uri             (str "/api/documents/" document-id "/words")
                    :params          word
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::ack-word uuid]
                    }})))

(rf/reg-event-db
  ::ack-word
  (fn [db [_ uuid]]
    (update-in db [:words :unknown] dissoc uuid)))

(rf/reg-event-fx
  ::init-words
  (fn [{:keys [db]} [_ id]]
    {:dispatch [::fetch-words id]}))

(rf/reg-event-db
  ::ignore-word
  (fn [db [_ uuid]]
    (update-in db [:words :unknown] dissoc uuid)))

(rf/reg-sub
 ::unknown-words
 (fn [db _]
   (-> db :words :unknown)))

(defn document-unknown-words [document]
  (let [words @(rf/subscribe [::unknown-words])]
    [:div.block
     [:table.table.is-striped
      [:thead
       [:tr
        [:th "Untranslated"] [:th "Braille"] [:th "Hyphenated"] [:th "Type"] [:th "Homograph Disambiguation"] [:th "Action"]]]
      [:tbody
       (for [{:keys [uuid untranslated braille hyphenated type homograph-disambiguation] :as word} (sort-by :untranslated (vals words))]
         ^{:key uuid}
         [:tr [:td untranslated]
          [:td [:input.input {:type "text" :value braille}]]
          [:td [:input.input {:type "text" :value hyphenated}]]
          [:td (get words/type-mapping type "Unknown")]
          [:td homograph-disambiguation]
          [:td
           [:div.buttons.has-addons
            [:button.button.is-success
             {:on-click (fn [e] (rf/dispatch [::save-word word]))}
             [:span.icon [:i.mi.mi-done]] #_[:span "Approve"]]
            [:button.button.is-warning.is-outlined
             [:span.icon [:i.mi.mi-book]] #_[:span "Local"]]
            [:button.button.is-danger.is-outlined
             {:on-click (fn [e] (rf/dispatch [::ignore-word uuid]))}
             [:span.icon [:i.mi.mi-cancel]] #_[:span "Ignore"]]]]
          ])]]]))
