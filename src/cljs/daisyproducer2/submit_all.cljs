(ns daisyproducer2.submit-all
  (:require [daisyproducer2.auth :as auth]
            [re-frame.core :as rf]))

(defn buttons [label valid-subscription has-words-subscription dispatch-event]
  (let [all-valid? @(rf/subscribe valid-subscription)
        has-words? @(rf/subscribe has-words-subscription)
        authenticated? @(rf/subscribe [::auth/authenticated?])]
    (when has-words?
      [:div.buttons.has-addons.is-right
       [:button.button.is-success
        {:disabled (not (and authenticated? all-valid?))
         :on-click (fn [e] (rf/dispatch dispatch-event))}
        [:span.icon {:aria-hidden true} [:i.mi.mi-done]]
        [:span label]]])))
