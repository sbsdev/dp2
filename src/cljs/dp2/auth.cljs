(ns dp2.auth
  (:require [ajax.core :as ajax]
            [dp2.words.notifications :as notifications]
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

(rf/reg-event-db
 ::login-success
 (fn [db [_ {:keys [token user]}]]
   (-> db
       (assoc-in [:credentials :token] token)
       (assoc-in [:credentials :user] user))))

(rf/reg-event-db
 ::login-failure
 (fn [db [_ request-type response]]
   (assoc-in db [:errors request-type] (get response :status-text))))

(rf/reg-sub
 ::authenticated?
 (fn [db [_ id]]
   (->> db :credentials some?)))

(rf/reg-sub
  ::token
  (fn [db _]
    (->> db :credentials :token)))

(rf/reg-sub
 ::user-initials
 (fn [db [_ id]]
   (->> db :credentials :user :initials)))

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
         [:a.navbar-item {:on-click #(rf/dispatch [::logout])} "Logout"]]]
       [:<>
        [:a.button.is-primary initials]
        [:a.button.is-light {:on-click #(rf/dispatch [::logout])} "Log out"]]
       [:a.button {:href "#/login"} "Log in"])]))

(defn login-page []
  (let [errors? @(rf/subscribe [::notifications/errors?])
        username (r/atom "")
        password (r/atom "")]
    (fn []
      [:section.section>div.container>div.content
       (cond
         errors? [notifications/error-notification]
         :else
         [:<>
          [:div.field.has-icons-left
           [:label.label "Username"]
           [:input.input
             {:type "text"
              :on-change #(reset! username (-> % .-target .-value))
              :on-key-down #(case (.-which %)
                              27 (reset! username "")
                              nil)
              :value @username}]]
          [:div.field.has-icons-left
           [:label.label "Password"]
           [:input.input
             {:type "password"
              :on-change #(reset! password (-> % .-target .-value))
              :on-key-down #(case (.-which %)
                              27 (reset! password "")
                              nil)
              :value @password}]]
          [:div.field.is-grouped
           [:div.control
            [:a.button.is-link
             {:on-click (fn [e] (rf/dispatch [::login @username @password]))
              :href "#/"}
             "Submit"]]]])])))
