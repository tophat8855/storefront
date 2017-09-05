(ns catalog.category
  (:require
   #?(:cljs [storefront.component :as component]
      :clj  [storefront.component-shim :as component])
   [catalog.category-filters :as category-filters]
   [catalog.categories :as categories]
   [storefront.components.money-formatters :as mf]
   [storefront.components.ui :as ui]
   [storefront.events :as events]
   [storefront.transitions :as transitions]
   [storefront.effects :as effects]
   [storefront.keypaths :as keypaths]
   [storefront.platform.component-utils :as utils]
   [storefront.platform.messages :as messages]
   [storefront.accessors.experiments :as experiments]))

(defn slug->facet [facet facets]
  (->> facets
       (filter (fn [{:keys [:facet/slug]}] (= slug facet)))
       first))

(defn slug->option [option options]
  (->> options
       (filter (fn [{:keys [:option/slug]}] (= slug option)))
       first))

(defmulti unconstrained-facet (fn [skus facets facet] facet))
(defmethod unconstrained-facet :hair/length [skus facets facet]
  (let [lengths  (->> skus
                      (map #(get-in % [:attributes :hair/length]))
                      sort)
        shortest (first lengths)
        longest  (last lengths)]
    [:p.h6.dark-gray
     "in "
     (->> facets
          (slug->facet :hair/length)
          :facet/options
          (slug->option shortest)
          :option/name)
     " - "
     (->> facets
          (slug->facet :hair/length)
          :facet/options
          (slug->option longest)
          :option/name)]))

(defmethod unconstrained-facet :hair/color [skus facets facet]
  (let [colors (->> skus
                    (map #(get-in % [:attributes :hair/color]))
                    distinct)]
    (when (> (count colors) 1)
      [:p.h6.dark-gray "+ more colors available"])))

(defn filter-tabs [category-criteria {:keys [facets filtered-sku-sets criteria]}]
  (let [sku-set-count        (count filtered-sku-sets)
        applied-filter-count (->> (apply dissoc criteria (keys category-criteria))
                                  (map (comp count val))
                                  (apply +))]
    [:div.py4
     (when (seq facets)
       [:div
        [:div.pb1.flex.justify-between
         [:p.h6.dark-gray (case applied-filter-count
                            0 "Filter by:"
                            1 "1 filter applied:"
                            (str applied-filter-count " filters applied:"))]
         [:p.h6.dark-gray (str sku-set-count " Item" (when (not= 1 sku-set-count) "s"))]]
        (into [:div.border.h6.border-teal.rounded.flex.center]
              (map-indexed
               (fn [idx {:keys [slug title selected?]}]
                 [:a.flex-auto.x-group-item.rounded-item
                  (assoc
                   (if selected?
                     (utils/fake-href events/control-category-filters-close)
                     (utils/fake-href events/control-category-filter-select {:selected slug}))
                   :key slug
                   :class (if selected? "bg-teal white" "dark-gray"))
                  [:div.border-teal.my1
                   {:class (when-not (zero? idx) "border-left")}
                   title]])
               facets))])]))

(defn filter-panel [selected-facet]
  [:div.px1
   (for [options (partition-all 4 (:options selected-facet))]
     [:div.flex-on-tb-dt.justify-around
      (for [{:keys [slug label represented? selected?]} options]
        [:div.py1.mr4
         {:key (str "filter-option-" slug)}
         (ui/check-box {:label     [:span
                                    (when (categories/new-facet? [(:slug selected-facet) slug]) [:span.mr1.teal "NEW"])
                                    label]
                        :value     selected?
                        :disabled  (not represented?)
                        :on-change #(let [event-handler (if selected?
                                                          events/control-category-criterion-deselected
                                                          events/control-category-criterion-selected)]
                                      (messages/handle-message event-handler
                                                               {:filter (:slug selected-facet)
                                                                :option slug}))})])])
   [:div.clearfix.mxn3.px1.py4.hide-on-tb-dt
    [:div.col.col-6.px3
     (ui/teal-ghost-button
      (utils/fake-href events/control-category-criteria-cleared)
      "Clear all")]
    [:div.col.col-6.px3
     (ui/teal-button
      (utils/fake-href events/control-category-filters-close)
      "Done")]]])

(defn hero-section [category]
  [:h1
   (let [{:keys [mobile-url file-name desktop-url alt]} (:hero (:images category))]
     [:picture
      [:source {:media   "(min-width: 750px)"
                :src-set (str desktop-url "-/format/auto/" file-name " 1x")}]
      [:img.block.col-12 {:src (str mobile-url "-/format/auto/" file-name)
                          :alt alt}]])])

(defn copy-section [category]
  [:div.mt6.mb2 [:p.py6.max-580.mx-auto.center (-> category :copy :description)]])

(defn product-cards [sku-sets facets]
  [:div.flex.flex-wrap.mxn1
   (if (empty? sku-sets)
     [:div.col-12.my8.py4.center
      #_ [:p.h1.py4 "😞"]
      [:p.h2.dark-gray.py6 "Sorry, we couldn’t find any matches."]
      [:p.h4.dark-gray.mb10.pb10
       [:a.teal (utils/fake-href events/control-category-criteria-cleared) "Clear all filters"]
       " to see more hair."]]
     (for [{:keys [sku-set/slug matching-skus representative-sku sku-set/name sold-out?] :as product} sku-sets]
       (let [image (->> representative-sku :images (filter (comp #{"catalog"} :use-case)) first)]
         [:div.col.col-6.col-4-on-tb-dt.px1
          {:key slug}
          [:a.inherit-color
           (utils/route-to events/navigate-product-details {:id   (:sku-set/id product)
                                                            :slug (:sku-set/slug product)})
           [:div.mb10.center
            ;; TODO: when adding aspect ratio, also use srcset/sizes to scale these images.
            [:img.block.col-12 {:src (str (:url image) "-/format/auto/" (:filename image))
                                :alt (:alt image)}]
            [:h2.h4.mt3.mb1 name]
            (if sold-out?
              [:p.h6.dark-gray "Out of stock"]
              [:div
               ;; This is pretty specific to hair. Might be better to have a
               ;; sku-set know its "constrained" and "unconstrained" facets.
               (unconstrained-facet matching-skus facets :hair/length)
               (unconstrained-facet matching-skus facets :hair/color)
               [:p.h6 "Starting at " (mf/as-money-without-cents (:price representative-sku))]])]]])))])

(defn ^:private component [{:keys [category filters facets]} owner opts]
  (let [category-criteria (:criteria category)]
    (component/create
     [:div
      (hero-section category)
      [:div.max-960.col-12.mx-auto.px2-on-mb
       (copy-section category)
       [:div.bg-white.sticky
        ;; The -5px prevents a sliver of the background from being visible above the filters
        ;; (when sticky) on android (and sometimes desktop chrome when using the inspector)
        {:style {:top "-5px"}}
        (if-let [selected-facet (->> filters
                                     :facets
                                     (filter :selected?)
                                     first)]
          [:div
           [:div.hide-on-tb-dt.px2.z4.fixed.overlay.overflow-auto.bg-white
            (filter-tabs category-criteria filters)
            (filter-panel selected-facet)]
           [:div.hide-on-mb
            (filter-tabs category-criteria filters)
            (filter-panel selected-facet)]]
          [:div
           (filter-tabs category-criteria filters)])]
       (product-cards (:filtered-sku-sets filters) facets)]])))

(defn ^:private query [data]
  {:category (categories/current-category data)
   :filters  (get-in data keypaths/category-filters-for-browse)
   :facets   (get-in data keypaths/facets)})

(defn built-component [data opts]
  (component/build component (query data) opts))

(defmethod transitions/transition-state events/navigate-category
  [_ event args app-state]
  (assoc-in app-state keypaths/current-category-id (:id args)))

#?(:cljs
   (defmethod effects/perform-effects events/navigate-category
     [_ event {:keys [id slug]} _ app-state]
     (let [category   (categories/current-category app-state)
           success-fn #(messages/handle-message events/api-success-sku-sets-for-browse
                                                (assoc % :category-id (:id category)))]
       (storefront.api/fetch-facets (get-in app-state keypaths/api-cache))
       (storefront.api/search-sku-sets (get-in app-state keypaths/api-cache)
                                       (:criteria category)
                                       success-fn))))

(defmethod transitions/transition-state events/api-success-sku-sets-for-browse
  [_ event {:keys [sku-sets] :as response} app-state]
  (-> app-state
      (assoc-in keypaths/category-filters-for-browse
                (categories/make-category-filters app-state response))))

(defmethod transitions/transition-state events/control-category-filter-select
  [_ _ {:keys [selected]} app-state]
  (update-in app-state
             keypaths/category-filters-for-browse
             category-filters/open selected))

(defmethod transitions/transition-state events/control-category-filters-close
  [_ _ _ app-state]
  (update-in app-state
             keypaths/category-filters-for-browse
             category-filters/close))

(defmethod transitions/transition-state events/control-category-criterion-selected
  [_ _ {:keys [filter option]} app-state]
  (update-in app-state
             keypaths/category-filters-for-browse
             category-filters/select-criterion filter option))

(defmethod transitions/transition-state events/control-category-criterion-deselected
  [_ _ {:keys [filter option]} app-state]
  (update-in app-state
             keypaths/category-filters-for-browse
             category-filters/deselect-criterion filter option))

(defmethod transitions/transition-state events/control-category-criteria-cleared
  [_ _ _ app-state]
  (update-in app-state
             keypaths/category-filters-for-browse
             category-filters/clear-criteria))
