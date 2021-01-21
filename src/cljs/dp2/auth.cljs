(ns dp2.auth
  (:require [ajax.core :as ajax]
            [dp2.events]
            [dp2.words.notifications :as notifications]
            [dp2.i18n :refer [tr]]
            [dp2.utils :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(rf/reg-event-fx
  ::login
  (fn [{:keys [db]} [_ username password]]
    {:http-xhrio {:method          :post
                  :uri             "/api/login"
                  :format          (ajax/json-request-format)
                  :params          {:username username :password password}
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::login-success]
                  :on-failure      [::login-failure :login]}}))

(rf/reg-event-db
 ::logout
 (fn [db [_]]
   (dissoc db :credentials)))

(rf/reg-event-fx
 ::login-success
 (fn [{:keys [db]} [_ {:keys [token user]}]]
   {:db (-> db
            (assoc-in [:credentials :token] token)
            (assoc-in [:credentials :user] user)
            ;; since JSON converts sets into arrays we have to convert
            ;; it back to a set
            (update-in [:credentials :user :roles] #(apply hash-set %)))
    :common/navigate-fx! [:documents]}))

(rf/reg-event-db
 ::login-failure
 (fn [db [_ request-type response]]
   (assoc-in db [:errors request-type] (get-in response [:response :message]))))

(rf/reg-sub
 ::authenticated?
 (fn [db [_ _]]
   (-> db :credentials some?)))

(rf/reg-sub
 ::is-admin?
 (fn [db [_ _]]
   (-> db :credentials :user utils/is-admin?)))

(rf/reg-sub
 ::user
 (fn [db [_ _]]
   (-> db :credentials :user)))

(rf/reg-sub
 ::user-initials
 :<- [::user]
 (fn [user] (-> user :initials)))


(defn auth-header [db]
  (let [token (get-in db [:credentials :token])]
    {:Authorization (str "Token " token)}))

(defn user-buttons []
  (let [initials @(rf/subscribe [::user-initials])]
    [:div.buttons
     (if initials
       #_[:div.navbar-item.has-dropdown.is-hoverable
        [:a.navbar-link.has-text-weight-bold.is-arrowless initials]
        [:div.navbar-dropdown
         [:a.navbar-item {:on-click #(rf/dispatch [::logout])} (tr [:logout])]]]
       [:<>
        [:a.button.is-primary initials]
        [:a.button.is-light {:on-click #(rf/dispatch [::logout])} (tr [:logout])]]
       [:a.button {:on-click #(rf/dispatch [:common/navigate! :login])} (tr [:login])])]))

(defn login-page []
  (let [username (r/atom "")
        password (r/atom "")]
    (fn []
      (let [errors? @(rf/subscribe [::notifications/errors?])]
        [:section.section>div.container>div.content
         (when errors?
           [notifications/error-notification])
         [:div.field.has-icons-left
          [:label.label (tr [:username])]
          [:input.input
           {:type "text"
            :on-change #(reset! username (-> % .-target .-value))
            :on-key-down #(case (.-which %)
                            27 (reset! username "")
                            nil)
            :value @username}]]
         [:div.field.has-icons-left
          [:label.label (tr [:password])]
          [:input.input
           {:type "password"
            :on-change #(reset! password (-> % .-target .-value))
            :on-key-down #(case (.-which %)
                            27 (reset! password "")
                            nil)
            :value @password}]]
         [:div.field.is-grouped
          [:div.control
           [:button.button.is-link
            {:on-click (fn [e] (rf/dispatch [::login @username @password]))}
            (tr [:login])]]]]))))
