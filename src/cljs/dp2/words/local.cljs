(ns dp2.words.local
  (:require [ajax.core :as ajax]
            [clojure.string :as string]
            [dp2.auth :as auth]
            [dp2.i18n :refer [tr]]
            [dp2.pagination :as pagination]
            [dp2.validation :as validation]
            [dp2.words :as words]
            [dp2.words.grade :as grade]
            [dp2.words.input-fields :as fields]
            [dp2.words.notifications :as notifications]
            [re-frame.core :as rf]))

(rf/reg-event-fx
  ::fetch-words
  (fn [{:keys [db]} [_ id]]
    (let [grade @(rf/subscribe [::grade/grade])
          offset (pagination/offset db :local)
          search @(rf/subscribe [::search id])]
      {:db (assoc-in db [:loading :local] true)
       :http-xhrio {:method          :get
                    :uri             (str "/api/documents/" id "/words")
                    :params          (cond-> {:grade grade
                                              :offset offset
                                              :limit pagination/page-size}
                                       (not (string/blank? search)) (assoc :search search))
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
         (assoc-in [:words :local] (zipmap (map :uuid words) words))
         (pagination/update-next :local next?)
         (assoc-in [:loading :local] false)
         ;; clear all button loading states
         (update-in [:loading] dissoc :buttons)))))

(rf/reg-event-db
 ::fetch-words-failure
 (fn [db [_ request-type response]]
   (-> db
       (assoc-in [:errors request-type] (get response :status-text))
       (assoc-in [:loading :local] false))))

(rf/reg-event-fx
  ::save-word
  (fn [{:keys [db]} [_ id]]
    (let [word (get-in db [:words :local id])
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
                    :on-success      [::ack-save id]
                    :on-failure      [::ack-failure id :save]
                    }})))

(rf/reg-event-fx
  ::delete-word
  (fn [{:keys [db]} [_ id]]
    (let [word (get-in db [:words :local id])
          cleaned (-> word
                      (select-keys [:untranslated :uncontracted :contracted :type :homograph-disambiguation
                                    :document-id :hyphenated :spelling]))
          document-id (:document-id word)]
      {:db (notifications/set-button-state db id :delete)
       :http-xhrio {:method          :delete
                    :format          (ajax/json-request-format)
                    :headers 	     (auth/auth-header db)
                    :uri             (str "/api/documents/" document-id "/words")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::ack-delete id document-id]
                    :on-failure      [::ack-failure id :delete]
                    }})))

(rf/reg-event-db
  ::ack-save
  (fn [db [_ id]]
    (-> db
        (notifications/clear-button-state id :save))))

(rf/reg-event-fx
  ::ack-delete
  (fn [{:keys [db]} [_ id document-id]]
    (let [db (-> db
                 (update-in [:words :local] dissoc id)
                 (notifications/clear-button-state id :delete))
          empty? (-> db (get-in [:words :local]) count (< 1))]
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

(rf/reg-sub
  ::search
  (fn [db [_ document-id]]
    (get-in db [:search :local document-id])))

(rf/reg-event-fx
   ::set-search
   (fn [{:keys [db]} [_ document-id new-search-value]]
     {:db (assoc-in db [:search :local document-id] new-search-value)
      :dispatch-n (list
                   ;; when searching for a new word reset the pagination
                   [::pagination/reset :local]
                   [::fetch-words document-id])}))


(defn words-search [document-id]
  (let [get-value (fn [e] (-> e .-target .-value))
        reset!    #(rf/dispatch [::set-search document-id ""])
        save!     #(rf/dispatch [::set-search document-id %])]
    [:div.field
     [:div.control
      [:input.input {:type "text"
                     :placeholder (tr [:search])
                     :value @(rf/subscribe [::search document-id])
                     :on-change #(save! (get-value %))
                     :on-key-down #(when (= (.-which %) 27) (reset!))}]]]))

(defn words-filter [document-id]
  [:div.field.is-horizontal
   [:div.field-body
    [words-search document-id]]])

(rf/reg-sub
 ::words
 (fn [db _]
   (->> db :words :local vals (sort-by :untranslated))))

(rf/reg-sub
 ::word
 (fn [db [_ id]]
   (get-in db [:words :local id])))

(rf/reg-sub
 ::valid?
 (fn [db [_ id]]
   (validation/word-valid? (get-in db [:words :local id]))))

(defn buttons [id]
  (let [valid? @(rf/subscribe [::valid? id])
        authenticated? @(rf/subscribe [::auth/authenticated?])]
    [:div.buttons.has-addons
     (if @(rf/subscribe [::notifications/button-loading? id :save])
       [:button.button.is-success.is-loading]
       [:button.button.is-success.has-tooltip-arrow
        {:disabled (not (and valid? authenticated?))
         :data-tooltip (tr [:save])
         :on-click (fn [e] (rf/dispatch [::save-word id]))}
        [:span.icon [:i.mi.mi-done]]])
     (if @(rf/subscribe [::notifications/button-loading? id :delete])
       [:button.button.is-danger.is-loading]
       [:button.button.is-danger.has-tooltip-arrow
        {:disabled (not authenticated?)
         :data-tooltip (tr [:delete])
         :on-click (fn [e] (rf/dispatch [::delete-word id]))}
        [:span.icon [:i.mi.mi-cancel]]
        #_[:span (tr [:delete])]])]))

(defn word [id]
  (let [grade @(rf/subscribe [::grade/grade])
        {:keys [uuid untranslated uncontracted contracted type homograph-disambiguation hyphenated]} @(rf/subscribe [::word id])]
    [:tr
     [:td untranslated]
     (when (#{0 1} grade)
       (if uncontracted
         [:td [fields/input-field :local uuid :uncontracted validation/braille-valid?]]
         [:td]))
     (when (#{0 2} grade)
       (if contracted
         [:td [fields/input-field :local uuid :contracted validation/braille-valid?]]
         [:td]))
     [:td (when hyphenated
            [fields/input-field :local uuid :hyphenated #(validation/hyphenation-valid? % untranslated)])]
     [:td {:width "8%"} (get words/type-mapping type (tr [:unknown]))]
     [:td {:width "8%"} homograph-disambiguation]
     [:td [fields/local-field :local uuid]]
     [:td {:width "8%"} [buttons uuid]]]))

(defn local-words []
  (let [words @(rf/subscribe [::words])
        document @(rf/subscribe [:current-document])
        spelling (:spelling (first words))
        grade @(rf/subscribe [::grade/grade])
        loading? @(rf/subscribe [::notifications/loading? :local])
        errors? @(rf/subscribe [::notifications/errors?])]
    [:<>
     [words-filter (:id document)]
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
        [pagination/pagination :local [::fetch-words (:id document)]]])]))
