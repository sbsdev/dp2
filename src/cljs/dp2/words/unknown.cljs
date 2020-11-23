(ns dp2.words.unknown
  (:require
   [re-frame.core :as rf]
   [clojure.string :as string]
   [ajax.core :as ajax]
   [dp2.words :as words]
   [dp2.words.grade :as grade]
   [dp2.words.notifications :as notifications]))

(rf/reg-event-fx
  ::fetch-words
  (fn [{:keys [db]} [_ id]]
    (let [grade @(rf/subscribe [::grade/grade])]
      {:db (assoc-in db [:loading :unknown] true)
       :http-xhrio {:method          :get
                    :uri             (str "/api/documents/" id "/unknown-words")
                    :params          {:grade grade}
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::fetch-words-success]
                    :on-failure      [::fetch-words-failure :fetch-words]}})))

(rf/reg-event-db
 ::fetch-words-success
 (fn [db [_ words]]
   (let [words (->> words
                    (map #(assoc % :uuid (str (random-uuid)))))]
     (-> db
         (assoc-in [:words :unknown] (zipmap (map :uuid words) words))
         (assoc-in [:loading :unknown] false)))))

(rf/reg-event-db
 ::fetch-words-failure
 (fn [db [_ request-type response]]
   (-> db
       (assoc-in [:errors request-type] (get response :status-text))
       (assoc-in [:loading :unknown] false))))

(rf/reg-event-fx
  ::save-word
  (fn [{:keys [db]} [_ id]]
    (let [word (get-in db [:words :unknown id])
          cleaned (-> word
                   (select-keys [:untranslated :grade1 :grade2 :type :homograph-disambiguation
                                 :document-id :islocal :hyphenated :spelling]))
          document-id (:document-id word)]
      {:http-xhrio {:method          :put
                    :format          (ajax/json-request-format)
                    :uri             (str "/api/documents/" document-id "/words")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::ack-save id]
                    }})))

(rf/reg-event-db
  ::ack-save
  (fn [db [_ uuid]]
    (update-in db [:words :unknown] dissoc uuid)))

(rf/reg-event-db
  ::ignore-word
  (fn [db [_ uuid]]
    (update-in db [:words :unknown] dissoc uuid)))

(rf/reg-sub
 ::words
 (fn [db _]
   (->> db :words :unknown vals (sort-by :untranslated))))

(rf/reg-sub
 ::word
 (fn [db [_ id]]
   (get-in db [:words :unknown id])))

(rf/reg-sub
 ::word-field
 (fn [db [_ id field-id]]
   (get-in db [:words :unknown id field-id])))

(rf/reg-event-db
 ::set-word-field
 (fn [db [_ id field-id value]]
   (assoc-in db [:words :unknown id field-id] value)))

(rf/reg-sub
 ::valid?
 (fn [db [_ id]]
   (words/valid? (get-in db [:words :unknown id]))))

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

(defn local-field [id]
  (let [value @(rf/subscribe [::word-field id :islocal])]
    [:input {:type "checkbox"
             :checked value
             :on-change #(rf/dispatch [::set-word-field id :islocal (not value)])}]))

(defn buttons [id]
  (let [valid? @(rf/subscribe [::valid? id])]
    [:div.buttons.has-addons
     [:button.button.is-success
      {:disabled (not valid?)
       :on-click (fn [e] (rf/dispatch [::save-word id]))}
      [:span.icon [:i.mi.mi-done]]
      #_[:span "Approve"]]
     [:button.button.is-danger
      {:on-click (fn [e] (rf/dispatch [::ignore-word id]))}
      [:span.icon [:i.mi.mi-cancel]]
      #_[:span "Delete"]]]))

(defn word [id]
  (let [grade @(rf/subscribe [::grade/grade])
        {:keys [uuid untranslated grade1 grade2 type homograph-disambiguation]} @(rf/subscribe [::word id])]
    [:tr
     [:td untranslated]
     (when (#{0 1} grade)
       (if grade1
         [:td [input-field uuid :grade1 words/braille-valid?]]
         [:td]))
     (when (#{0 2} grade)
       (if grade2
         [:td [input-field uuid :grade2 words/braille-valid?]]
         [:td]))
     [:td [input-field uuid :hyphenated #(words/hyphenation-valid? % untranslated)]]
     [:td {:width "8%"} (get words/type-mapping type "Unknown")]
     [:td {:width "8%"} homograph-disambiguation]
     [:td [local-field uuid]]
     [:td {:width "8%"} [buttons uuid]]]))

(defn unknown-words []
  (let [words @(rf/subscribe [::words])
        spelling (:spelling (first words))
        grade @(rf/subscribe [::grade/grade])
        loading? @(rf/subscribe [::notifications/loading? :unknown])
        errors? @(rf/subscribe [::notifications/errors?])]
    (cond
      errors? [notifications/error-notification]
      loading? [notifications/loading-spinner]
      :else
      [:table.table.is-striped
       [:thead
        [:tr
         [:th "Untranslated"]
         (when (#{0 1} grade) [:th "Grade 1"])
         (when (#{0 2} grade) [:th "Grade 2"])
         [:th "Hyphenated (" (words/spelling-string spelling) ")"] [:th "Type"]
         [:th "Homograph Disambiguation"] [:th "Local"]
         [:th "Action"]]]
       [:tbody
        (for [{:keys [uuid]} words]
          ^{:key uuid}
          [word uuid])]])))
