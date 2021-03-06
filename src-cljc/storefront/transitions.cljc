(ns storefront.transitions
  (:require [storefront.keypaths :as keypaths]
            [storefront.events :as events]
            [storefront.accessors.pixlee :as pixlee]
            [spice.core :as spice]))

(defmulti transition-state #?(:cljs identity
                              :clj (comp first vector)))

(defmethod transition-state :default
  [dispatch event args app-state]
  ;; (js/console.log "IGNORED transition" (clj->js event) (clj->js args)) ;; enable to see ignored transitions
  app-state)

(defmethod transition-state events/navigate-shop-by-look [_ event {:keys [album-slug] :as args} app-state]
  (-> app-state
      (assoc-in keypaths/selected-album-slug (keyword album-slug))
      (assoc-in keypaths/selected-look-id nil)))

(defmethod transition-state events/navigate-shop-by-look-details [_ event {:keys [album-slug look-id]} app-state]
  (let [shared-cart-id (:shared-cart-id (pixlee/selected-look app-state))
        current-shared-cart (get-in app-state keypaths/shared-cart-current)]
    (cond-> app-state
      :always
      (assoc-in keypaths/selected-look-id (spice/parse-int look-id))

      (not= shared-cart-id (:number current-shared-cart))
      (assoc-in keypaths/shared-cart-current nil))))

;; Utilities

(defn sign-in-user
  [app-state {:keys [email token store-slug id total-available-store-credit must-set-password]}]
  (-> app-state
      (assoc-in keypaths/user-id id)
      (assoc-in keypaths/user-email email)
      (assoc-in keypaths/user-token token)
      (assoc-in keypaths/user-must-set-password must-set-password)
      (assoc-in keypaths/user-store-slug store-slug)
      (assoc-in keypaths/checkout-as-guest false)
      #?(:cljs
         (assoc-in keypaths/user-total-available-store-credit (js/parseFloat total-available-store-credit)))))

(defn clear-fields [app-state & fields]
  (reduce #(assoc-in %1 %2 "") app-state fields))
