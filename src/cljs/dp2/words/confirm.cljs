(ns dp2.words.confirm
  (:require [ajax.core :as ajax]
            [dp2.auth :as auth]
            [dp2.i18n :refer [tr]]
            [dp2.pagination :as pagination]
            [dp2.submit-all :as submit-all]
            [dp2.validation :as validation]
            [dp2.words :as words]
            [dp2.words.notifications :as notifications]
            [re-frame.core :as rf]))

(rf/reg-event-fx
  ::fetch-words
  (fn [{:keys [db]} [_]]
    (let [offset (pagination/offset db :confirm)]
      {:db (assoc-in db [:loading :confirm] true)
       :http-xhrio {:method          :get
                    :uri             "/api/confirmable"
                    :params          {:offset (* offset pagination/page-size)
                                      :limit pagination/page-size}
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::fetch-words-success]
                    :on-failure      [::fetch-words-failure :fetch-confirm-words]}})))

(rf/reg-event-db
 ::fetch-words-success
 (fn [db [_ words]]
   (let [words (->> words
                    (map #(assoc % :uuid (str (random-uuid)))))
         next? (-> words count (= pagination/page-size))
         prev? (-> db (pagination/offset :confirm) pos?)]
     (-> db
         (assoc-in [:words :confirm] (zipmap (map :uuid words) words))
         (pagination/update-next-prev :confirm next? prev?)
         (assoc-in [:loading :confirm] false)))))

(rf/reg-event-db
 ::fetch-words-failure
 (fn [db [_ request-type response]]
   (-> db
       (assoc-in [:errors request-type] (get response :status-text))
       (assoc-in [:loading :confirm] false))))

(rf/reg-event-fx
  ::save-word
  (fn [{:keys [db]} [_ id]]
    (let [word (get-in db [:words :confirm id])
          cleaned (-> word
                      (select-keys [:untranslated :uncontracted :contracted :type :homograph-disambiguation
                                    :document-id :hyphenated :spelling :islocal]))]
      {:http-xhrio {:method          :put
                    :format          (ajax/json-request-format)
                    :headers 	     (auth/auth-header db)
                    :uri             (str "/api/confirmable")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::ack-save id]
                    }})))

(rf/reg-event-fx
  ::save-all-words
  (fn [{:keys [db]} _]
    (let [ids (keys (get-in db [:words :confirm]))]
      {:dispatch-n (map (fn [id] [::save-word id]) ids)})))

(rf/reg-event-fx
  ::delete-word
  (fn [{:keys [db]} [_ id]]
    (let [word (get-in db [:words :confirm id])
          cleaned (-> word
                      (select-keys [:untranslated :uncontracted :contracted :type :homograph-disambiguation
                                    :document-id :hyphenated :spelling]))
          document-id (:document-id word)]
      {:http-xhrio {:method          :delete
                    :format          (ajax/json-request-format)
                    :headers 	     (auth/auth-header db)
                    :uri             (str "/api/documents/" document-id "/words")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::ack-delete id]
                    }})))

(rf/reg-event-db
  ::ack-save
  (fn [db [_ id]]
    (update-in db [:words :confirm] dissoc id)))

(rf/reg-event-db
  ::ack-delete
  (fn [db [_ id]]
    (update-in db [:words :confirm] dissoc id)))

(rf/reg-sub
  ::words
  (fn [db _]
    (->> db :words :confirm vals)))

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
   (get-in db [:words :confirm id])))

(rf/reg-sub
 ::word-field
 (fn [db [_ id field-id]]
   (get-in db [:words :confirm id field-id])))

(rf/reg-event-db
 ::set-word-field
 (fn [db [_ id field-id value]]
   (assoc-in db [:words :confirm id field-id] value)))

(rf/reg-sub
 ::valid?
 (fn [db [_ id]]
   (validation/word-valid? (get-in db [:words :confirm id]))))

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
     [:button.button.is-success.has-tooltip-arrow
      {:disabled (not (and valid? authenticated?))
       :data-tooltip (tr [:approve])
       :on-click (fn [e] (rf/dispatch [::save-word id]))}
      [:span.icon [:i.mi.mi-done]]
      #_[:span (tr [:approve])]]
     [:button.button.is-danger.has-tooltip-arrow
      {:disabled (not authenticated?)
       :data-tooltip (tr [:delete])
       :on-click (fn [e] (rf/dispatch [::delete-word id]))}
      [:span.icon [:i.mi.mi-cancel]]
      #_[:span (tr [:delete])]]]))

(defn type-field [id]
  (let [type @(rf/subscribe [::word-field id :type])
        set-type-fn (fn [type]
                      (fn [e] (rf/dispatch [::set-word-field id :type type])))]
    (case type
      0 nil
      (1 2)
      [:div.select
       [:select
        [:option {:selected (= type 2)
                  :on-click (set-type-fn 2)}
         (tr [:type-name])]
        [:option {:selected (= type 1)
                  :on-click (set-type-fn 1)}
         (tr [:type-name-hoffmann])]]]
      (3 4)
      [:div.select
       [:select
        [:option {:selected (= type 4)
                  :on-click (set-type-fn 4)}
         (tr [:type-place])]
        [:option {:selected (= type 3)
                  :on-click (set-type-fn 3)}
         (tr [:type-place-langenthal])]]]
      5 (tr [:type-homograph])
      :else (tr [:unknown]))))

(defn word [id]
  (let [{:keys [uuid untranslated type homograph-disambiguation hyphenated spelling document-title]} @(rf/subscribe [::word id])]
    [:tr
     [:td untranslated]
     [:td [input-field uuid :uncontracted validation/braille-valid?]]
     [:td [input-field uuid :contracted validation/braille-valid?]]
     [:td (when hyphenated
            [input-field uuid :hyphenated #(validation/hyphenation-valid? % untranslated)])]
     [:td spelling]
     [:td [type-field uuid]]
     [:td homograph-disambiguation]
     [:td [:abbr {:title document-title } (str (subs document-title 0 3) "...")]]
     [:td [local-field uuid]]
     [:td {:width "8%"} [buttons uuid]]]))

(defn words-page []
  (let [loading? @(rf/subscribe [::notifications/loading? :confirm])
        errors? @(rf/subscribe [::notifications/errors?])]
    [:section.section>div.container>div.content
     (cond
       errors? [notifications/error-notification]
       loading? [notifications/loading-spinner]
       :else
       [:<>
        [:table.table.is-striped
         [:thead
          [:tr
           [:th (tr [:untranslated])]
           [:th (tr [:uncontracted])]
           [:th (tr [:contracted])]
           [:th (tr [:hyphenated])]
           [:th [:abbr {:title (tr [:spelling])} (subs (tr [:spelling]) 0 1)]]
           [:th [:abbr {:title (tr [:type])} (subs (tr [:type]) 0 1)]]
           [:th [:abbr {:title (tr [:homograph-disambiguation])} (subs (tr [:homograph-disambiguation]) 0 1)]]
           [:th (tr [:book])]
           [:th [:abbr {:title (tr [:local])} (subs (tr [:local]) 0 1)]]
           [:th (tr [:action])]]]
         [:tbody
          (for [{:keys [uuid]} @(rf/subscribe [::words-sorted])]
            ^{:key uuid} [word uuid])]]
        [submit-all/buttons (tr [:approve-all]) [::words-valid?] [::has-words?] [::save-all-words]]
        [pagination/pagination :confirm [::fetch-words]]])]))
