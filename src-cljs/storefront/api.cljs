(ns storefront.api
  (:require [ajax.core :refer [GET POST PUT json-response-format]]
            [cljs.core.async :refer [put!]]
            [storefront.events :as events]
            [storefront.taxons :refer [taxon-name-from]]))

(def base-url "http://localhost:3005")
(def send-sonar-base-url "https://www.sendsonar.com/api/v1")
(def send-sonar-publishable-key "d7d8f2d0-9f91-4507-bc82-137586d41ab8")

(defn api-req [method path params success-handler]
  (method (str base-url path)
          {:handler success-handler
           :headers {"Accepts" "application/json"}
           :format :json
           :params params
           :response-format (json-response-format {:keywords? true})}))

(defn get-taxons [events-ch]
  (api-req
   GET
   "/product-nav-taxonomy"
   {}
   #(put! events-ch [events/api-success-taxons (select-keys % [:taxons])])))

(defn get-store [events-ch store-slug]
  (api-req
   GET
   "/stylist"
   {:store_slug store-slug}
   #(put! events-ch [events/api-success-store %])))

(defn get-products [events-ch taxon-path]
  (api-req
   GET
   "/products"
   {:taxon_name (taxon-name-from taxon-path)}
   #(put! events-ch [events/api-success-products (merge (select-keys % [:products])
                                                        {:taxon-path taxon-path})])))

(defn get-product [events-ch product-path]
  (api-req
   GET
   (str "/products")
   {:slug product-path}
   #(put! events-ch [events/api-success-product {:product-path product-path
                                                 :product %}])))

(defn select-sign-in-keys [args]
  (select-keys args [:email :token :store_slug :id]))

(defn sign-in [events-ch email password]
  (api-req
   POST
   "/login"
   {:email email
    :password password}
   #(put! events-ch [events/api-success-sign-in (select-sign-in-keys %)])))

(defn sign-up [events-ch email password password-confirmation]
  (api-req
   POST
   "/signup"
   {:email email
    :password password
    :password_confirmation password-confirmation}
   #(put! events-ch [events/api-success-sign-up (select-sign-in-keys %)])))

(defn forgot-password [events-ch email]
  (api-req
   POST
   "/forgot_password"
   {:email email}
   #(put! events-ch [events/api-success-forgot-password])))

(defn reset-password [events-ch password password-confirmation reset-token]
  (api-req
   POST
   "/reset_password"
   {:password password
    :password_confirmation password-confirmation
    :reset_password_token reset-token}
   #(put! events-ch [events/api-success-reset-password (select-sign-in-keys %)])))

(defn update-account [events-ch id email password password-confirmation token]
  (api-req
   PUT
   "/users"
   {:id id
    :email email
    :password password
    :password_confirmation password-confirmation
    :token token}
   #(put! events-ch [events/api-success-manage-account (select-sign-in-keys %)])))

(defn get-stylist-commissions [events-ch user-token]
  (api-req
   GET
   "/stylist/commissions"
   {:user-token user-token}
   #(put! events-ch [events/api-success-stylist-commissions
                     {:new-orders (% :new_orders)}])))

(defn get-sms-number [events-ch]
  (letfn [(normalize-number [x] ;; smooth out send-sonar's two different number formats
            (apply str (if (= "+" (first x))
                         (drop 3 x)
                         x)))
          (callback [resp]
            (put! events-ch
                  [events/api-success-sms-number
                   {:number (-> resp :available_number normalize-number)}]))]
    (GET (str send-sonar-base-url "/phone_numbers/available")
        {:handler callback
         :headers {"Accepts" "application/json"
                   "X-Publishable-Key" send-sonar-publishable-key}
         :format :json
         :response-format (json-response-format {:keywords? true})})))
