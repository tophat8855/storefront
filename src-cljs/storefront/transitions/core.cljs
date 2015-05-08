(ns storefront.transitions.core
  (:require [storefront.events :as events]
            [storefront.state :as state]
            [storefront.routes :as routes]
            [storefront.taxons :refer [taxon-path-for]]))

(defmulti transition-state identity)
(defmethod transition-state :default [dispatch event arg app-state]
  app-state)

(defmethod transition-state events/navigate [_ event args app-state]
  (assoc-in app-state state/navigation-event-path event))

(defmethod transition-state events/navigate-category [_ event {:keys [taxon-path]} app-state]
  (let [taxons (get-in app-state state/taxons-path)
        taxon (first (filter #(= (taxon-path-for %) taxon-path) taxons))]
    (assoc-in app-state state/browse-taxon-path taxon)))

(defmethod transition-state events/api-success-taxons [_ event args app-state]
  (assoc-in app-state state/taxons-path (:taxons args)))

(defmethod transition-state events/api-success-store [_ event args app-state]
  (assoc-in app-state state/store-path args))

(defmethod transition-state events/api-success-products [_ event {:keys [taxon-id products]} app-state]
  (update-in app-state state/products-for-taxons-path assoc taxon-id products))
