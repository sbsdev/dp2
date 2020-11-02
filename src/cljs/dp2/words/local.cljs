(ns dp2.words.local
  (:require
   [re-frame.core :as rf]
   [ajax.core :as ajax]
   [dp2.words :as words]))

(rf/reg-event-db
  ::set-words
  (fn [db [_ words]]
    (let [words (->> words
                     (map #(assoc % :uuid (str (random-uuid)))))]
      (assoc-in db [:words :local] (zipmap (map :uuid words) words)))))

(rf/reg-event-fx
  ::fetch-words
  (fn [{:keys [db]} [_ id]]
    (let [grade (-> db :current-grade)]
      {:http-xhrio {:method          :get
                    :uri             (str "/api/documents/" id "/words")
                    :params          {:grade grade}
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::set-words]}})))

(rf/reg-event-fx
  ::save-word
  (fn [{:keys [db]} [_ id]]
    (let [word (get-in db [:words :local id])
          cleaned (-> word
                      (select-keys [:untranslated :braille :grade :type :homograph-disambiguation
                                    :document-id :islocal :hyphenated :spelling]))
          document-id (:document-id word)]
      {:http-xhrio {:method          :put
                    :format          (ajax/json-request-format)
                    :uri             (str "/api/documents/" document-id "/words")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    }})))

(rf/reg-event-fx
  ::delete-word
  (fn [{:keys [db]} [_ id]]
    (let [word (get-in db [:words :local id])
          cleaned (-> word
                      (select-keys [:untranslated :braille :grade :type :homograph-disambiguation
                                    :document-id :islocal :hyphenated :spelling]))
          document-id (:document-id word)]
      {:http-xhrio {:method          :delete
                    :format          (ajax/json-request-format)
                    :uri             (str "/api/documents/" document-id "/words")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    :on-success      [::ack-delete id]
                    }})))

(rf/reg-event-db
  ::ack-delete
  (fn [db [_ uuid]]
    (update-in db [:words :local] dissoc uuid)))

(rf/reg-event-fx
  ::init-words
  (fn [_ [_ id]]
    {:dispatch [::fetch-words id]}))

(rf/reg-sub
 ::words
 (fn [db _]
   (->> db :words :local vals (sort-by :untranslated))))

(rf/reg-sub
 ::suggested-braille
 (fn [db [_ uuid]]
   (get-in db [:words :local uuid :braille])))

(rf/reg-sub
 ::new-braille
 (fn [db [_ uuid]]
   (get-in db [:words :local uuid :new-braille])))

(rf/reg-sub
 ::braille
 (fn [[_ id]]
   [(rf/subscribe [::suggested-braille id]) (rf/subscribe [::new-braille id])])
 (fn [[suggested new] _]
   (or new suggested)))

(rf/reg-event-db
  ::set-new-braille
  (fn [db [_ uuid braille]]
    (let [suggested (get-in db [:words :local uuid :braille])]
      (if (= suggested braille)
        (update-in db [:words :local uuid] dissoc :new-braille)
        (update-in db [:words :local uuid] assoc :new-braille braille)))))

(rf/reg-event-db
  ::reset-new-braille
  (fn [db [_ uuid]]
    (update-in db [:words :local uuid] dissoc :new-braille)))

(rf/reg-sub ::valid-braille
 (fn [[_ id]]
   [(rf/subscribe [::braille id])])
 (fn [[braille] _]
   (words/braille-valid? braille)))

(defn braille-field [id]
  (let [value @(rf/subscribe [::braille id])
        changed? @(rf/subscribe [::new-braille id])
        valid? @(rf/subscribe [::valid-braille id])
        klass (cond
                (not valid?) "is-danger"
                changed? "is-success")
        gettext (fn [e] (-> e .-target .-value))
        emit    (fn [e] (rf/dispatch [::set-new-braille id (gettext e)]))
        reset   (fn [] (rf/dispatch [::reset-new-braille id]))]
    [:div.field
     [:input.input {:type "text"
                    :class klass
                    :value value
                    :on-change emit
                    :on-key-down #(when (= (.-which %) 27) (reset))}]
     (when (not valid?)
       [:p.help.is-danger "Braille not valid"])]))

(rf/reg-event-db ::toggle-islocal
 (fn [db [_ uuid]]
   (let [islocal (get-in db [:words :local uuid :islocal])]
     (assoc-in db [:words :local uuid :islocal] (not islocal)))))

(rf/reg-sub ::islocal
 (fn [db [_ uuid]] (get-in db [:words :local uuid :islocal])))

(defn local-field [id]
  (let [value @(rf/subscribe [::islocal id])]
    [:input {:type "checkbox"
             :checked value
             :on-change (fn [e] (rf/dispatch [::toggle-islocal id]))}]))

(defn buttons [id]
  (let [valid @(rf/subscribe [::valid-braille id])
        changed? @(rf/subscribe [::new-braille id])]
    [:div.buttons.has-addons
     [:button.button.is-success
      {:disabled (or (not changed?) (not valid))
       :on-click (fn [e] (rf/dispatch [::save-word id]))}
       [:span.icon [:i.mi.mi-done]]
      #_[:span "Approve"]]
     [:button.button.is-danger
      {:on-click (fn [e] (rf/dispatch [::delete-word id]))}
      [:span.icon [:i.mi.mi-cancel]]
      #_[:span "Delete"]]]))

(rf/reg-sub ::word
 (fn [db [_ uuid]] (get-in db [:words :local uuid])))

(defn word [id]
  (let [{:keys [uuid untranslated braille type homograph-disambiguation islocal hyphenated]} @(rf/subscribe [::word id])]
    [:tr
     [:td untranslated]
     [:td [braille-field uuid]]
     [:td [:input.input {:type "text" :value hyphenated}]]
     [:td (get words/type-mapping type "Unknown")]
     [:td homograph-disambiguation]
     [:td [local-field uuid]]
     [:td {:width "8%"} [buttons uuid]]
     ]))

(defn local-words []
  (let [words @(rf/subscribe [::words])
        spelling (:spelling (first words))]
    [:div.block
     [:table.table.is-striped
      [:thead
       [:tr
        [:th "Untranslated"] [:th "Braille"]
        [:th "Hyphenated (" (words/spelling-string spelling) ")"] [:th "Type"]
        [:th "Homograph Disambiguation"] [:th "Local"] [:th "Action"]]]
      [:tbody
       (for [{:keys [uuid]} words]
         ^{:key uuid}
         [word uuid])]]]))
