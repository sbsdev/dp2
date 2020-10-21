(ns dp2.words.unknown
  (:require
   [re-frame.core :as rf]
   [clojure.string :as string]
   [ajax.core :as ajax]
   [dp2.words :as words]))

(defn hyphenation-valid?
  "Return true if the `hyphenation` is not blank, is equal to
  `word` (modulo the hyphenation marks) and contains at least one of
  the letters 'a-z', '\u00DF-\u00FF' or '-'. Also each '-' in the
  hyphenation should be surrounded by letters."
  [hyphenation word]
  (and (not (string/blank? hyphenation))
       (= word (string/replace hyphenation "-" ""))
       (not (string/starts-with? hyphenation "-"))
       (not (string/ends-with? hyphenation "-"))
       (not (string/includes? hyphenation "--"))
       (some? (re-matches #"[a-z\xC0-\xFF\u0100-\u017F-]+" hyphenation))))

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
                                 :document-id :islocal]))
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
 ::words
 (fn [db _]
   (-> db :words :unknown)))

(rf/reg-event-db
  ::set-new-hyphenation
  (fn [db [_ uuid hyphenation]]
    (let [suggested (get-in db [:words :unknown uuid :hyphenated])]
      (if (= suggested hyphenation)
        (update-in db [:words :unknown uuid] dissoc :new-hyphenation)
        (update-in db [:words :unknown uuid] assoc :new-hyphenation hyphenation)))))

(rf/reg-event-db
  ::reset-new-hyphenation
  (fn [db [_ uuid]]
    (update-in db [:words :unknown uuid] dissoc :new-hyphenation)))

(rf/reg-sub
 ::suggested-hyphenation
 (fn [db [_ uuid]]
   (get-in db [:words :unknown uuid :hyphenated])))

(rf/reg-sub
 ::new-hyphenation
 (fn [db [_ uuid]]
   (get-in db [:words :unknown uuid :new-hyphenation])))

(rf/reg-sub
 ::hyphenation
 (fn [[_ id]]
   [(rf/subscribe [::suggested-hyphenation id]) (rf/subscribe [::new-hyphenation id])])
 (fn [[suggested new] _]
   (or new suggested)))

(rf/reg-sub
 ::untranslated
 (fn [db [_ uuid]]
   (get-in db [:words :unknown uuid :untranslated])))

(rf/reg-sub
 ::valid-hyphenation
 (fn [[_ id]]
   [(rf/subscribe [::hyphenation id]) (rf/subscribe [::untranslated id])])
 (fn [[hyphenation word] _]
   (hyphenation-valid? hyphenation word)))

(defn hyphenation-field [id]
  (let [value @(rf/subscribe [::hyphenation id])
        changed? @(rf/subscribe [::new-hyphenation id])
        valid? @(rf/subscribe [::valid-hyphenation id])
        klass (cond
                (not valid?) "is-danger"
                changed? "is-success")
        gettext (fn [e] (-> e .-target .-value))
        emit    (fn [e] (rf/dispatch [::set-new-hyphenation id (gettext e)]))
        reset   (fn [] (rf/dispatch [::reset-new-hyphenation id]))]
    [:div.field
     [:input.input {:type "text"
                    :class klass
                    :value value
                    :on-change emit
                    :on-key-down #(when (= (.-which %) 27) (reset))}]
     (when (not valid?)
       [:p.help.is-danger "Hyphenation not valid"])]))

(rf/reg-sub
 ::suggested-braille
 (fn [db [_ uuid]]
   (get-in db [:words :unknown uuid :braille])))

(rf/reg-sub
 ::new-braille
 (fn [db [_ uuid]]
   (get-in db [:words :unknown uuid :new-braille])))

(rf/reg-sub
 ::braille
 (fn [[_ id]]
   [(rf/subscribe [::suggested-braille id]) (rf/subscribe [::new-braille id])])
 (fn [[suggested new] _]
   (or new suggested)))

(rf/reg-event-db
  ::set-new-braille
  (fn [db [_ uuid braille]]
    (let [suggested (get-in db [:words :unknown uuid :braille])]
      (if (= suggested braille)
        (update-in db [:words :unknown uuid] dissoc :new-braille)
        (update-in db [:words :unknown uuid] assoc :new-braille braille)))))

(rf/reg-event-db
  ::reset-new-braille
  (fn [db [_ uuid]]
    (update-in db [:words :unknown uuid] dissoc :new-braille)))

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

(rf/reg-sub
 ::valid
 (fn [[_ id]]
   [(rf/subscribe [::valid-braille id]) (rf/subscribe [::valid-hyphenation id])])
 (fn [[valid-braille valid-hyphenation] _]
   (and valid-braille valid-hyphenation)))

(defn buttons [id word]
  (let [valid @(rf/subscribe [::valid id])]
    [:td
     [:div.buttons.has-addons
      [:button.button.is-success
       {:disabled (not valid)
        :on-click (fn [e] (rf/dispatch [::save-word (assoc word :islocal false)]))}
       [:span.icon [:i.mi.mi-done]]
       #_[:span "Approve"]]
      [:button.button.is-warning
       {:disabled (not valid)
        :on-click (fn [e] (rf/dispatch [::save-word (assoc word :islocal true)]))}
       [:span.icon [:i.mi.mi-book]]
       #_[:span "Local"]]
      [:button.button.is-danger
       {:on-click (fn [e] (rf/dispatch [::ignore-word id]))}
       [:span.icon [:i.mi.mi-cancel]]
       #_[:span "Ignore"]]]])  )

(defn document-unknown-words [document]
  (let [words @(rf/subscribe [::words])]
    [:div.block
     [:table.table.is-striped
      [:thead
       [:tr
        [:th "Untranslated"] [:th "Braille"] [:th "Hyphenated"] [:th "Type"] [:th "Homograph Disambiguation"] [:th "Action"]]]
      [:tbody
       (for [{:keys [uuid untranslated braille type homograph-disambiguation] :as word} (sort-by :untranslated (vals words))]
         ^{:key uuid}
         [:tr [:td untranslated]
          [:td [braille-field uuid]]
          [:td [hyphenation-field uuid]]
          [:td (get words/type-mapping type "Unknown")]
          [:td homograph-disambiguation]
          [buttons uuid word]
          ])]]]))
