(ns dp2.words.unknown
  (:require [ajax.core :as ajax]
            [dp2.auth :as auth]
            [dp2.i18n :refer [tr]]
            [dp2.pagination :as pagination]
            [dp2.submit-all :as submit-all]
            [dp2.validation :as validation]
            [dp2.words :as words]
            [dp2.words.grade :as grade]
            [dp2.words.notifications :as notifications]
            [re-frame.core :as rf]))

(rf/reg-event-fx
  ::fetch-words
  (fn [{:keys [db]} [_ id]]
    (let [grade @(rf/subscribe [::grade/grade])
          offset (pagination/offset db :unknown)]
      {:db (assoc-in db [:loading :unknown] true)
       :http-xhrio {:method          :get
                    :uri             (str "/api/documents/" id "/unknown-words")
                    :params          {:grade grade
                                      :offset (* offset pagination/page-size)
                                      :limit pagination/page-size}
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::fetch-words-success]
                    :on-failure      [::fetch-words-failure :fetch-words]}})))

(rf/reg-event-db
 ::fetch-words-success
 (fn [db [_ words]]
   (let [words (->> words
                    (map #(assoc % :uuid (str (random-uuid)))))
         next? (-> words count (= pagination/page-size))]
     (-> db
         (assoc-in [:words :unknown] (zipmap (map :uuid words) words))
         (pagination/update-next :unknown next?)
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
                   (select-keys [:untranslated :uncontracted :contracted :type :homograph-disambiguation
                                 :document-id :islocal :hyphenated :spelling]))
          document-id (:document-id word)]
      {:db (notifications/set-button-state db :unknown id :save)
       :http-xhrio {:method          :put
                    :format          (ajax/json-request-format)
                    :headers 	     (auth/auth-header db)
                    :uri             (str "/api/documents/" document-id "/words")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::ack-save id]
                    :on-failure      [::ack-failure id :save]
                    }})))

(rf/reg-event-fx
  ::save-all-words
  (fn [{:keys [db]} _]
    (let [ids (keys (get-in db [:words :unknown]))]
      {:dispatch-n (map (fn [id] [::save-word id]) ids)})))

(rf/reg-event-db
  ::ack-save
  (fn [db [_ id]]
    (-> db
        (update-in [:words :unknown] dissoc id)
        (notifications/clear-button-state :unknown id :save))))

(rf/reg-event-db
 ::ack-failure
 (fn [db [_ id request-type response]]
   (-> db
       (assoc-in [:errors request-type] (or (get-in response [:response :status-text])
                                            (get response :status-text)))
       (notifications/clear-button-state :unknown id request-type))))

(rf/reg-event-db
  ::ignore-word
  (fn [db [_ uuid]]
    (update-in db [:words :unknown] dissoc uuid)))

(rf/reg-sub
 ::words
 (fn [db _]
   (->> db :words :unknown vals)))

(rf/reg-sub
 ::words-sorted
 :<- [::words]
 (fn [words] (->> words (sort-by (juxt :document-id :untranslated)))))

(rf/reg-sub
 ::has-words?
 :<- [::words]
 (fn [words] (->> words seq some?)))

(rf/reg-sub
 ::words-valid?
 :<- [::words]
 (fn [words] (every? validation/word-valid? words)))

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
   (validation/word-valid? (get-in db [:words :unknown id]))))

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
           [:p.help.is-danger (tr [:input-not-valid])])]))))

(defn local-field [id]
  (let [value @(rf/subscribe [::word-field id :islocal])]
    [:input {:type "checkbox"
             :checked value
             :on-change #(rf/dispatch [::set-word-field id :islocal (not value)])}]))

(defn buttons [id]
  (let [valid? @(rf/subscribe [::valid? id])
        authenticated? @(rf/subscribe [::auth/authenticated?])]
    [:div.buttons.has-addons
     (if @(rf/subscribe [::notifications/button-loading? :unknown id :save])
       [:button.button.is-success.is-loading]
       [:button.button.is-success.has-tooltip-arrow
        {:disabled (not (and valid? authenticated?))
         :data-tooltip (tr [:save])
         :on-click (fn [e] (rf/dispatch [::save-word id]))}
        [:span.icon [:i.mi.mi-done]]
        #_[:span (tr [:save])]])
     [:button.button.is-danger.has-tooltip-arrow
      {:disabled (not authenticated?)
       :data-tooltip (tr [:ignore])
       :on-click (fn [e] (rf/dispatch [::ignore-word id]))}
      [:span.icon [:i.mi.mi-cancel]]
      #_[:span (tr [:ignore])]]]))

(defn word [id]
  (let [grade @(rf/subscribe [::grade/grade])
        {:keys [uuid untranslated uncontracted contracted type homograph-disambiguation hyphenated]} @(rf/subscribe [::word id])]
    [:tr
     [:td untranslated]
     (when (#{0 1} grade)
       (if uncontracted
         [:td [input-field uuid :uncontracted validation/braille-valid?]]
         [:td]))
     (when (#{0 2} grade)
       (if contracted
         [:td [input-field uuid :contracted validation/braille-valid?]]
         [:td]))
     [:td (when hyphenated
            [input-field uuid :hyphenated #(validation/hyphenation-valid? % untranslated)])]
     [:td {:width "8%"} (get words/type-mapping type (tr [:unknown]))]
     [:td {:width "8%"} homograph-disambiguation]
     [:td [local-field uuid]]
     [:td {:width "8%"} [buttons uuid]]]))

(defn unknown-words []
  (let [words @(rf/subscribe [::words-sorted])
        document @(rf/subscribe [:current-document])
        spelling (:spelling (first words))
        grade @(rf/subscribe [::grade/grade])
        loading? @(rf/subscribe [::notifications/loading? :unknown])
        errors? @(rf/subscribe [::notifications/errors?])]
    (cond
      errors? [notifications/error-notification]
      loading? [notifications/loading-spinner]
      :else
      [:<>
       [:table.table.is-striped
        [:thead
         [:tr
          [:th (tr [:untranslated])]
          (when (#{0 1} grade) [:th (tr [:uncontracted])])
          (when (#{0 2} grade) [:th (tr [:contracted])])
          [:th (tr [:hyphenated-with-spelling] [(words/spelling-string spelling)])]
          [:th (tr [:type])]
          [:th (tr [:homograph-disambiguation])]
          [:th (tr [:local])]
          [:th (tr [:action])]]]
        [:tbody
         (for [{:keys [uuid]} words]
           ^{:key uuid}
           [word uuid])]]
       [submit-all/buttons (tr [:save-all]) [::words-valid?] [::has-words?] [::save-all-words]]
       [pagination/pagination :unknown [::fetch-words (:id document)]]])))
