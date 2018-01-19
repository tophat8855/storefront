(ns storefront.components.stylist.cash-out
  (:require [storefront.component :as component]
            [storefront.components.ui :as ui]
            [storefront.components.money-formatters :as mf]))

(defn component [{:keys [payout-attributes]} owner opts]
  (component/create
   [:div.container.mb4.px3
    [:h3.my4 "Cash Out Your Earnings"]
    [:div.col-12.pt2.inline-block
     [:div.col.col-8
      [:div" Mayvenn InstaPay"]
      [:div.h6
       (str "Linked Card: xxxx-xxxx-xxxx-" (:last4 payout-attributes))]]
     [:div.h2.col.col-4.teal.right-align (mf/as-money 505.60)]]

    [:div.h6.center.navy
     "Instant: Funds typically arrive in minutes"]

    [:div.py3
     (ui/teal-button {:class "light"} "Cash Out")]

    [:div.h6.gray "Transfers may take up to 30 minutes and vary by bank"]]))

(defn query [data]
  {:payout-attributes {:last4 "1234"}
   :payout-method     "green_dot"})

(defn built-component [data opts]
  (component/build component (query data) opts))
