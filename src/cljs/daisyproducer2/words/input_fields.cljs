(ns daisyproducer2.words.input-fields
  (:require [daisyproducer2.i18n :refer [tr]]
            [re-frame.core :as rf]))

(rf/reg-sub
 ::word-field
 (fn [db [_ page id field-id]]
   (get-in db [:words page id field-id])))

(rf/reg-event-db
 ::set-word-field
 (fn [db [_ page id field-id value]]
   (assoc-in db [:words page id field-id] value)))

(defn local-field [page id]
  (let [value @(rf/subscribe [::word-field page id :islocal])
        html-id (str "local-field-" id)]
    [:<>
     [:label.is-sr-only {:for html-id} (tr [:local])]
     [:input {:type "checkbox"
              :id html-id
              :aria-checked value
              :checked value
              :on-change #(rf/dispatch [::set-word-field page id :islocal (not value)])}]]))

(defn input-field [page id field-id validator]
  (let [initial-value @(rf/subscribe [::word-field page id field-id])
        get-value (fn [e] (-> e .-target .-value))
        reset! #(rf/dispatch [::set-word-field page id field-id initial-value])
        save! #(rf/dispatch [::set-word-field page id field-id %])]
    (fn []
      (let [value @(rf/subscribe [::word-field page id field-id])
            valid? (validator value)
            changed? (not= initial-value value)
            klass (list (cond (not valid?) "is-danger"
                              changed? "is-warning")
                        ;; braille fields should be in mono space
                        (when (#{:contracted :uncontracted} field-id) "braille"))]
        [:div.field
         [:input.input {:type "text"
                        :aria-label (tr [field-id])
                        :class klass
                        :value value
                        :on-change #(save! (get-value %))
                        :on-key-down #(when (= (.-which %) 27) (reset!))}]
         (when-not valid?
           [:p.help.is-danger (tr [:input-not-valid])])]))))

(defn disabled-field [value]
  [:div.field
   [:input.input {:type "text" :value value :disabled "disabled"}]])
