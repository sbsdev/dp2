(ns dp2.words.local
  (:require
   [re-frame.core :as rf]
   [clojure.string :as string]
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
  (fn [_ [_ id]]
    {:http-xhrio {:method          :get
                  :uri             (str "/api/documents/" id "/words")
                  :params          {:grade 2}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::set-words]}}))

(rf/reg-event-fx
  ::save-word
  (fn [{:keys [db]} [_ word]]
    (let [uuid (:uuid word)
          cleaned (-> word
                      (select-keys [:untranslated :braille :grade :type :homograph-disambiguation
                                 :document-id :islocal]))
          document-id (:document-id word)]
      {:db (assoc-in db [:words :local uuid] word)
       :http-xhrio {:method          :put
                    :format          (ajax/json-request-format)
                    :uri             (str "/api/documents/" document-id "/words")
                    :params          cleaned
                    :response-format (ajax/json-response-format {:keywords? true})
                    }})))

(rf/reg-event-fx
  ::init-words
  (fn [_ [_ id]]
    {:dispatch [::fetch-words id]}))

(rf/reg-sub
 ::words
 (fn [db _]
   (-> db :words :local)))

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

(def valid-braille-re
  #"-?[A-Z0-9&%\[^\],;:/?+=\(*\).\\@#\"!>$_<\'àáâãåæçèéêëìíîïðñòóôõøùúûýþÿœāăąćĉċčďđēėęğģĥħĩīįıĳĵķĺļľŀłńņňŋōŏőŕŗřśŝşšţťŧũūŭůűųŵŷźżžǎẁẃẅỳ┊]+")

(defn braille-valid?
  "Return true if `s` is valid ascii braille."
  [s]
  (and (not (string/blank? s))
       (some? (re-matches valid-braille-re s))))

(rf/reg-sub
 ::valid-braille
 (fn [[_ id]]
   [(rf/subscribe [::braille id])])
 (fn [[braille] _]
   (braille-valid? braille)))

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

(defn buttons [id word]
  (let [valid @(rf/subscribe [::valid-braille id])
        changed? @(rf/subscribe [::new-braille id])
        islocal (:islocal word)]
    [:div.buttons.has-addons
     [:button.button.is-success
      {:disabled (or (not changed?) (not valid))
       :on-click (fn [e] (rf/dispatch [::save-word word]))}
       [:span.icon [:i.mi.mi-done]]
      #_[:span "Approve"]]
     [:button.button.is-warning
      {:disabled (not valid)
       :class (when-not islocal "is-light")
       :on-click (fn [e] (rf/dispatch [::save-word (assoc word :islocal (not islocal))]))}
      [:span.icon [:i.mi.mi-book]]
      #_[:span "Local"]]
     [:button.button.is-danger
      {:on-click (fn [e] (rf/dispatch [::ignore-word id]))}
      [:span.icon [:i.mi.mi-cancel]]
      #_[:span "Ignore"]]]))


(defn local-words []
  (let [words @(rf/subscribe [::words])]
    [:div.block
     [:table.table.is-striped
      [:thead
       [:tr
        [:th "Untranslated"] [:th "Braille"] [:th "Type"] [:th "Homograph Disambiguation"] [:th "Action"]]]
      [:tbody
       (for [{:keys [uuid untranslated braille type homograph-disambiguation] :as word} (sort-by :untranslated (vals words))]
         ^{:key untranslated}
         [:tr
          [:td untranslated]
          [:td [braille-field uuid]]
          [:td (get words/type-mapping type "Unknown")]
          [:td homograph-disambiguation]
          [:td [buttons uuid word]]
          ])]]]))