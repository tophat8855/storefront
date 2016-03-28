(ns storefront.components.utils
  (:require [storefront.routes :as routes]
            [storefront.keypaths :as keypaths]
            [storefront.events :as events]
            [storefront.messages :refer [handle-message]]))

(defn noop-callback [e] (.preventDefault e))

;; TODO: handle-message remove arg
(defn send-event-callback [_ event & [args]]
  (fn [e]
    (.preventDefault e)
    (handle-message event args)
    nil))

(defn route-to [app-state navigation-event & [args]]
  {:href (routes/path-for @app-state navigation-event args)
   :on-click
   (fn [e]
     (.preventDefault e)
     (routes/enqueue-navigate @app-state navigation-event args))})

(defn change-text [app-state owner keypath]
  {:value (get-in app-state keypath)
   :on-change
   (fn [e]
     (handle-message events/control-change-state
                     {:keypath keypath
                      :value (.. e -target -value)}))})

(defn change-checkbox [app-state keypath]
  (let [checked-val (when (get-in app-state keypath) "checked")]
    {:checked checked-val
     :value checked-val
     :on-change
     (fn [e]
       (handle-message events/control-change-state
                       {:keypath keypath
                        :value (.. e -target -checked)}))}))

(defn change-radio [app-state keypath value]
  (let [keypath-val (get-in app-state keypath)
        checked-val (when (= keypath-val (name value)) "checked")]
    {:checked checked-val
     :on-change
     (fn [e]
       (handle-message events/control-change-state
                       {:keypath keypath
                        :value value}))}))

(defn change-file [event]
  {:on-change (fn [e]
                (handle-message event
                                {:file (-> (.. e -target -files)
                                           array-seq
                                           first)}))})

(def nbsp [:span {:dangerouslySetInnerHTML {:__html " &nbsp;"}}])
(def rarr [:span {:dangerouslySetInnerHTML {:__html " &rarr;"}}])

(defn position [pred coll]
  (first (keep-indexed #(when (pred %2) %1)
                       coll)))
