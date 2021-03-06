(ns catalog.menu
  (:require
   #?@(:clj [[storefront.component-shim :as component]]
       :cljs [[storefront.api :as api]
              [storefront.component :as component]])
   [catalog.categories :as categories]
   [catalog.selector :as selector]
   [clojure.set :as set]
   [storefront.components.ui :as ui]
   [storefront.components.svg :as svg]
   [storefront.effects :as effects]
   [storefront.events :as events]
   [storefront.keypaths :as keypaths]
   [storefront.platform.component-utils :as utils]
   [storefront.platform.messages :as messages]
   [storefront.transitions :as transitions]
   [storefront.accessors.experiments :as experiments]))

;;NOTE Used by slideout-nav
(defn major-menu-row [& content]
  [:div.h4.border-bottom.border-gray.py3
   (into [:a.block.inherit-color.flex.items-center] content)])

(defn component
  [{:keys [nav-root options]}
   owner
   opts]
  (component/create
   (let [{:keys [selector/electives]} nav-root]
     [:div
      [:a.dark-gray.block.py1.px3.h6
       (utils/fake-href events/menu-home)
       [:span.mr1 (ui/back-caret "Back")]]
      [:div.px6
       (major-menu-row
        [:div.h2.flex-auto.center "Shop " (:copy/title nav-root)])
       [:ul.list-reset
        (for [{:keys [page/slug] :as category} options]
          [:li {:key slug}
           (major-menu-row
            (assoc (utils/route-to events/navigate-category category)
                   :data-test (str "menu-step-" slug))
            [:span.flex-auto.titleize
             (when (:category/new? category)
               [:span.teal "NEW "])
             (:copy/title category)])])]]])))

(defn query [data]
  (let [{:keys [selector/essentials] :as nav-root} (categories/current-traverse-nav data)]
    {:nav-root    nav-root
     :options     (selector/strict-query categories/menu-categories
                                         (select-keys nav-root essentials))}))

(defmethod transitions/transition-state events/menu-home
  [_ _ _ app-state]
  (update-in app-state keypaths/current-traverse-nav dissoc :id))

(defmethod effects/perform-effects events/menu-home
  [_ _ _ _ app-state])

(defmethod transitions/transition-state events/menu-list
  [_ _ {:keys [catalog/category-id]} app-state]
  (assoc-in app-state keypaths/current-traverse-nav-id category-id))

(defmethod effects/perform-effects events/control-menu-expand-hamburger
  [_ _ _ _ _]
  (messages/handle-message events/menu-home))
