(ns storefront.components.stylist-kit
  (:require [storefront.components.product :as product]
            [storefront.components.utils :as utils]
            [storefront.components.formatters :refer [as-money-without-cents]]
            [storefront.components.ui :as ui]
            [storefront.keypaths :as keypaths]
            [storefront.request-keys :as request-keys]
            [storefront.events :as events]
            [storefront.utils.query :as query]
            [storefront.accessors.taxons :as taxons]
            [om.core :as om]
            [sablono.core :refer-macros [html]] ))

(defn kit-image [product]
  [:img.col-12 {:src (->> product :images first :large_url)
                :alt "Contents of stylist kit, including sample bundle rings, and other Mayvenn stylist resources"}])

(defn component [{:keys [product variant-quantity selected-variant adding-to-bag? bagged-variants]} owner]
  (om/component
   (html
    (when product
      (ui/container
       [:.clearfix.mxn2
        ;; TODO: put schema.org back on bundle builder
        {:item-type "http://schema.org/Product"}
        [:.md-up-col.md-up-col-7.px2
         [:.to-md-hide (kit-image product)]]
        [:.md-up-col.md-up-col-5.px2
         [:.center
          [:h1.medium.titleize.navy.h2.line-height-2 {:item-prop "name"} (:name product)]
          ;; The mxn2 pairs with the p2 of the ui/container, to make the
          ;; carousel full width on mobile.
          [:.md-up-hide.mxn2.my2 (kit-image product)]]
         [:div {:item-prop "offers"
                :item-scope ""
                :item-type "http://schema.org/Offer"}
          [:.h2.my2
           [:.right-align.light-gray.h5 "PRICE"]
           [:.flex.h1 {:style {:min-height "1.5em"}} ; prevent slight changes to size depending on content of counter
            (if (:can_supply? selected-variant)
              [:.flex-auto
               [:link {:item-prop "availability" :href "http://schema.org/InStock"}]
               (ui/counter variant-quantity
                           false
                           (utils/send-event-callback events/control-counter-dec
                                                      {:path keypaths/browse-variant-quantity})
                           (utils/send-event-callback events/control-counter-inc
                                                      {:path keypaths/browse-variant-quantity}))]
              [:span.flex-auto "Currently out of stock"])
            [:.navy {:item-prop "price"}
             (as-money-without-cents (:price selected-variant))]]]

          (ui/button
           "Add to bag"
           {:on-click      (utils/send-event-callback events/control-add-to-bag
                                                      {:product product
                                                       :variant selected-variant
                                                       :quantity variant-quantity})
            :show-spinner? adding-to-bag?
            :color         "bg-navy"})]

         (when (seq bagged-variants)
           [:div
            (map-indexed product/redesigned-display-bagged-variant bagged-variants)
            [:.cart-button ; for scrolling
             (ui/button "Check out" (utils/route-to events/navigate-cart))]])

         [:.border-top.border-bottom.border-light-silver.p2.my2.center.navy.shout.medium.h5
          "Free shipping & 30 day guarantee"]

         (when-let [html-description (:description product)]
           [:.border.border-light-gray.p2.rounded.mt2
            [:.mb2.h3.medium.navy.shout "Description"]
            [:div
             {:item-prop "description" :dangerouslySetInnerHTML {:__html html-description}}]])]])))))

(defn- selected-variant [app-state product]
  (query/get (get-in app-state keypaths/browse-variant-query)
             (:variants product)))

(defn query [data]
  (let [product-id (first (:product-ids (taxons/current-taxon data)))
        product (query/get {:id product-id}
                           (vals (get-in data keypaths/products)))]
    {:product          product
     :selected-variant (selected-variant data product)
     :variant-quantity (get-in data keypaths/browse-variant-quantity)
     :adding-to-bag?   (utils/requesting? data request-keys/add-to-bag)
     :bagged-variants  (get-in data keypaths/browse-recently-added-variants)}))

(defn built-component [data]
  (om/build component (query data)))
