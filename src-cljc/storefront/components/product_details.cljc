(ns storefront.components.product-details
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [storefront.accessors.bundle-builder :as bundle-builder]
            [storefront.accessors.experiments :as experiments]
            [storefront.accessors.named-searches :as named-searches]
            [storefront.accessors.orders :as orders]
            [storefront.accessors.pixlee :as pixlee]
            [storefront.accessors.products :as products]
            [storefront.accessors.promos :as promos]
            [storefront.accessors.sku-sets :as sku-sets]
            [storefront.assets :as assets]
            #?(:clj [storefront.backend-api :as api]
               :cljs [storefront.api :as api])
            #?(:clj  [storefront.component-shim :as component]
               :cljs [storefront.component :as component])
            [storefront.components.money-formatters :refer [as-money-without-cents as-money]]
            [storefront.components.ui :as ui]
            [storefront.config :as config]
            [storefront.effects :as effects]
            [storefront.events :as events]
            #?(:cljs [storefront.hooks.pixlee :as pixlee-hooks])
            [storefront.keypaths :as keypaths]
            [storefront.platform.carousel :as carousel]
            [storefront.platform.messages :as messages]
            [storefront.platform.reviews :as review-component]
            #?(:cljs [storefront.hooks.reviews :as review-hooks])
            [storefront.platform.ugc :as ugc]
            [storefront.request-keys :as request-keys]
            [storefront.transitions :as transitions]
            [storefront.platform.component-utils :as utils]))

(defn page [wide-left wide-right-and-narrow]
  [:div.clearfix.mxn2 {:item-scope :itemscope :item-type "http://schema.org/Product"}
   [:div.col-on-tb-dt.col-7-on-tb-dt.px2 [:div.hide-on-mb wide-left]]
   [:div.col-on-tb-dt.col-5-on-tb-dt.px2 wide-right-and-narrow]])

(defn title [name]
  [:h1.h2.medium.titleize.navy {:item-prop "name"} name])

(defn full-bleed-narrow [body]
  ;; The mxn2 pairs with the p2 of the container, to make the body full width
  ;; on mobile.
  [:div.hide-on-tb-dt.mxn2.my2 body])

(def schema-org-offer-props
  {:item-prop "offers"
   :item-scope ""
   :item-type "http://schema.org/Offer"})

(defn quantity-and-price-structure [quantity price]
  [:div
   [:div.right-align.dark-gray.h6 "PRICE"]
   [:div.flex.h2 {:style {:min-height "1.5em"}} ; prevent slight changes to size depending on content of counter
    [:div.flex-auto quantity]
    [:div.navy price]]])

(defn counter-or-out-of-stock [can-supply? quantity]
  (if can-supply?
    [:div
     [:link {:item-prop "availability" :href "http://schema.org/InStock"}]
     (ui/counter quantity
                 false
                 (utils/send-event-callback events/control-counter-dec
                                            {:path keypaths/browse-variant-quantity})
                 (utils/send-event-callback events/control-counter-inc
                                            {:path keypaths/browse-variant-quantity}))]
    [:span.h4 "Currently out of stock"]))

(defn add-to-bag-button [adding-to-bag? variant quantity]
  (ui/navy-button {:on-click  (utils/send-event-callback events/control-add-to-bag
                                                         {:variant  variant
                                                          :quantity quantity})
                   :data-test "add-to-bag"
                   :spinning? adding-to-bag?}
                  "Add to bag"))

(defn ^:private number->words [n]
  (let [mapping ["Zero" "One" "Two" "Three" "Four" "Five" "Six" "Seven" "Eight" "Nine" "Ten" "Eleven" "Twelve" "Thirteen" "Fourteen" "Fifteen"]]
    (get mapping n (str "(x " n ")"))))

(defn display-bagged-variant [idx {:keys [quantity variant]}]
  [:div.h6.my1.p1.py2.caps.dark-gray.bg-light-gray.medium.center
   {:key idx
    :data-test "items-added"}
   "Added to bag: "
   (number->words quantity)
   " "
   (products/product-title variant)])

(def checkout-button
  (component/html
   [:div
    {:data-test "cart-button"
     :data-ref "cart-button"}
    (ui/teal-button (utils/route-to events/navigate-cart) "Check out")]))

(defn bagged-variants-and-checkout [bagged-variants]
  (when (seq bagged-variants)
    [:div
     (map-indexed display-bagged-variant bagged-variants)
     checkout-button]))

(defn option-html [step-name later-step?
                   {:keys [name image price-delta checked? sold-out? selections]}]
  [:label.btn.p1.flex.flex-column.justify-center.items-center.container-size.letter-spacing-0
   {:data-test (str "option-" (string/replace name #"\W+" ""))
    :class (cond
             sold-out?   "border-gray       bg-gray       dark-gray light"
             later-step? "border-light-gray bg-light-gray dark-gray light"
             checked?    "border-gray       bg-teal       white     medium"
             true        "border-gray       bg-white      dark-gray light")
    :style {:font-size "14px" :line-height "18px"}}
   [:input.hide {:type      "radio"
                 :disabled  (or later-step? sold-out?)
                 :checked   checked?
                 :on-change (utils/send-event-callback events/control-bundle-option-select
                                                       {:selections selections
                                                        :step-name step-name})}]
   (if image
     [:img.mbp4.content-box.circle.border-light-gray
      {:src image :alt name
       :width 30 :height 30
       :class (cond checked? "border" sold-out? "muted")}]
     [:span.block.titleize name])
   [:span.block
    (if sold-out?
      "Sold Out"
      [:span {:class (str (when-not checked? "navy") (when later-step? " muted"))}
       "+" (as-money-without-cents price-delta)])]])

(defn step-html [{:keys [step-name selected-option later-step? options]}]
  [:div.my2 {:key step-name}
   [:h2.h3.clearfix.h5
    [:span.block.left.navy.medium.shout
     (name step-name)
     (when selected-option [:span.inline-block.mxp2.dark-gray " - "])]
    (when selected-option
      [:span.block.overflow-hidden.dark-gray.h5.regular
       (or (:long-name selected-option)
           [:span.titleize (:name selected-option)])])]
   [:div.flex.flex-wrap.content-stretch.mxnp3
    (for [{:keys [name] :as option} options]
      [:div.flex.flex-column.justify-center.pp3
       {:key   (string/replace (str name step-name) #"\W+" "-")
        :style {:height "72px"}
        :class (if (#{:length :color :style} step-name) "col-4" "col-6")}
       (option-html step-name later-step? option)])]])

(defn indefinite-articalize [word]
  (let [vowel? (set "AEIOUaeiou")]
    (str (if (vowel? (first word)) "an " "a ")
         word)))

(defn variant-name [variant flow]
  (when (seq flow)
    (string/upper-case (:variant-name variant))))

(defn summary-structure [desc quantity-and-price]
  [:div
   (when (seq desc)
     [:div
      [:h2.h3.light "Summary"]
      [:div.navy desc]])
   quantity-and-price])

(defn no-variant-summary [next-step]
  (summary-structure
   (str "Select " (-> next-step name string/capitalize indefinite-articalize) "!")
   (quantity-and-price-structure ui/nbsp "$--.--")))

(defn item-price [price]
  [:span {:item-prop "price"} (as-money-without-cents price)])

(defn variant-summary [{:keys [flow
                               variant
                               variant-quantity]}]
  (let [{:keys [can_supply? price]} variant]
    (summary-structure
     (variant-name variant flow)
     (quantity-and-price-structure
      (counter-or-out-of-stock can_supply? variant-quantity)
      (item-price price)))))

(def triple-bundle-upsell
  (component/html [:p.center.h5.p2.navy promos/bundle-discount-description]))

(def shipping-and-guarantee
  (component/html
   [:div.border-top.border-bottom.border-gray.p2.my2.center.navy.shout.medium.h6
    "Free shipping & 30 day guarantee"]))

(defn sku-set-description [{{:keys [included-items description]} :copy}]
  [:div.border.border-dark-gray.mt2.p2.rounded
   [:h2.h3.medium.navy.shout "Description"]
   [:div {:item-prop "description"}
    #_
    (when (or colors weights materials)
      (let [attrs (->> [["Color" colors]
                        ["Weight" weights]
                        ["Material" materials]]
                       (filter second))
            ;;This won't work if we have 5 possible attrs
            size (str "col-" (/ 12 (count attrs)))]
        (into [:div.clearfix.mxn1.my2]
              (for [[title value] attrs]
                [:dl.col.m0.inline-block {:class size}
                 [:dt.mx1.dark-gray.shout.h6 title]
                 [:dd.mx1.ml0.h5.navy.medium value]]))))
    (when (seq included-items)
      [:div.my2
       [:h3.mbp3.h5 "Includes:"]
       [:ul.list-reset.navy.h5.medium
        (for [[idx item] (map-indexed vector included-items)]
          [:li.mbp3 {:key idx} item])]])
    [:div.h5.dark-gray
     (for [[idx item] (map-indexed vector description)]
       [:p.mt2 {:key idx} item])]]])

(defn old-carousel-image [image]
  (ui/aspect-ratio
   640 580
   [:img.col-12 (ui/img-attrs image :large)]))

(defn carousel-image [{:keys [filename url alt]}]
  (ui/aspect-ratio
   640 580
   [:img.col-12
    {:src (str url "-/format/auto/-/resize/640x/" filename)
     :alt alt}]))

(defn carousel [images {:keys [slug]}]
  (let [image-body (if (= slug "yaki-straight")
                     carousel-image
                     old-carousel-image)
        items (mapv (fn [image]
                      {:id   (str (hash (or (:large_url image)
                                            (:url image))))
                       :body (image-body image)})
                    images)]
    (component/build carousel/component
                     {:slides (map :body items)
                      :settings {:dots true}}
                     {:react-key (apply str "category-swiper-" slug (interpose "-" (map :id items)))})))

(defn reviews-summary [reviews opts]
  [:div.h6
   {:style {:min-height "18px"}}
   (component/build review-component/reviews-summary-component reviews opts)])

(defn component [{:keys [sku-set fetching-sku-set? carousel-images reviews ugc]}
                 owner opts]
  (let [review? (:review? reviews)]
    (component/create
     [:div.container.p2
      (page
       [:div
        (carousel carousel-images sku-set)
        [:div.hide-on-mb (component/build ugc/component ugc opts)]]
       [:div
        [:div.center
         (title (:name sku-set))
         (when review? (reviews-summary reviews opts))
         [:meta {:item-prop "image" :content (first carousel-images)}]
         #_
         (full-bleed-narrow (carousel carousel-images named-search))
         #_
         (when (and (not fetching-sku-sets?)
                    needs-selections?)
           (starting-at (:initial-variants bundle-builder)))]
        (if fetching-sku-set?
          [:div.h2.mb2 ui/spinner]
          [:div
           #_
           [:div schema-org-offer-props
            [:div.my2
             (if selected-variant
               (variant-summary {:flow                  (:flow bundle-builder)
                                 :variant               selected-variant
                                 :variant-quantity      variant-quantity})
               (no-variant-summary (bundle-builder/next-step bundle-builder)))]
            (when (named-searches/eligible-for-triple-bundle-discount? named-search)
              triple-bundle-upsell)
            (when selected-variant
              (add-to-bag-button adding-to-bag? selected-variant variant-quantity))
            (bagged-variants-and-checkout bagged-variants)
            (when (named-searches/is-stylist-product? named-search) shipping-and-guarantee)]])
        (sku-set-description sku-set)
        #_
        [:div.hide-on-tb-dt.mxn2.mb3 (component/build ugc/component ugc opts)]])
      (when review?
        (component/build review-component/reviews-component reviews opts))])))

(defn ugc-query [sku-set data]
  (let [images (pixlee/images-in-album (get-in data keypaths/ugc)
                                       (sku-sets/id->named-search (:id sku-set)))]
    {:named-search sku-set
     :album        images}))

(defn query [data]
  (let [sku-code->sku (get-in data keypaths/skus)
        sku-set       (sku-sets/current-sku-set data)
        skus          (map (comp sku-code->sku :sku) (:skus sku-set))
        reviews       (assoc (review-component/query data)
                             :review?
                             (sku-sets/eligible-for-reviews? sku-set))]
    {:sku-set           sku-set
     :skus              skus
     :carousel-images   (filter (comp #{"carousel"} :use-case) (map :images skus))
     :fetching-sku-set? false
     :reviews           reviews
     :ugc               (ugc-query sku-set data)}))

(defn built-component [data opts]
  (component/build component (query data) opts))


(defn fetch-current-sku-set-album [app-state sku-set-id]
  (when-let [slug (->> sku-set-id
                       (sku-sets/sku-set-by-id app-state)
                       :id
                       sku-sets/id->named-search)]
    (when-let [album-id (get-in config/pixlee [:albums slug])]
      #?(:cljs (pixlee-hooks/fetch-album album-id slug)
         :clj nil))))

(defmethod effects/perform-effects events/navigate-product-details
  [_ event {:keys [id slug]} _ app-state]
  (api/search-sku-sets id (fn [response] (messages/handle-message events/api-success-sku-sets-for-details response)))
  #?(:cljs (review-hooks/insert-reviews))
  (fetch-current-sku-set-album app-state id))

(defmethod effects/perform-effects events/api-success-sku-sets-for-details
  [_ event {:keys [sku-sets] :as response} _ app-state]
  (fetch-current-sku-set-album app-state (get-in app-state keypaths/product-details-sku-set-id)))

(defmethod transitions/transition-state events/navigate-product-details
  [_ event {:keys [id slug]} app-state]
  (assoc-in app-state keypaths/product-details-sku-set-id id))