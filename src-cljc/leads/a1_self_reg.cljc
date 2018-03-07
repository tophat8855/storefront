(ns leads.a1-self-reg
  (:require #?@(:clj [[storefront.component-shim :as component]]
                :cljs [[storefront.browser.cookie-jar :as cookie-jar]
                       [storefront.component :as component]
                       [storefront.api :as api]
                       [storefront.history :as history]])
            [leads.header :as header]
            [storefront.components.footer :as footer]
            [storefront.assets :as assets]
            [storefront.platform.component-utils :as utils]
            [storefront.platform.messages :as messages]
            [storefront.effects :as effects]
            [storefront.events :as events]
            [storefront.keypaths]
            [storefront.transitions :as transitions]
            [leads.keypaths :as keypaths]
            [storefront.components.ui :as ui]))

(defn ^:private sign-up-section
  [{:keys [first-name last-name phone email password field-errors]} focused]
  [:div
   (ui/text-field-group {:type     "text"
                         :label    "First Name *"
                         :id       "first_name"
                         :name     "first-name"
                         :required true
                         :errors   (get field-errors ["first-name"])
                         :keypath  keypaths/stylist-first-name
                         :focused  focused
                         :value    first-name}
                        {:type     "text"
                         :label    "Last Name *"
                         :id       "last_name"
                         :name     "last-name"
                         :required true
                         :errors   (get field-errors ["last-name"])
                         :keypath  keypaths/stylist-last-name
                         :focused  focused
                         :value    last-name})
   (ui/text-field {:data-test "phone"
                   :errors    (get field-errors ["phone"])
                   :id        "phone"
                   :keypath   keypaths/stylist-phone
                   :focused   focused
                   :label     "Mobile Phone Number *"
                   :name      "phone"
                   :required  true
                   :type      "tel"
                   :value     phone})
   (ui/text-field {:data-test "email"
                   :errors    (get field-errors ["email"])
                   :id        "email"
                   :keypath   keypaths/stylist-email
                   :focused   focused
                   :label     "Email *"
                   :name      "email"
                   :required  true
                   :type      "email"
                   :value     email})
   (ui/text-field {:data-test "password"
                   :errors    (get field-errors ["password"])
                   :id        "password"
                   :keypath   keypaths/stylist-password
                   :focused   focused
                   :label     "Password *"
                   :name      "password"
                   :required  true
                   :type      "password"
                   :value     password
                   :hint      password})])

(defn ^:private referral-section
  [{:keys [referred referrers-phone field-errors]} focused]
  [:div
   (ui/check-box {:data-test "referred"
                  :keypath   keypaths/stylist-referred
                  :label     "Yes, somebody referred me to Mayvenn"
                  :value     referred
                  :label-classes "h5"})
   (when referred
     (ui/text-field {:data-test "referrers-phone"
                     :errors    (get field-errors ["referrers-phone"])
                     :id        "referrers-phone"
                     :keypath   keypaths/stylist-referrers-phone
                     :focused   focused
                     :label     "Referrer's Mobile Phone Number *"
                     :name      "referrers-phone"
                     :type      "referrers-phone"
                     :value     referrers-phone}))])

(defn ^:private contact-section
  [{:keys [address1 address2 city zip state field-errors]} states focused]
  [:div.pb3
   [:div.center.pb3 "Contact information"
    [:br]
    "We'll need to know where to send your commission checks"]
   (ui/text-field {:data-test "address1"
                   :errors    (get field-errors ["address1"])
                   :id        "address1"
                   :keypath   keypaths/stylist-address1
                   :focused   focused
                   :label     "Street Address *"
                   :name      "address1"
                   :required  true
                   :type      "text"
                   :value     address1})
   (ui/text-field {:data-test "address2"
                   :errors    (get field-errors ["address2"])
                   :id        "address2"
                   :keypath   keypaths/stylist-address2
                   :focused   focused
                   :label     "Apt or Suite #"
                   :name      "address2"
                   :required  false
                   :type      "text"
                   :value     address2})
   (ui/text-field {:data-test "city"
                   :errors    (get field-errors ["city"])
                   :id        "city"
                   :keypath   keypaths/stylist-city
                   :focused   focused
                   :label     "City *"
                   :name      "city"
                   :required  true
                   :type      "text"
                   :value     city})
   [:div
    [:div.col.col-6.pr1
     (ui/select-field {:data-test   "state"
                       :errors      (get field-errors ["state"])
                       :id          :state
                       :keypath     keypaths/stylist-state
                       :focused     focused
                       :label       "State *"
                       :options     states
                       :placeholder "State *"
                       :required    true
                       :value       state})]
    [:div.col.col-6.pr1
     (ui/text-field {:data-test "zip"
                     :errors    (get field-errors ["zip"])
                     :id        "zip"
                     :keypath   keypaths/stylist-zip
                     :focused   focused
                     :label     "ZIP code *"
                     :name      "zip"
                     :required  true
                     :type      "text"
                     :value     zip})]]])

(defn ^:private stylist-details-section
  [{:keys [birthday licensed payout-method venmo-phone paypal-email slug field-errors]} focused]
  [:div
   [:div.flex.flex-column.items-center.col-12
    (ui/text-field {:data-test "birth-date"
                    :errors    (get field-errors ["birthday"])
                    :id        "birth-date"
                    :keypath   keypaths/stylist-birthday
                    :focused   focused
                    :label     "Birthday"
                    :name      "birth-date"
                    :required  true
                    :type      "date"
                    :value     birthday})]
   [:div.pb2.center
    [:div.pb2 "Are you a licensed hair stylist?"]
    (ui/radio-group {:group-name    "licensed"
                     :checked-value licensed
                     :keypath       keypaths/stylist-licensed}
                    [{:id "yes" :label "Yes" :value true}
                     {:id "no" :label "No" :value false}])]
   [:div.pb3.center
    [:div.pb3
     [:div.pb2 "How do you want to get paid once you start earning commissions?"]
     (ui/radio-group {:group-name    "payout-method"
                      :keypath       keypaths/stylist-payout-method
                      :checked-value payout-method
                      :required      true}
                     [{:id "venmo" :label "Venmo" :value "venmo"}
                      {:id "paypal" :label "Paypal" :value "paypal"}
                      {:id "check" :label "Check" :value "check"}])]
    (case payout-method
      "venmo"  (ui/text-field {:data-test "venmo-phone"
                               :errors    (get field-errors ["venmo-phone"])
                               :id        "venmo-phone"
                               :keypath   keypaths/stylist-venmo-phone
                               :focused   focused
                               :label     "Venmo phone number *"
                               :name      "venmo-phone"
                               :required  true
                               :type      "tel"
                               :value     venmo-phone})
      "paypal" (ui/text-field {:data-test "paypal-email"
                               :errors    (get field-errors ["paypal-email"])
                               :id        "paypal-email"
                               :keypath   keypaths/stylist-paypal-email
                               :focused   focused
                               :label     "Paypal email address *"
                               :name      "paypal-email"
                               :required  true
                               :type      "email"
                               :value     paypal-email})
      [:span])]
   [:div.pb2.center
    [:div.pb2 "What do you want to name your store?"
     [:br]
     "(We'll check to see if it's available)"]
    [:div.flex
     [:div.flex-auto
      (ui/text-field {:data-test     "slug"
                      :wrapper-class "rounded-left"
                      :errors        (get field-errors ["slug"])
                      :id            "slug"
                      :keypath       keypaths/stylist-slug
                      :focused       focused
                      :label         (if (empty? slug) "yourstorename" "Store Name")
                      :name          "slug"
                      :required      true
                      :type          "text"
                      :value         slug})]
     [:div.rounded.rounded-right.border.border-gray.bg-light-gray.x-group-item.p2.floating-label-height
      ".mayvenn.com"]]]])

(defn ^:private component
  [{:keys [focused sign-up referral contact stylist-details states]} owner opts]
  (component/create
   [:div.bg-teal.white.pb4
    [:div.max-580.mx-auto
     [:div.px4.registration-header.medium.center
      [:img.my2 {:src (assets/path "/images/leads/logo-knockout.png")}]
      [:div.h5.center.mb3
       "Congrats again on being pre-approved to become a Mayvenn stylist. "
       "Please continue your application below. "
       "We'll review your application and get back to you in 1 to 2 business days. "
       "If you're approved, we'll get in touch with final steps like activating your "
       "custom website, setting up your account, and helping you kickstart your sales."]]
     [:div.bg-white.black.p4.my2
      [:div.center.pb2 "Let's get started with some basic information"]
      [:form {:action    ""
              :method    "POST"
              :role      "form"
              :on-submit (utils/send-event-callback events/leads-a1-control-self-registration-submit {})}
       (sign-up-section sign-up focused)
       (referral-section referral focused)
       (contact-section contact states focused)
       (stylist-details-section stylist-details focused)

       [:div.py3.border-top.border-gray
        (ui/submit-button "Submit registration"
                          {:data-test "registration-submit"
                           :class     "h2"})]]]]]))

(defn ^:private sign-up-query [data]
  {:first-name (get-in data keypaths/stylist-first-name)
   :last-name  (get-in data keypaths/stylist-last-name)
   :phone      (get-in data keypaths/stylist-phone)
   :email      (get-in data keypaths/stylist-email)
   :password   (get-in data keypaths/stylist-password)})

(defn ^:private contact-query [data]
  {:address1 (get-in data keypaths/stylist-address1)
   :address2 (get-in data keypaths/stylist-address2)
   :city     (get-in data keypaths/stylist-city)
   :zip      (get-in data keypaths/stylist-zip)
   :state    (get-in data keypaths/stylist-state)})

(defn ^:private stylist-details-query [data]
  {:birthday      (get-in data keypaths/stylist-birthday)
   :licensed     (get-in data keypaths/stylist-licensed)
   :payout-method (get-in data keypaths/stylist-payout-method)
   :venmo-phone   (get-in data keypaths/stylist-venmo-phone)
   :paypal-email  (get-in data keypaths/stylist-paypal-email)
   :slug          (get-in data keypaths/stylist-slug)})

(defn ^:private query [data]
  (let [field-errors (get-in data storefront.keypaths/field-errors)]
    {:focused         (get-in data storefront.keypaths/ui-focus)
     :states          (map (juxt :name :abbr) (get-in data storefront.keypaths/states))
     :referral        {:referred        (get-in data keypaths/stylist-referred)
                       :referrers-phone (get-in data keypaths/stylist-referrers-phone)
                       :field-errors    field-errors}
     :sign-up         (merge (sign-up-query data)
                             {:field-errors field-errors})
     :contact         (merge (contact-query data)
                             {:field-errors field-errors})
     :stylist-details (merge (stylist-details-query data)
                             {:field-errors field-errors})}))

(defn built-component [data opts]
  (component/build component (query data) opts))

(defn handle-referral [{:keys [referred] :as registration}]
  (if referred
    registration
    (-> registration
        (assoc :referred false)
        (dissoc :referrers-phone))))

(defn handle-address-2 [{:keys [address2] :as registration}]
  (if (empty? address2)
    (dissoc registration :address2)
    registration))

(defn handle-payout-method [{:keys [payout-method] :as registration}]
  (condp = payout-method
    "venmo"  (dissoc registration :paypal-email)
    "paypal" (dissoc registration :venmo-phone)
    "check"  (dissoc registration :paypal-email :venmo-phone)
    registration))

#?(:cljs
   (defmethod transitions/transition-state events/navigate-leads-a1-self-reg
     [_ _ {:keys [submitted-lead]} app-state]
     (-> app-state
         (update-in keypaths/stylist merge (get-in app-state keypaths/remote-lead))
         (update-in keypaths/stylist merge submitted-lead))))

#?(:cljs
   (defmethod effects/perform-effects events/navigate-leads-a1-self-reg
     [dispatch event args _ app-state]
     (api/get-states (get-in app-state storefront.keypaths/api-cache))))

#?(:cljs
   (defmethod effects/perform-effects events/api-success-leads-a1-lead-registered
     [_ event {:keys [lead] :as old-lead} _ app-state]
     (let [lead (or lead old-lead)]
       (cookie-jar/save-lead (get-in app-state storefront.keypaths/cookie)
                             {"lead-id" (:id lead)})
       (history/enqueue-navigate events/navigate-leads-a1-resolve))))

#?(:cljs
   (defmethod effects/perform-effects events/leads-a1-control-self-registration-submit
     [dispatch event args _ app-state]
     (let [{:keys [id] :as lead} (get-in app-state keypaths/remote-lead)
           registration                  (-> (get-in app-state keypaths/stylist)
                                             handle-referral
                                             handle-address-2
                                             handle-payout-method)]
       (api/advance-lead-registration {:lead-id    id
                                       :session-id (get-in app-state storefront.keypaths/session-id)
                                       :step-data  {:registration registration}}
                                      (fn [response-body]
                                        (messages/handle-message events/api-success-leads-a1-lead-registered
                                                                 response-body))))))

(defmethod transitions/transition-state events/api-success-leads-a1-lead-registered
  [_ event {:keys [lead user-id token]} app-state]
  (-> app-state
      (assoc-in keypaths/remote-lead lead)
      (assoc-in keypaths/remote-user-token token)
      (assoc-in keypaths/remote-user-id user-id)))


