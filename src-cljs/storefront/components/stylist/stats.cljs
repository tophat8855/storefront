(ns storefront.components.stylist.stats
  (:require [om.core :as om]
            [sablono.core :refer [html]]
            [storefront.components.money-formatters :as mf]
            [storefront.components.formatters :as f]
            [storefront.components.svg :as svg]
            [storefront.components.ui :as ui]
            [storefront.platform.carousel :as carousel]))

(def payday 3) ;; 3 -> Wednesday in JS

(defn days-till-payout []
  (let [dow (.getDay (js/Date.))]
    (mod (+ 7 payday (- dow))
         7)))

(defn in-x-days []
  (let [days (days-till-payout)]
    (condp = days
      0 "today"
      1 "tomorrow"
      (str "in " days " days"))))

(def stat-card "left col-12 relative")
(def re-center-money {:style {:margin-left "-5px"}})

(defn previous-payout-slide [{:keys [amount date]}]
  [:.my4
   {:key "previous-payout" :class stat-card}
   [:.p1 "LAST PAYMENT"]
   (if (> amount 0)
     [:div
      [:.py2.h0 re-center-money (ui/big-money amount)]
      [:div "On " (f/long-date date)]]
     [:div
      [:.py2.h0 svg/large-payout]
      [:div "Your last payment will show here."]])])

(defn next-payout-slide [{:keys [payout-method amount]}]
  [:.my4
   {:key "next-payment" :class stat-card}
   [:.p1 "AVAILABLE EARNINGS"]
   (if (> amount 0)
     [:div
      [:.py2.h0 re-center-money (ui/big-money amount)]
      (if (= "green_dot" payout-method)
        [:div.col-5.mt1.mb2.mx-auto
         (ui/light-ghost-button {:class "rounded-1 p1 light"}
                                [:span.ml1 "Cash Out Now"]
                                [:span.ml2
                                 (svg/dropdown-arrow {:class  "stroke-white"
                                                      :width  "12px"
                                                      :height "10px"
                                                      :style  {:transform "rotate(-90deg)"}})])]
        [:div "Payment " (in-x-days)])]
     [:div
      [:.py2.h0 svg/large-dollar]
      [:div "See your available earnings here."]])])

(defn lifetime-payouts-slide [{:keys [amount]}]
  [:.my4
   {:class stat-card :key "render-stat"}
   [:.p1 "LIFETIME COMMISSIONS"]
   (if (> amount 0)
     [:div
      [:.py2.h0 re-center-money [:div.line-height-1 (mf/as-money-without-cents amount)]]
      [:div "Sales since you joined Mayvenn"]]
     [:div
      [:.py2.h0 svg/large-percent]
      [:div "All sales since you joined Mayvenn."]])])

(defn stylist-dashboard-stats-component [{:keys [stats payout-method]} owner]
  (om/component
   (html
    (let [items [[:div.my4.clearfix
                  (next-payout-slide {:amount        (-> stats :next-payout :amount)
                                      :payout-method payout-method})]
                 [:div.my4.clearfix
                  (previous-payout-slide (:previous-payouts stats))]
                 [:div.my4.clearfix
                  (lifetime-payouts-slide (:lifetime-payouts stats))]]]
      [:div.bg-teal.white.center
       [:div.bg-darken-bottom-1
        (om/build carousel/component
                  {:slides   items
                   :settings {:arrows true
                              :dots   true
                              :swipe  true}}
                  {:react-key "stat-swiper"})]]))))
