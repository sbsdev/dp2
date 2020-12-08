(ns dp2.words.global
  (:require
    [re-frame.core :as rf]
    [ajax.core :as ajax]
    [dp2.words :as words]
    [dp2.words.notifications :as notifications]
    [clojure.string :as string]))

(rf/reg-event-fx
  ::fetch-words
  (fn [{:keys [db]} [_]]
    (let [search @(rf/subscribe [::search])]
      {:db (assoc-in db [:loading :global] true)
       :http-xhrio {:method          :get
                    :uri             "/api/words"
                    :params          {:untranslated (if (string/blank? search) "" (str search "%"))
                                      :limit 50}
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::fetch-words-success]
                    :on-failure      [::fetch-words-failure :fetch-global-words]}})))

(rf/reg-event-db
 ::fetch-words-success
 (fn [db [_ words]]
   (let [words (->> words
                    (map #(assoc % :uuid (str (random-uuid)))))]
     (-> db
         (assoc-in [:words :global] (zipmap (map :uuid words) words))
         (assoc-in [:loading :global] false)))))

(rf/reg-event-db
 ::fetch-words-failure
 (fn [db [_ request-type response]]
   (-> db
       (assoc-in [:errors request-type] (get response :status-text))
       (assoc-in [:loading :global] false))))

(rf/reg-event-fx
  ::save-word
  (fn [{:keys [db]} [_ id]]
    (let [word (get-in db [:words :global id])
          cleaned (-> word
                      (select-keys [:untranslated :uncontracted :contracted :type :homograph-disambiguation]))]
      {:http-xhrio {:method          :put
                    :format          (ajax/json-request-format)
                    :uri             (str "/api/words")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    }})))

(rf/reg-event-fx
  ::delete-word
  (fn [{:keys [db]} [_ id]]
    (let [word (get-in db [:words :global id])
          cleaned (-> word
                      (select-keys [:untranslated :uncontracted :contracted :type :homograph-disambiguation]))]
      {:http-xhrio {:method          :delete
                    :format          (ajax/json-request-format)
                    :uri             (str "/api/words")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::ack-delete id]
                    }})))

(rf/reg-event-db
  ::ack-delete
  (fn [db [_ id]]
    (update-in db [:words :global] dissoc id)))


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
     {:db (assoc db :words-search new-search-value)
      :dispatch [::fetch-words]}))


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
    [words-search]]])

(rf/reg-sub
 ::word
 (fn [db [_ id]]
   (get-in db [:words :global id])))

(rf/reg-sub
 ::word-field
 (fn [db [_ id field-id]]
   (get-in db [:words :global id field-id])))

(rf/reg-event-db
 ::set-word-field
 (fn [db [_ id field-id value]]
   (assoc-in db [:words :global id field-id] value)))

(rf/reg-sub
 ::valid?
 (fn [db [_ id]]
   (words/valid? (get-in db [:words :global id]))))

(defn input-field [id field-id validator]
  (let [initial-value @(rf/subscribe [::word-field id field-id])
        get-value (fn [e] (-> e .-target .-value))
        reset! #(rf/dispatch [::set-word-field id field-id initial-value])
        save! #(rf/dispatch [::set-word-field id field-id %])]
    (fn []
      (let [value @(rf/subscribe [::word-field id field-id])
            valid? (validator value)
            changed? (not= initial-value value)]
        [:div.field
         [:input.input {:type "text"
                        :class (cond (not valid?) "is-danger"
                                     changed? "is-warning")
                        :value value
                        :on-change #(save! (get-value %))
                        :on-key-down #(when (= (.-which %) 27) (reset!))}]
         (when-not valid?
           [:p.help.is-danger "Input not valid"])]))))

(defn buttons [id]
  (let [valid? @(rf/subscribe [::valid? id])]
    [:div.buttons.has-addons
     [:button.button.is-success.has-tooltip-arrow
      {:disabled (not valid?)
       :data-tooltip "Save"
       :on-click (fn [e] (rf/dispatch [::save-word id]))}
      [:span.icon [:i.mi.mi-done]]
      #_[:span "Approve"]]
     [:button.button.is-danger.has-tooltip-arrow
      {:data-tooltip "Delete"
       :on-click (fn [e] (rf/dispatch [::delete-word id]))}
      [:span.icon [:i.mi.mi-cancel]]
      #_[:span "Delete"]]]))

(defn word [id]
  (let [{:keys [uuid untranslated type homograph-disambiguation]} @(rf/subscribe [::word id])]
    [:tr
     [:td untranslated]
     [:td [input-field uuid :uncontracted words/braille-valid?]]
     [:td [input-field uuid :contracted words/braille-valid?]]
     [:td {:width "8%"} (get words/type-mapping type "Unknown")]
     [:td {:width "8%"} homograph-disambiguation]
     [:td {:width "8%"} [buttons uuid]]]))

(defn words-page []
  (let [loading? @(rf/subscribe [::notifications/loading? :global])
        errors? @(rf/subscribe [::notifications/errors?])]
    [:section.section>div.container>div.content
     [:<>
      [words-filter]
      (cond
        errors? [notifications/error-notification]
        loading? [notifications/loading-spinner]
        :else
        [:table.table.is-striped
         [:thead
          [:tr
           [:th "Untranslated"] [:th "Grade 1"] [:th "Grade 2"] [:th "Type"] [:th "Homograph Disambiguation"]]]
         [:tbody
          (for [{:keys [uuid]} @(rf/subscribe [::words])]
            ^{:key uuid} [word uuid])]])]]))
