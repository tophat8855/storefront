(ns storefront.components.category
  (:require [storefront.components.utils :as utils]
            [storefront.components.product :refer [display-bagged-variant]]
            [storefront.components.formatters :refer [as-money-without-cents as-money]]
            [storefront.accessors.products :as products]
            [storefront.accessors.taxons :refer [filter-nav-taxons taxon-path-for taxon-class-name] :as taxons]
            [storefront.components.reviews :refer [reviews-component reviews-summary-component]]
            [storefront.components.counter :refer [counter-component]]
            [storefront.components.carousel :refer [carousel-component]]
            [clojure.string :as string]
            [om.core :as om]
            [sablono.core :refer-macros [html]]
            [storefront.hooks.experiments :as experiments]
            [storefront.events :as events]
            [storefront.keypaths :as keypaths]
            [storefront.request-keys :as request-keys]
            [storefront.utils.query :as query]
            [storefront.utils.sequences :refer [update-vals]]))

(defn display-taxon [data selected-taxon taxon]
  (let [taxon-path (taxon-path-for taxon)
        selected-class (if (= selected-taxon taxon) "selected" nil)
        taxon-classes (string/join " " (conj [taxon-path] selected-class))]
    [:div.hair-taxon.decorated.small-width {:class taxon-classes}
     [:a.taxon-link (utils/route-to data events/navigate-category {:taxon-path taxon-path})
      [:p.hair-taxon-name (:name taxon)]]]))

(defn display-product [data taxon product]
  (let [collection-name (:collection_name product)]
   [:a {:href (utils/href-to data events/navigate-product {:product-path (:slug product) :query-params {:taxon_id (taxon :id)}})
        :on-click (utils/click-to data events/control-click-category-product {:target product :taxon taxon})}
     [:div.taxon-product-container
      (when-let [first-image (->> product
                                  :master
                                  :images
                                  first
                                  :product_url)]
        [:div.taxon-product-image-container
         {:style {:background-image (str "url('" first-image "')")}}
         (when (#{"ultra" "deluxe"} collection-name)
           [:div.corner-ribbon {:class collection-name}
            collection-name])
         [:img {:src first-image}]])
      [:div.taxon-product-info-container
       [:div.taxon-product-description-container
        [:div.taxon-product-collection
         (when (products/graded? product)
           [:div.taxon-product-collection-indicator
            {:class collection-name}])
         collection-name]
        [:div.taxon-product-title
         (:name product)]]
       [:div.taxon-from-price
        [:span "From: "]
        [:br]
        (let [variant (-> product products/all-variants first)]
          (if (= (variant :price) (variant :original_price))
            (as-money-without-cents (variant :price))
            (list
             [:span.original-price
              (as-money-without-cents (variant :original_price))]
             [:span.current-price
              (as-money-without-cents (variant :price))])))]]]]))

(defn original-category-component [data owner]
  (om/component
   (html
    (if-let [taxon (taxons/current-taxon data)]
      [:div
       [:div.taxon-products-banner {:class (taxon-class-name taxon)}]
       [:div.taxon-products-container
        (when-not (:stylist_only? taxon)
          [:div.taxon-nav
           (map (partial display-taxon data taxon)
                (filter-nav-taxons (get-in data keypaths/taxons)))
           [:div {:style {:clear "both"}}]])
        [:div.taxon-products-list-container
         (let [products (->> (get-in data keypaths/products)
                             vals
                             (sort-by :index)
                             (filter #(contains? (set (:taxon_ids %)) (:id taxon))))]
           (if (query/get {:request-key (concat request-keys/get-products
                                                [(taxon-path-for taxon)])}
                          (get-in data keypaths/api-requests))
             [:.spinner]
             (map (partial display-product data taxon) products)))]]

       [:div.gold-features
        [:figure.guarantee-feature]
        [:figure.free-shipping-feature]
        [:figure.triple-bundle-feature]
        [:feature.fs-feature]]]))))

;; Bundle builder below

(def display-product-images-for-taxons #{"blonde" "closures"})

(defn selection-flow [data]
  (let [taxon-name (:name (taxons/current-taxon data))]
    (condp = taxon-name
      "closures" '(:style :material :origin :length)
      "blonde" '(:color :grade :origin :length)
      '(:grade :origin :length))))

(defn format-step-name [step-name]
  (let [step-name (name step-name)
        vowel? (set "AEIOUaeiou")]
    (str (if (vowel? (first step-name)) "an " "a ")
         (string/capitalize step-name))))

(defn next-step [data step-name]
  (if step-name
    (first (drop-while (partial not= step-name) (selection-flow data)))
    (first (selection-flow data))))

(defn option-selection-event [data step-name selected-options selected-variants]
  (utils/send-event-callback data
                             events/control-bundle-option-select
                             {:step-name step-name
                              :selected-options selected-options
                              :selected-variants selected-variants}))

(defn min-price [variants]
  (when (seq variants)
    (->> variants
         (map :price)
         (apply min))))

(defn price-for-option [step-name option-min-price option-price-diff]
  (case step-name
    :grade    [:min-price option-min-price]
    :material [:diff-price option-price-diff]
    :origin   [:diff-price option-price-diff]
    :style    []
    :length   [:diff-price option-price-diff]
    :color    []))

(defn format-price [[type price]]
  (str ({:min-price "From " :diff-price "+ "} type) (as-money price)))

(defn build-options-for-step [data variants {:keys [step-name option-names dependent-steps]}]
  (let [all-selections   (get-in data keypaths/bundle-builder-selected-options) ;; e.g. {:grade "6a" :source "malaysia"}
        prior-selections (select-keys all-selections dependent-steps)
        step-disabled?   (> (count dependent-steps) (count all-selections))
        step-variants    (products/filter-variants-by-selections prior-selections variants)
        step-min-price   (min-price step-variants)]
    (for [option-name option-names]
      (let [option-variants  (products/filter-variants-by-selections {step-name option-name} step-variants)
            option-min-price (min-price option-variants)
            represented?     (not (empty? option-variants))
            sold-out?        (and represented?
                                  (every? :sold-out? option-variants))]
        {:option-name option-name
         :price (when (and (not step-disabled?) represented?)
                  (price-for-option step-name option-min-price (- option-min-price step-min-price)))
         :disabled (or step-disabled?
                       sold-out?
                       (not represented?))
         :represented represented?
         :checked (= (get all-selections step-name nil) option-name)
         :sold-out sold-out?
         :on-change (option-selection-event data
                                            step-name
                                            (assoc prior-selections step-name option-name)
                                            option-variants)}))))

(defn step-html [step-name idx options]
    [:.step
     [:h2 (str (inc idx)) ". Choose " (format-step-name step-name)]
     [:.options
      (for [{:keys [option-name price represented disabled checked sold-out on-change]} options]
        (when represented
          (let [option-id (string/replace (str option-name step-name) #"\W+" "-")]
            (list
             [:input {:type "radio"
                      :id option-id
                      :disabled disabled
                      :checked checked
                      :on-change on-change}]
             [:.option {:class [step-name (when sold-out "sold-out")]}
              [:.option-name option-name]
              (cond
                sold-out [:.subtext "Sold Out"]
                (seq price) [:.subtext (format-price price)])
              [:label {:for option-id}]]))))]])

(defn bundle-builder-steps [data variants steps]
  (map-indexed (fn [idx {:keys [step-name] :as step}]
                 (step-html step-name
                            idx
                            (build-options-for-step data variants step)))
               steps))

(def summary-option-mapping
  {"6a premier collection" "6a premier"
   "7a deluxe collection" "7a deluxe"
   "8a ultra collection" "8a ultra"
   "closures" "closure"})

(defn summary-format [data]
  (let [variant (products/selected-variant data)
        flow (conj (vec (selection-flow data)) :category)]
    (->> flow
         (map variant)
         (map #(get summary-option-mapping % %))
         (string/join " ")
         string/upper-case)))

(defn add-to-bag-button [data variants]
  (if (query/get {:request-key request-keys/add-to-bag}
                 (get-in data keypaths/api-requests))
    [:button.large.primary#add-to-cart-button.saving]
    [:button.large.primary#add-to-cart-button
     {:on-click (utils/send-event-callback data events/control-browse-add-to-bag)}
     "ADD TO BAG"]))

(def bundle-promotion-notice [:div [:em.bundle-discount-callout "Save 5% - Purchase 3 or more bundles"]])

(defn summary-section [data variants]
  (if-let [variant (products/selected-variant data)]
    [:.selected
     [:.line-item-summary (summary-format data)]
     (om/build counter-component data {:opts {:path keypaths/browse-variant-quantity
                                              :inc-event events/control-counter-inc
                                              :dec-event events/control-counter-dec
                                              :set-event events/control-counter-set}})
     [:.price (as-money (:price variant))]
     bundle-promotion-notice
     (add-to-bag-button data variants)]
    [:.selected
     [:div (str "Select " (format-step-name (next-step data (get-in data keypaths/bundle-builder-previous-step))) "!")]
     [:.price "$--.--"]
     bundle-promotion-notice]))

(defn build-steps [flow redundant-attributes]
  (let [options (->> redundant-attributes
                     (apply merge-with concat)
                     (update-vals set)
                     (update-vals (fn [opts] (->> opts
                                             (sort-by :position)
                                             (map :name)))))
        dependent-steps (vec (reductions #(conj %1 %2) [] flow))]
    (map (fn [step step-dependencies]
           {:step-name step
            :option-names (step options)
            :dependent-steps step-dependencies})
         flow
         dependent-steps)))

(defn css-url [url] (str "url(" url ")"))

(defn product-image-url [data taxon]
  (when-let [product (products/selected-product data)]
    (when (contains? display-product-images-for-taxons (:name taxon))
      (get-in product [:master :images 0 :large_url]))))

(defn category-descriptions [taxon]
  (case (:name taxon)
    "closures"
    '("100% Human Virgin Hair"
      "Silk and Lace Materials"
      "Colors: 1B and #613 Blonde"
      "14\" and 18\" Length Bundles"
      "3.5 ounces")

    "blonde"
    '("100% Human Virgin Hair"
      "Colors: #27 and #613 Blonde"
      "14\" - 26\" Length Bundles"
      "3.5 ounces")

    '("100% Human Virgin Hair"
      "Color 1B"
      "12\" - 28\" Length Bundles"
      "3.5 ounces")))

(defn bundle-builder-category-component [data owner]
  (om/component
   (html
    (let [taxon (taxons/current-taxon data)
          products (products/for-taxon data taxon)]
      [:.bundle-builder
       [:header
        [:h1
         [:div
          "Select Your "
          (:name taxon)
          " Hair"]
         [:.category-sub-header "Buy now and get FREE SHIPPING"]]]

       (if-not (seq products)
         [:.spinner]
         [:div
          [:.reviews-wrapper
           [:.reviews-inner-wrapper
            (when (get-in data keypaths/reviews-loaded)
              (om/build reviews-summary-component data))]]
          [:.carousel
           (if-let [product-url (product-image-url data taxon)]
             [:.hair-category-image {:style {:background-image (css-url product-url)}}]
             (om/build carousel-component data {:opts {:index-path keypaths/bundle-builder-carousel-index
                                                       :images-path (conj keypaths/taxon-images
                                                                          (keyword (:name taxon)))}}))]
          (let [variants (products/current-taxon-variants data)
                steps (build-steps (selection-flow data) (map :product_attrs products))]
            (list
             (bundle-builder-steps data variants steps)
             [:#summary
              [:h3 "Summary"]
              (summary-section data variants)
              (when-let [bagged-variants (seq (get-in data keypaths/browse-recently-added-variants))]
                [:div#after-add {:style {:display "block"}}
                 [:div.added-to-bag-container
                  (map (partial display-bagged-variant data) bagged-variants)]
                 [:div.go-to-checkout
                  [:a.cart-button (utils/route-to data events/navigate-cart) "Checkout"]]])]))
          [:ul.category-description
           (for [description (category-descriptions taxon)]
             [:li description])]
          [:.reviews-wrapper
           (when (get-in data keypaths/reviews-loaded)
             (om/build reviews-component data))]])
       [:div.gold-features
        [:figure.guarantee-feature]
        [:figure.free-shipping-feature]
        [:figure.triple-bundle-feature]]]))))

(defn category-component [data owner]
  (apply (if (experiments/display-variation data "bundle-builder")
           bundle-builder-category-component
           original-category-component)
         [data owner]))
