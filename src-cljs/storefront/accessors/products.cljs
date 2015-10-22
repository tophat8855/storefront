(ns storefront.accessors.products
  (:require [storefront.keypaths :as keypaths]
            [storefront.utils.sequences :refer [update-vals]]
            [storefront.accessors.taxons :as taxons]))

(defn graded? [product]
  (-> product
      :collection_name
      #{"premier" "deluxe" "ultra"}
      boolean))

(defn all-variants [product]
  (conj (:variants product) (:master product)))

(defn for-taxon [data taxon]
  (->> (get-in data keypaths/products)
       vals
       (sort-by :index)
       (filter #(contains? (set (:taxon_ids %)) (:id taxon)))))

(defn selected-variant [data]
  (let [variants (get-in data keypaths/bundle-builder-selected-variants)]
    (when (= 1 (count variants))
      (first variants))))

(defn selected-products [data]
  (let [variants (get-in data keypaths/bundle-builder-selected-variants)
        product-ids (set (map :product_id variants))]
    (select-keys (get-in data keypaths/products) product-ids)))

(defn selected-product [data]
  (let [selected (selected-products data)]
    (when (= 1 (count selected))
      (last (first selected)))))

(defn build-variants
  "We wish the API gave us a list of variants.  Instead, variants are nested
  inside products.

  So, this explodes them out into the data structure we'd prefer."
  [product]
  (let [product-attrs (update-vals (comp :name first) (:product_attrs product))
        variants (:variants product)]
    (map (fn [variant]
           (-> variant
               (merge product-attrs)
               ;; Variants have one specific length, stored in option_values.
               ;; We need to overwrite the product length, which includes all
               ;; possible lengths.
               (assoc :length (some-> variant :option_values first :name (str "\""))
                      :price (js/parseFloat (:price variant))
                      :sold-out? (not (:can_supply? variant)))
               (dissoc :option_values)))
         variants)))

(defn current-taxon-variants [app-state]
  (let [products (for-taxon app-state (taxons/current-taxon app-state))]
    (mapcat build-variants products)))

(defn ordered-products-for-category [app-state taxon]
  (let [taxon-path (taxons/taxon-path-for taxon)
        taxon-product-order (get-in app-state keypaths/taxon-product-order)
        products (get-in app-state keypaths/products {})]
    (map products (taxon-product-order taxon-path))))

(defn filter-variants-by-selections [selections variants]
  (filter (fn [variant]
            (every? (fn [[step-name option-name]]
                      (= (step-name variant) option-name))
                    selections))
          variants))

(defn not-black-color [attr]
  (when (not= "black" (:color attr))
    (:color attr)))

(def summary-option-mapping
  {"6a premier collection" "6a premier"
   "7a deluxe collection" "7a deluxe"
   "8a ultra collection" "8a ultra"
   "closures" "closure"})

(def ^:private closure-summary [:style :material :origin :length (constantly "closure")])
(def ^:private bundle-summary [not-black-color (comp summary-option-mapping :grade) :origin :length :style])

(defn closure? [variant]
  (= "closures" (get-in variant [:variant-attrs :category])))

(defn bundle? [variant]
  (boolean (get-in variant [:variant-attrs :category])))

(defn summary [{:keys [variant-attrs product-name] :as variant}]
  (let [summary-fns (cond (closure? variant) closure-summary
                          (bundle? variant)  bundle-summary
                          :else [(constantly product-name)])
        strs (filter identity ((apply juxt summary-fns) variant-attrs))]
    (clojure.string/join " " strs)))

(defn thumbnail-urls [data product-id]
  (map :small_url (get-in data (conj keypaths/products product-id :master :images))))
