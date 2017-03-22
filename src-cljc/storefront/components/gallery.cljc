(ns storefront.components.gallery
  (:require #?(:clj [storefront.component-shim :as component]
               :cljs [storefront.component :as component])
            [storefront.components.ui :as ui]
            [storefront.platform.component-utils :as utils]
            [storefront.accessors.stylists :as stylists]
            [storefront.events :as events]
            [storefront.request-keys :as request-keys]
            [storefront.assets :as assets]
            [storefront.keypaths :as keypaths]))

(defn title [{:keys [store_nickname]}]
  [:div.p2.center
   [:h2 [:img {:style {:height "50px"}
                  :src (assets/path "/images/icons/profile.png")}]]
   [:h1 (str store_nickname "'s Gallery")]
   [:div (str "Scroll through "
              store_nickname
              "'s best #MayvennMade looks and get inspiration for your next style!")]])

(defn manage-section [editing? adding-photo?]
  [:div.p2.center.dark-gray.bg-light-gray
   [:h1 "Manage your gallery"]
   [:div.p1 "Here you can upload images, edit posts and manage your gallery settings."]
   (ui/narrow-container
    [:div.p1 (ui/teal-button (merge (utils/route-to events/navigate-gallery-image-picker)
                                    {:data-test "add-to-gallery-link"
                                     :spinning?  adding-photo?})
                             "Choose an image to upload")]
    ;;TODO change button depending upon state
    [:div.p1 (if editing?
               (ui/dark-gray-button {} "Cancel editing")
               (ui/ghost-button {} "Edit your gallery"))])])

(def pending-approval
  (component/html
   [:div.container-size.bg-dark-gray.white.medium.flex.items-center.center.p2
    "Your image has been successfully submitted and is pending approval. Check back here to be updated on its status."]))

(defn images [{:keys [gallery]}]
  (into [:div.clearfix.mxn1]
        (for [{:keys [status resizable_url]} (:images gallery)]
          [:div.col.col-12.col-4-on-tb-dt.px1.pb2
           {:key resizable_url}
           (ui/aspect-ratio 1 1
                            (if (= "approved" status)
                              [:img.col-12 {:src resizable_url}]
                              pending-approval))])))

(defn component [{:keys [store editing? own-store? adding-photo?] :as data} owner opts]
  (component/create
   [:div.container
    (title store)
    (when own-store?
      (manage-section editing? adding-photo?))
    (images store)]))

(defn query [data]
  {:store         (get-in data keypaths/store)
   :editing?      false
   :own-store?    (stylists/own-store? data)
   :adding-photo? (utils/requesting? data request-keys/add-to-stylist-gallery)})

(defn built-component [data opts]
  (component/build component (query data) nil))
