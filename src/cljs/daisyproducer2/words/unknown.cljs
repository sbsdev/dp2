(ns daisyproducer2.words.unknown
  (:require [ajax.core :as ajax]
            [daisyproducer2.auth :as auth]
            [daisyproducer2.i18n :refer [tr]]
            [daisyproducer2.pagination :as pagination]
            [daisyproducer2.submit-all :as submit-all]
            [daisyproducer2.validation :as validation]
            [daisyproducer2.words :as words]
            [daisyproducer2.words.grade :as grade]
            [daisyproducer2.words.input-fields :as fields]
            [daisyproducer2.words.notifications :as notifications]
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
                                      :offset offset
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
         (assoc-in [:loading :unknown] false)
         ;; clear all button loading states
         (update-in [:loading] dissoc :buttons)))))

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
      {:db (notifications/set-button-state db id :save)
       :http-xhrio {:method          :put
                    :format          (ajax/json-request-format)
                    :headers 	     (auth/auth-header db)
                    :uri             (str "/api/documents/" document-id "/words")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::ack-save id document-id]
                    :on-failure      [::ack-failure id :save]
                    }})))

(rf/reg-event-fx
  ::save-all-words
  (fn [{:keys [db]} _]
    (let [ids (keys (get-in db [:words :unknown]))]
      {:dispatch-n (map (fn [id] [::save-word id]) ids)})))

(rf/reg-event-fx
  ::ack-save
  (fn [{:keys [db]} [_ id document-id]]
    (let [db (-> db
                 (update-in [:words :unknown] dissoc id)
                 (notifications/clear-button-state id :save))
          empty? (-> db (get-in [:words :unknown]) count (< 1))]
      (if empty?
        {:db db :dispatch [::fetch-words document-id]}
        {:db db}))))

(rf/reg-event-db
 ::ack-failure
 (fn [db [_ id request-type response]]
   (-> db
       (assoc-in [:errors request-type] (or (get-in response [:response :status-text])
                                            (get response :status-text)))
       (notifications/clear-button-state id request-type))))

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
 ::valid?
 (fn [db [_ id]]
   (validation/word-valid? (get-in db [:words :unknown id]))))

(defn buttons [id]
  (let [valid? @(rf/subscribe [::valid? id])
        authenticated? @(rf/subscribe [::auth/authenticated?])]
    [:div.buttons.has-addons
     (if @(rf/subscribe [::notifications/button-loading? id :save])
       [:button.button.is-success.is-loading]
       [:button.button.is-success.has-tooltip-arrow
        {:disabled (not (and valid? authenticated?))
         :data-tooltip (tr [:approve])
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
        {:keys [uuid untranslated uncontracted contracted type homograph-disambiguation
                hyphenated invalid-hyphenated]} @(rf/subscribe [::word id])]
    [:tr
     [:td untranslated]
     (when (#{0 1} grade)
       (if uncontracted
         [:td [fields/input-field :unknown uuid :uncontracted validation/braille-valid?]]
         [:td]))
     (when (#{0 2} grade)
       (if contracted
         [:td [fields/input-field :unknown uuid :contracted validation/braille-valid?]]
         [:td]))
     [:td (if hyphenated
            [fields/input-field :unknown uuid :hyphenated #(validation/hyphenation-valid? % untranslated)]
            [fields/disabled-field invalid-hyphenated])]
     [:td {:width "8%"} (get words/type-mapping type (tr [:unknown]))]
     [:td {:width "8%"} homograph-disambiguation]
     [:td [fields/local-field :unknown uuid]]
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
