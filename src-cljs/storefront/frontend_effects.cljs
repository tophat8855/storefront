(ns storefront.frontend-effects
  (:require [ajax.core :refer [-abort]]
            [cemerick.url :refer [url-encode]]
            [clojure.set :as set]
            [goog.labs.userAgent.device :as device]
            [storefront.effects :refer [perform-effects redirect page-not-found]]
            [storefront.accessors.credit-cards :refer [parse-expiration filter-cc-number-format]]
            [storefront.accessors.experiments :as experiments]
            [storefront.accessors.named-searches :as named-searches]
            [catalog.categories :as categories]
            [storefront.accessors.nav :as nav]
            [storefront.accessors.orders :as orders]
            [storefront.accessors.pixlee :as accessors.pixlee]
            [storefront.accessors.products :as products]
            [storefront.accessors.stylist-urls :as stylist-urls]
            [storefront.accessors.stylists :as stylists]
            [storefront.request-keys :as request-keys]
            [storefront.api :as api]
            [storefront.browser.cookie-jar :as cookie-jar]
            [storefront.browser.scroll :as scroll]
            [storefront.browser.tags :as tags]
            [storefront.config :as config]
            [storefront.events :as events]
            [storefront.history :as history]
            [storefront.hooks.apple-pay :as apple-pay]
            [storefront.hooks.convert :as convert]
            [storefront.hooks.exception-handler :as exception-handler]
            [storefront.hooks.facebook :as facebook]
            [storefront.hooks.facebook-analytics :as facebook-analytics]
            [storefront.hooks.google-analytics :as google-analytics]
            [storefront.hooks.pixlee :as pixlee]
            [storefront.hooks.places-autocomplete :as places-autocomplete]
            [storefront.hooks.reviews :as reviews]
            [storefront.hooks.riskified :as riskified]
            [storefront.hooks.seo :as seo]
            [storefront.hooks.sift :as sift]
            [storefront.hooks.stringer :as stringer]
            [storefront.hooks.stripe :as stripe]
            [storefront.hooks.svg :as svg]
            [storefront.hooks.talkable :as talkable]
            [storefront.hooks.uploadcare :as uploadcare]
            [storefront.hooks.wistia :as wistia]
            [storefront.keypaths :as keypaths]
            [storefront.platform.messages :refer [handle-later handle-message]]
            [storefront.routes :as routes]
            [storefront.utils.maps :as maps]
            [storefront.effects :as effects]))

(defn changed? [previous-app-state app-state keypath]
  (not= (get-in previous-app-state keypath)
        (get-in app-state keypath)))

(defn potentially-show-email-popup [app-state]
  (let [is-on-homepage?     (= (get-in app-state keypaths/navigation-event) events/navigate-home)
        not-seen-popup-yet? (not (get-in app-state keypaths/email-capture-session))
        signed-in?          (get-in app-state keypaths/user-id)]
    (when (and is-on-homepage? not-seen-popup-yet?)
      (if signed-in?
        (cookie-jar/save-email-capture-session (get-in app-state keypaths/cookie) "signed-in")
        (handle-message events/show-email-popup)))))

(defn refresh-account [app-state]
  (let [user-id (get-in app-state keypaths/user-id)
        user-token (get-in app-state keypaths/user-token)]
    (when (and user-id user-token)
      (api/get-account user-id user-token))))

(defn refresh-current-order [app-state]
  (let [user-id (get-in app-state keypaths/user-id)
        user-token (get-in app-state keypaths/user-token)
        stylist-id (get-in app-state keypaths/store-stylist-id)
        order-number (get-in app-state keypaths/order-number)]
    (when (and user-id user-token stylist-id (not order-number))
      (api/get-current-order user-id
                             user-token
                             stylist-id))))

(defn refresh-products [app-state product-ids]
  (when (seq product-ids)
    (api/get-products-by-ids product-ids
                             (get-in app-state keypaths/user-id)
                             (get-in app-state keypaths/user-token))))

(defn ensure-products [app-state product-ids]
  (let [not-cached (remove (products/loaded-ids app-state) (set product-ids))]
    (refresh-products app-state not-cached)))

(defn refresh-named-search-products
  "Intentionally bypass ensure-products whenever we navigate to a category"
  [app-state named-search]
  (refresh-products app-state (:product-ids named-search)))

(defn update-email-capture-session [app-state]
  (when-let [value (get-in app-state keypaths/email-capture-session)]
    (cookie-jar/save-email-capture-session (get-in app-state keypaths/cookie) value)))

(defn scroll-promo-field-to-top []
  ;; In a timeout so that changes to the advertised promo aren't changing the scroll too.
  (js/setTimeout #(scroll/scroll-selector-to-top "[data-ref=promo-code]") 0))

(defn redirect-named-search [category]
  ;; TODO: ensure we have a redirect for all named-searches. Most should go to
  ;; a category page. Kits should redirect straight to product page.
  (when category
    (redirect events/navigate-category category)))

(defmethod perform-effects events/app-start [dispatch event args _ app-state]
  (svg/insert-sprite)
  (stringer/insert-tracking)
  (google-analytics/insert-tracking)
  (convert/insert-tracking)
  (riskified/insert-tracking (get-in app-state keypaths/session-id))
  (sift/insert-tracking)
  (facebook-analytics/insert-tracking)
  (talkable/insert)
  (places-autocomplete/insert)
  (refresh-account app-state)
  (refresh-current-order app-state)
  (doseq [feature (get-in app-state keypaths/features)]
    ;; trigger GA analytics, even though feature is already enabled
    (handle-message events/enable-feature {:feature feature})))

(defmethod perform-effects events/app-stop [_ event args _ app-state]
  (convert/remove-tracking)
  (riskified/remove-tracking)
  (sift/remove-tracking)
  (stringer/remove-tracking)
  (google-analytics/remove-tracking)
  (facebook-analytics/remove-tracking))

(defmethod perform-effects events/enable-feature [_ event args _ app-state]
  (when (and (experiments/old-taxon? app-state)
             ;;TODO get literal from experiments
             (= (:feature args) "old-taxon")
             (#{events/navigate-product-details events/navigate-category}
              (get-in app-state keypaths/navigation-event)))
    (effects/redirect events/navigate-home))
  (when (and (experiments/new-taxon-launch? app-state)
             ;;TODO get literal from experiments
             (= (:feature args) "one-drill-down")
             (= (get-in app-state keypaths/navigation-event)
                events/navigate-named-search))
    (redirect-named-search (categories/named-search->category
                            (get-in app-state (conj keypaths/navigation-args :named-search-slug))
                            (get-in app-state keypaths/categories)))))

(defmethod perform-effects events/external-redirect-welcome [_ event args _ app-state]
  (set! (.-location js/window) (get-in app-state keypaths/welcome-url)))

(defmethod perform-effects events/external-redirect-paypal-setup [_ event args _ app-state]
  (set! (.-location js/window) (get-in app-state keypaths/order-cart-payments-paypal-redirect-url)))

(defmethod perform-effects events/external-redirect-telligent [_ event args _ app-state]
  (set! (.-location js/window) (or (get-in app-state keypaths/telligent-community-url)
                                   config/telligent-community-url)))

(defmethod perform-effects events/control-navigate [_ event {:keys [navigation-message]} _ app-state]
  ;; A user has clicked a link
  ;; The URL has already changed. Save scroll position on the page they are
  ;; leaving, and handle the nav message.
  (handle-message events/navigation-save (-> (get-in app-state keypaths/navigation-stashed-stack-item)
                                             (assoc :final-scroll js/document.body.scrollTop)))
  (apply handle-message navigation-message))

(defmethod perform-effects events/browser-navigate [_ _ {:keys [navigation-message]} _ app-state]
  ;; A user has clicked the forward/back button, or maybe a special link that
  ;; simulates the back button (utils/route-back). The browser already knows
  ;; about the URL, so all we have to do is manipulate the undo/redo stacks and
  ;; handle the nav message.
  (let [leaving-stack-item {:final-scroll js/document.body.scrollTop}
        back               (first (get-in app-state keypaths/navigation-undo-stack))
        forward            (first (get-in app-state keypaths/navigation-redo-stack))]
    (condp routes/exact-page? navigation-message
      (:navigation-message back)
      (do
        (handle-message events/navigation-undo leaving-stack-item)
        (apply handle-message (assoc-in navigation-message [1 :nav-stack-item] back)))

      (:navigation-message forward)
      (do
        (handle-message events/navigation-redo leaving-stack-item)
        (apply handle-message (assoc-in navigation-message [1 :nav-stack-item] forward)))

      (apply handle-message navigation-message))))

(defmethod perform-effects events/redirect [_ event {:keys [nav-message]} _ app-state]
  (apply history/enqueue-redirect nav-message))

;; FIXME:(jm) This is all triggered on pages we're redirecting through. :(
(defmethod perform-effects events/navigate [_ event {:keys [query-params nav-stack-item] :as args} _ app-state]
  (let [args (dissoc args :nav-stack-item)]
    (handle-message events/control-menu-collapse-all)
    (refresh-account app-state)
    (api/get-sms-number)
    (api/get-promotions (get-in app-state keypaths/api-cache)
                        (or
                         (first (get-in app-state keypaths/order-promotion-codes))
                         (get-in app-state keypaths/pending-promo-code)))

    (let [order-number   (get-in app-state keypaths/order-number)
          loaded-order?  (boolean (get-in app-state (conj keypaths/order :total)))
          checkout-page? (routes/sub-page? [event args] [events/navigate-checkout])]
      (when (and order-number
                 (or (not checkout-page?)
                     (not loaded-order?)))
        (api/get-order order-number (get-in app-state keypaths/order-token))))
    (seo/set-tags app-state)
    (let [restore-scroll-top (:final-scroll nav-stack-item 0)]
      (if (zero? restore-scroll-top)
        ;; We can always snap to 0, so just do it immediately. (HEAT is unhappy if the page is scrolling underneath it.)
        (scroll/snap-to-top)
        ;; Otherwise give the screen some time to render before trying to restore scroll
        (handle-later events/snap {:top restore-scroll-top} 100)))

    (when-let [pending-promo-code (:sha query-params)]
      (cookie-jar/save-pending-promo-code
       (get-in app-state keypaths/cookie)
       pending-promo-code)
      (redirect event (update-in args [:query-params] dissoc :sha)))

    (if (routes/sub-page? [event args] [events/navigate-leads])
      (let [utm-params (some-> query-params
                               (select-keys [:utm_source :utm_medium :utm_campaign :utm_content :utm_term])
                               (set/rename-keys {:utm_source   "leads.utm-source"
                                                 :utm_medium   "leads.utm-medium"
                                                 :utm_campaign "leads.utm-campaign"
                                                 :utm_content  "leads.utm-content"
                                                 :utm_term     "leads.utm-term"})
                               (maps/filter-nil))]
        (when (seq utm-params)
          (cookie-jar/save-leads-utm-params
           (get-in app-state keypaths/cookie)
           utm-params)))

      (let [utm-params (some-> query-params
                               (select-keys [:utm_source :utm_medium :utm_campaign :utm_content :utm_term])
                               (set/rename-keys {:utm_source   :storefront/utm-source
                                                 :utm_medium   :storefront/utm-medium
                                                 :utm_campaign :storefront/utm-campaign
                                                 :utm_content  :storefront/utm-content
                                                 :utm_term     :storefront/utm-term})
                               (maps/filter-nil))]
        (when (seq utm-params)
          (cookie-jar/save-utm-params
           (get-in app-state keypaths/cookie)
           utm-params))))

    (when (get-in app-state keypaths/popup)
      (handle-message events/control-popup-hide))

    (exception-handler/refresh)

    (update-email-capture-session app-state)))

(defmethod perform-effects events/navigate-home [_ _ _ _ app-state]
  (when (= "welcome" (get-in app-state keypaths/store-slug))
    (redirect events/navigate-leads-home))
  (potentially-show-email-popup app-state))

(defmethod perform-effects events/navigate-content [_ [_ _ & static-content-id :as event] _ _ app-state]
  (when-not (= static-content-id
               (get-in app-state keypaths/static-id))
    (api/get-static-content event)))

(defmethod perform-effects events/navigate-content-about-us [_ _ _ _ app-state]
  (wistia/load))

(defmethod perform-effects events/navigate-shop-by-look [_ event {:keys [look-id]} _ app-state]
  (when-not look-id ;; we are on navigate-shop-by-look, not navigate-shop-by-look-details
    (pixlee/fetch-mosaic)))

(defmethod perform-effects events/navigate-shop-by-look-details [_ event {:keys [look-id]} _ app-state]
  (if-let [shared-cart-id (:shared-cart-id (accessors.pixlee/selected-look app-state))]
    (api/fetch-shared-cart shared-cart-id)
    (pixlee/fetch-image look-id)))

(defn fetch-current-named-search-album [app-state]
  (when-let [{:keys [slug]} (named-searches/current-named-search app-state)]
    (when-let [album-id (get-in config/pixlee [:albums slug])]
      (pixlee/fetch-album album-id slug))))

(defn hidden-search? [app-state named-search]
  (and (:stylist_only? named-search)
       (not (stylists/own-store? app-state))))

(defmethod perform-effects events/navigate-named-search [_ event args _ app-state]
  (if-let [category (when (experiments/new-taxon-launch? app-state)
                      (categories/named-search->category
                       (get-in app-state (conj keypaths/navigation-args :named-search-slug))
                       (get-in app-state keypaths/categories)))]
    (redirect-named-search category)
    (let [named-search (named-searches/current-named-search app-state)]
      (if (hidden-search? app-state named-search)
        (page-not-found)
        (do
          (reviews/insert-reviews)
          (refresh-named-search-products app-state named-search)
          (fetch-current-named-search-album app-state))))))

(defmethod perform-effects events/navigate-ugc-named-search [_ event args _ app-state]
  (let [named-search (named-searches/current-named-search app-state)]
    (if (hidden-search? app-state named-search)
      (page-not-found)
      (fetch-current-named-search-album app-state))))

(defmethod perform-effects events/pixlee-api-success-fetch-image [_ event _ _ app-state]
  (when-let [shared-cart-id (:shared-cart-id (accessors.pixlee/selected-look app-state))]
    (api/fetch-shared-cart shared-cart-id)))

(defmethod perform-effects events/pixlee-api-failure-fetch-album [_ event resp _ app-state]
  (page-not-found))

(defmethod perform-effects events/navigate-account [_ event args _ app-state]
  (when-not (get-in app-state keypaths/user-token)
    (redirect events/navigate-sign-in)))

(defmethod perform-effects events/navigate-stylist [_ event args _ app-state]
  (cond
    (not (get-in app-state keypaths/user-token))
    (redirect events/navigate-sign-in)

    (not (stylists/own-store? app-state))
    (page-not-found)

    :else nil))

(defmethod perform-effects events/navigate-stylist-account [_ event args _ app-state]
  (when-let [user-token (get-in app-state keypaths/user-token)]
    (uploadcare/insert)
    (api/get-states (get-in app-state keypaths/api-cache))
    (api/get-stylist-account (get-in app-state keypaths/user-id) user-token)))

(defmethod perform-effects events/navigate-gallery [_ event args _ app-state]
  (api/get-gallery (if (stylists/own-store? app-state)
                     {:user-id (get-in app-state keypaths/user-id)
                      :user-token (get-in app-state keypaths/user-token)}
                     {:stylist-id (get-in app-state keypaths/store-stylist-id)})))

(defmethod perform-effects events/navigate-gallery-image-picker [_ event args _ app-state]
  (if (stylists/own-store? app-state)
    (uploadcare/insert)
    (redirect events/navigate-gallery)))

(defmethod perform-effects events/control-delete-gallery-image [_ event {:keys [image-url]} _ app-state]
  (api/delete-gallery-image (get-in app-state keypaths/user-id)
                            (get-in app-state keypaths/user-token)
                            image-url))

(defmethod perform-effects events/api-success-gallery [_ event args _ app-state]
  (cond
    (not (stylists/gallery? app-state))
    (page-not-found)

    (and (stylists/own-store? app-state)
         (routes/exact-page? (get-in app-state keypaths/navigation-message) [events/navigate-gallery])
         (some (comp #{"pending"} :status) (get-in app-state keypaths/store-gallery-images)))
    (handle-later events/poll-gallery {} 5000)))

(defmethod perform-effects events/control [_ _ args _ app-state]
  (update-email-capture-session app-state))

(defmethod perform-effects events/control-email-captured-submit [_ _ args _ app-state]
  (when (empty? (get-in app-state keypaths/errors))
    (cookie-jar/save-email-capture-session (get-in app-state keypaths/cookie) "opted-in")))

(defmethod perform-effects events/app-restart [_ _ _ _]
  (.reload js/window.location))

(defmethod perform-effects events/api-end [_ event args previous-app-state app-state]
  (let [app-version    (get-in app-state keypaths/app-version)
        remote-version (:app-version args)
        needs-restart? (and app-version remote-version
                            (< config/allowed-version-drift (- remote-version app-version)))]
    (when needs-restart?
      (handle-later events/app-restart))))

(defmethod perform-effects events/api-success-telligent-login [_ event {:keys [cookie max-age]} _ app-state]
  (cookie-jar/save-telligent-cookie (get-in app-state keypaths/cookie) cookie max-age)
  (handle-message events/external-redirect-telligent))

(defmethod perform-effects events/api-success-stylist-commissions [_ event args _ app-state]
  (ensure-products app-state
                   (->> (get-in app-state keypaths/stylist-commissions-history)
                        (map :order)
                        (mapcat orders/product-items)
                        (map :product-id)
                        set)))

(defmethod perform-effects events/navigate-stylist-dashboard [_ event args _ app-state]
  (when-let [user-token (get-in app-state keypaths/user-token)]
    (api/get-stylist-stats (get-in app-state keypaths/user-id) user-token)))

(defmethod perform-effects events/navigate-stylist-dashboard-commissions [_ event args _ app-state]
  (when (zero? (get-in app-state keypaths/stylist-commissions-page 0))
    (handle-message events/control-stylist-commissions-fetch)))

(defmethod perform-effects events/control-stylist-commissions-fetch [_ _ args _ app-state]
  (let [user-id (get-in app-state keypaths/user-id)
        user-token (get-in app-state keypaths/user-token)
        page (inc (get-in app-state keypaths/stylist-commissions-page 0))]
    (when (and user-id user-token)
      (api/get-stylist-commissions user-id
                                   user-token
                                   {:page page}))))

(defmethod perform-effects events/navigate-stylist-dashboard-bonus-credit [_ event args _ app-state]
  (when (zero? (get-in app-state keypaths/stylist-bonuses-page 0))
    (handle-message events/control-stylist-bonuses-fetch)))

(defmethod perform-effects events/control-stylist-bonuses-fetch [_ event args _ app-state]
  (let [user-id    (get-in app-state keypaths/user-id)
        user-token (get-in app-state keypaths/user-token)
        page       (inc (get-in app-state keypaths/stylist-bonuses-page 0))]
    (when user-token
      (api/get-stylist-bonus-credits user-id
                                     user-token
                                     {:page page}))))

(defmethod perform-effects events/navigate-stylist-dashboard-referrals [_ event args _ app-state]
  (when (zero? (get-in app-state keypaths/stylist-referral-program-page 0))
    (handle-message events/control-stylist-referrals-fetch)))

(defmethod perform-effects events/control-stylist-referrals-fetch [_ event args _ app-state]
  (let [user-id    (get-in app-state keypaths/user-id)
        user-token (get-in app-state keypaths/user-token)
        page       (inc (get-in app-state keypaths/stylist-referral-program-page 0))]
    (when user-token
      (api/get-stylist-referral-program user-id
                                        user-token
                                        {:page page}))))

(defmethod perform-effects events/control-stylist-referral-submit [_ event args _ app-state]
  (api/send-referrals
   (get-in app-state keypaths/session-id)
   {:referring-stylist-id (get-in app-state keypaths/store-stylist-id)
    :referrals (map #(select-keys % [:fullname :email :phone]) (get-in app-state keypaths/stylist-referrals))}))

(def cart-error-codes
  {"paypal-incomplete"      "We were unable to complete your order with PayPal. Please try again."
   "paypal-invalid-address" "Unfortunately, Mayvenn products cannot be delivered to this address at this time. Please choose a new shipping destination."})

(defmethod perform-effects events/navigate-cart [_ event args _ app-state]
  (api/get-shipping-methods)
  (api/get-states (get-in app-state keypaths/api-cache))
  (stripe/insert)
  (refresh-current-order app-state)
  (when-let [error-msg (-> args :query-params :error cart-error-codes)]
    (handle-message events/flash-show-failure {:message error-msg})))

(defn ensure-bucketed-for [app-state experiment]
  (let [already-bucketed? (contains? (get-in app-state keypaths/experiments-bucketed) experiment)]
    (when-not already-bucketed?
      (when-let [variation (experiments/variation-for app-state experiment)]
        (handle-message events/bucketed-for {:experiment experiment :variation variation})
        (handle-message events/enable-feature {:experiment experiment :feature (:feature variation)})))))

(defmethod perform-effects events/navigate-checkout [_ event args _ app-state]
  (let [have-cart? (get-in app-state keypaths/order-number)]
    (if-not have-cart?
      (redirect events/navigate-cart)
      (let [authenticated?  (or (get-in app-state keypaths/user-id)
                                (get-in app-state keypaths/checkout-as-guest))
            authenticating? (nav/checkout-auth-events event)]
        (when-not (or authenticated? authenticating?)
          (redirect events/navigate-checkout-returning-or-guest))))))

(defmethod perform-effects events/navigate-checkout-sign-in [_ event args _ app-state]
  (facebook/insert))

(defmethod perform-effects events/navigate-checkout-returning-or-guest [_ event args _ app-state]
  (places-autocomplete/remove-containers)
  (api/get-states (get-in app-state keypaths/api-cache))
  (facebook/insert))

(defn- fetch-saved-cards [app-state]
  (when-let [user-id (get-in app-state keypaths/user-id)]
    (api/get-saved-cards user-id (get-in app-state keypaths/user-token))))

(defmethod perform-effects events/navigate-checkout-address [_ event args _ app-state]
  (places-autocomplete/remove-containers)
  (api/get-states (get-in app-state keypaths/api-cache))
  (fetch-saved-cards app-state))

(defmethod perform-effects events/navigate-checkout-payment [dispatch event args _ app-state]
  (fetch-saved-cards app-state)
  (stripe/insert))

(defmethod perform-effects events/navigate-checkout-confirmation [_ event args _ app-state]
  (stripe/insert)
  (api/get-shipping-methods))

(defmethod perform-effects events/navigate-order-complete [_ event {{:keys [paypal order-token]} :query-params number :number} _ app-state]
  (when (not (get-in app-state keypaths/user-id))
    (facebook/insert))
  (when (and number order-token)
    (api/get-completed-order number order-token))
  (when paypal
    (redirect events/navigate-order-complete {:number number})))

(defmethod perform-effects events/navigate-friend-referrals [_ event args _ app-state]
  (talkable/show-referrals app-state))

(defmethod perform-effects events/navigate-account-referrals [_ event args _ app-state]
  (talkable/show-referrals app-state))

(defmethod perform-effects events/api-success-get-completed-order [_ event order _ app-state]
  (handle-message events/order-completed order))

(defn redirect-to-return-navigation [app-state]
  (apply redirect
         (get-in app-state keypaths/return-navigation-message)))

(defn ^:private redirect-to-telligent-as-user [app-state]
  (api/telligent-sign-in (get-in app-state keypaths/session-id)
                         (get-in app-state keypaths/user-id)
                         (get-in app-state keypaths/user-token))
  (handle-message events/flash-later-show-success {:message "Redirecting to the stylist community"}) )

(defn redirect-when-signed-in [app-state]
  (when (get-in app-state keypaths/user-email)
    (if (get-in app-state keypaths/telligent-community-url)
      (redirect-to-telligent-as-user app-state)
      (do
        (redirect-to-return-navigation app-state)
        (handle-message events/flash-later-show-success {:message "You are already signed in."})))))

(defmethod perform-effects events/navigate-sign-in [_ event args _ app-state]
  (facebook/insert)
  (redirect-when-signed-in app-state))
(defmethod perform-effects events/navigate-sign-out [_ _ {{:keys [telligent-url]} :query-params} _ app-state]
  (if telligent-url
    (do
      (cookie-jar/clear-telligent-session (get-in app-state keypaths/cookie))
      (handle-message events/external-redirect-telligent))
    (handle-message events/sign-out)))
(defmethod perform-effects events/navigate-sign-up [_ event args _ app-state]
  (facebook/insert)
  (redirect-when-signed-in app-state))
(defmethod perform-effects events/navigate-forgot-password [_ event args _ app-state]
  (facebook/insert)
  (redirect-when-signed-in app-state))
(defmethod perform-effects events/navigate-reset-password [_ event args _ app-state]
  (facebook/insert)
  (redirect-when-signed-in app-state))

(defmethod perform-effects events/navigate-not-found [_ event args _ app-state]
  (handle-message events/flash-show-failure
                  {:message "The page you were looking for could not be found."}))

(defmethod perform-effects events/control-sign-in-submit [_ event args _ app-state]
  (api/sign-in (get-in app-state keypaths/session-id)
               (stringer/browser-id)
               (get-in app-state keypaths/sign-in-email)
               (get-in app-state keypaths/sign-in-password)
               (get-in app-state keypaths/store-stylist-id)
               (get-in app-state keypaths/order-number)
               (get-in app-state keypaths/order-token)))

(defmethod perform-effects events/control-sign-up-submit [_ event {:keys [order]} _ app-state]
  (let [{:keys [number token]} (or (get-in app-state keypaths/order)
                                   (get-in app-state keypaths/completed-order))]
    (api/sign-up (get-in app-state keypaths/session-id)
                 (stringer/browser-id)
                 (get-in app-state keypaths/sign-up-email)
                 (get-in app-state keypaths/sign-up-password)
                 (get-in app-state keypaths/store-stylist-id)
                 number
                 token)))

(defmethod perform-effects events/control-facebook-sign-in [_ event args _ app-state]
  (facebook/start-log-in app-state))

(defmethod perform-effects events/control-facebook-reset [_ event args _ app-state]
  (facebook/start-reset app-state))

(defmethod perform-effects events/facebook-success-sign-in [_ _ {:keys [authResponse]} _ app-state]
  (let [{:keys [number token]} (or (get-in app-state keypaths/order)
                                   (get-in app-state keypaths/completed-order))]
    (api/facebook-sign-in (get-in app-state keypaths/session-id)
                          (stringer/browser-id)
                          (:userID authResponse)
                          (:accessToken authResponse)
                          (get-in app-state keypaths/store-stylist-id)
                          number
                          token)))

(defmethod perform-effects events/facebook-failure-sign-in [_ _ args _ app-state]
  (handle-message events/flash-show-failure
                  {:message "Could not sign in with Facebook.  Please try again, or sign in with email and password."}))

(defmethod perform-effects events/facebook-email-denied [_ _ args _ app-state]
  (handle-message events/flash-show-failure
                  {:message "We need your Facebook email address to communicate with you about your orders. Please try again."}))

(defn- abort-pending-requests [requests]
  (doseq [{xhr :xhr} requests] (when xhr (-abort xhr))))

(defmethod perform-effects events/control-sign-out [_ event args _ app-state]
  (handle-message events/sign-out))

(defmethod perform-effects events/control-add-to-bag
  [dispatch event {:keys [variant quantity] :as args} _ app-state]
  (api/add-to-bag
   (get-in app-state keypaths/session-id)
   {:variant variant
    :quantity quantity
    :stylist-id (get-in app-state keypaths/store-stylist-id)
    :token (get-in app-state keypaths/order-token)
    :number (get-in app-state keypaths/order-number)
    :user-id (get-in app-state keypaths/user-id)
    :user-token (get-in app-state keypaths/user-token)}))

(defmethod perform-effects events/control-forgot-password-submit [_ event args _ app-state]
  (api/forgot-password (get-in app-state keypaths/session-id) (get-in app-state keypaths/forgot-password-email)))

(defmethod perform-effects events/control-reset-password-submit [_ event args _ app-state]
  (if (empty? (get-in app-state keypaths/reset-password-password))
    (handle-message events/flash-show-failure {:message "Your password cannot be blank."})
    (api/reset-password (get-in app-state keypaths/session-id)
                        (stringer/browser-id)
                        (get-in app-state keypaths/reset-password-password)
                        (get-in app-state keypaths/reset-password-token)
                        (get-in app-state keypaths/order-number)
                        (get-in app-state keypaths/order-token))))

(defmethod perform-effects events/facebook-success-reset [_ event facebook-response _ app-state]
  (api/facebook-reset-password (get-in app-state keypaths/session-id)
                               (stringer/browser-id)
                               (-> facebook-response :authResponse :userID)
                               (-> facebook-response :authResponse :accessToken)
                               (get-in app-state keypaths/reset-password-token)
                               (get-in app-state keypaths/order-number)
                               (get-in app-state keypaths/order-token)))

(defn save-cookie [app-state]
    (cookie-jar/save-order (get-in app-state keypaths/cookie)
                           (get-in app-state keypaths/order))
    (cookie-jar/save-user (get-in app-state keypaths/cookie)
                          (get-in app-state keypaths/user)))

(defmethod perform-effects events/control-account-profile-submit [_ event args _ app-state]
  (when (empty? (get-in app-state keypaths/errors))
    (api/update-account (get-in app-state keypaths/session-id)
                        (get-in app-state keypaths/user-id)
                        (get-in app-state keypaths/manage-account-email)
                        (get-in app-state keypaths/manage-account-password)
                        (get-in app-state keypaths/user-token))))

(defmethod perform-effects events/control-cart-update-coupon [_ event args _ app-state]
  (let [coupon-code (get-in app-state keypaths/cart-coupon-code)]
    (when-not (empty? coupon-code)
      (api/add-promotion-code (get-in app-state keypaths/session-id)
                              (get-in app-state keypaths/order-number)
                              (get-in app-state keypaths/order-token)
                              coupon-code
                              false))))

(defmethod perform-effects events/control-cart-share-show [dispatch event args _ app-state]
  (api/create-shared-cart (get-in app-state keypaths/session-id)
                          (get-in app-state keypaths/order-number)
                          (get-in app-state keypaths/order-token)))

(defmethod perform-effects events/control-cart-line-item-inc [_ event {:keys [variant]} _ app-state]
  (api/inc-line-item (get-in app-state keypaths/session-id) (get-in app-state keypaths/order) {:variant variant}))

(defmethod perform-effects events/control-create-order-from-shared-cart [_ event {:keys [shared-cart-id]} _ app-state]
  (api/create-order-from-cart (get-in app-state keypaths/session-id)
                              shared-cart-id
                              (get-in app-state keypaths/user-id)
                              (get-in app-state keypaths/user-token)
                              (get-in app-state keypaths/store-stylist-id)))

(defmethod perform-effects events/control-cart-line-item-dec [_ event {:keys [variant]} _ app-state]
  (api/dec-line-item (get-in app-state keypaths/session-id) (get-in app-state keypaths/order) {:variant variant}))

(defmethod perform-effects events/control-cart-remove [_ event variant-id _ app-state]
  (api/delete-line-item (get-in app-state keypaths/session-id) (get-in app-state keypaths/order) variant-id))

(defmethod perform-effects events/control-checkout-cart-submit [dispatch event args _ app-state]
  ;; If logged in, this will send user to checkout-address. If not, this sets
  ;; things up so that if the user chooses sign-in from the returning-or-guest
  ;; page, then signs-in, they end up on the address page. Convoluted.
  (history/enqueue-navigate events/navigate-checkout-address))

(defmethod perform-effects events/control-checkout-cart-apple-pay [dispatch event args _ app-state]
  (apple-pay/begin (get-in app-state keypaths/order)
                   (get-in app-state keypaths/session-id)
                   (cookie-jar/retrieve-utm-params (get-in app-state keypaths/cookie))
                   (get-in app-state keypaths/shipping-methods)
                   (get-in app-state keypaths/states)))

(defmethod perform-effects events/control-checkout-cart-paypal-setup [dispatch event args _ app-state]
  (let [order (get-in app-state keypaths/order)]
    (api/update-cart-payments
     (get-in app-state keypaths/session-id)
     {:order (-> app-state
                 (get-in keypaths/order)
                 (select-keys [:token :number])
                 ;;; Get ready for some nonsense!
                 ;;
                 ;; Paypal requires that urls are *double* url-encoded, such as
                 ;; the token part of the return url, but that *query
                 ;; parameters* are only singley encoded.
                 ;;
                 ;; Thanks for the /totally sane/ API, PayPal.
                 (assoc-in [:cart-payments]
                           {:paypal {:amount (get-in app-state keypaths/order-total)
                                     :mobile-checkout? (not (device/isDesktop))
                                     :return-url (str stylist-urls/store-url "/orders/" (:number order) "/paypal/"
                                                      (url-encode (url-encode (:token order)))
                                                      "?sid="
                                                      (url-encode (get-in app-state keypaths/session-id)))
                                     :callback-url (str config/api-base-url "/v2/paypal-callback?number=" (:number order)
                                                        "&order-token=" (url-encode (:token order)))
                                     :cancel-url (str stylist-urls/store-url "/cart?error=paypal-cancel")}}))
      :event events/external-redirect-paypal-setup})))

(defmethod perform-effects events/control-stylist-account-profile-submit [_ _ args _ app-state]
  (let [session-id      (get-in app-state keypaths/session-id)
        user-id         (get-in app-state keypaths/user-id)
        user-token      (get-in app-state keypaths/user-token)
        stylist-account (get-in app-state keypaths/stylist-manage-account)]
    (api/update-stylist-account-profile session-id user-id user-token stylist-account)))

(defmethod perform-effects events/control-stylist-account-password-submit [_ _ args _ app-state]
  (let [session-id      (get-in app-state keypaths/session-id)
        user-id         (get-in app-state keypaths/user-id)
        user-token      (get-in app-state keypaths/user-token)
        stylist-account (get-in app-state keypaths/stylist-manage-account)]
    (when (empty? (get-in app-state keypaths/errors))
      (api/update-stylist-account-password session-id user-id user-token stylist-account))))

(defn reformat-green-dot [{:keys [expiration_date] :as attributes}]
  (when (seq attributes)
    (let [[month year] (parse-expiration (str expiration_date))]
      (-> attributes
          (dissoc :expiration_date)
          (assoc :expiration_month month)
          (assoc :expiration_year year)
          (update :card_number (comp clojure.string/join filter-cc-number-format str))))))

(defmethod perform-effects events/control-stylist-account-commission-submit [_ _ args _ app-state]
  (let [session-id      (get-in app-state keypaths/session-id)
        user-id         (get-in app-state keypaths/user-id)
        user-token      (get-in app-state keypaths/user-token)
        stylist-account (-> app-state
                            (get-in keypaths/stylist-manage-account)
                            (update :green_dot_payout_attributes reformat-green-dot))]
    (api/update-stylist-account-commission session-id user-id user-token stylist-account)))

(defmethod perform-effects events/control-stylist-account-social-submit [_ _ _ _ app-state]
  (let [session-id      (get-in app-state keypaths/session-id)
        user-id         (get-in app-state keypaths/user-id)
        user-token      (get-in app-state keypaths/user-token)
        stylist-account (get-in app-state keypaths/stylist-manage-account)]
    (api/update-stylist-account-social session-id user-id user-token stylist-account)))

(defmethod perform-effects events/uploadcare-api-failure [_ _ {:keys [error error-data]} _ app-state]
  (exception-handler/report error error-data))

(defmethod perform-effects events/image-picker-component-mounted [_ _ args _ app-state]
  (uploadcare/dialog args))

(defmethod perform-effects events/uploadcare-api-success-upload-portrait [_ _ {:keys [cdnUrl]} _ app-state]
  (let [user-id    (get-in app-state keypaths/user-id)
        user-token (get-in app-state keypaths/user-token)
        session-id (get-in app-state keypaths/session-id)]
    (api/update-stylist-account-portrait session-id user-id user-token {:portrait-url cdnUrl})
    (history/enqueue-navigate events/navigate-stylist-account-profile)))

(defmethod perform-effects events/uploadcare-api-success-upload-gallery [_ event {:keys [cdnUrl]} _ app-state]
  (let [user-id    (get-in app-state keypaths/user-id)
        user-token (get-in app-state keypaths/user-token)]
    (api/append-stylist-gallery user-id user-token {:gallery-urls [cdnUrl]})
    (history/enqueue-navigate events/navigate-gallery)))

(defmethod perform-effects events/control-checkout-update-addresses-submit [_ event args _ app-state]
  (let [guest-checkout? (get-in app-state keypaths/checkout-as-guest)
        billing-address (get-in app-state keypaths/checkout-billing-address)
        shipping-address (get-in app-state keypaths/checkout-shipping-address)
        update-addresses (if guest-checkout? api/guest-update-addresses api/update-addresses)]
    (update-addresses
     (get-in app-state keypaths/session-id)
     (cond-> (merge (select-keys (get-in app-state keypaths/order) [:number :token])
                    {:billing-address billing-address :shipping-address shipping-address})
       guest-checkout?
       (assoc :email (get-in app-state keypaths/checkout-guest-email))

       (get-in app-state keypaths/checkout-bill-to-shipping-address)
       (assoc :billing-address shipping-address)))))

(defmethod perform-effects events/control-checkout-shipping-method-select [_ event args _ app-state]
  (api/update-shipping-method (get-in app-state keypaths/session-id)
                              (merge (select-keys (get-in app-state keypaths/order) [:number :token])
                                     {:shipping-method-sku (get-in
                                                            app-state
                                                            keypaths/checkout-selected-shipping-method-sku)})))

(defmethod perform-effects events/stripe-success-create-token [_ _ {:keys [token place-order?]} _ app-state]
  (api/update-cart-payments
   (get-in app-state keypaths/session-id)
   {:order        (-> app-state
                      (get-in keypaths/order)
                      (select-keys [:token :number])
                      (assoc :cart-payments (get-in app-state keypaths/checkout-selected-payment-methods))
                      (assoc-in [:cart-payments :stripe :source] (:id token))
                      (assoc-in [:cart-payments :stripe :save?] (boolean (and (get-in app-state keypaths/user-id)
                                                                              (get-in app-state keypaths/checkout-credit-card-save)))))
    :navigate     events/navigate-checkout-confirmation
    :place-order? place-order?}))

(defn create-stripe-token [app-state args]
  ;; create stripe token (success handler commands waiter w/ payment methods (success navigates to confirm))
  (stripe/create-token (get-in app-state keypaths/stripe-card-element)
                       (get-in app-state keypaths/checkout-credit-card-name)
                       (get-in app-state (conj keypaths/order :billing-address))
                       args))

(defmethod perform-effects events/control-checkout-payment-method-submit [_ event args _ app-state]
  (handle-message events/flash-dismiss)
  (let [use-store-credit (pos? (get-in app-state keypaths/user-total-available-store-credit))
        covered-by-store-credit (orders/fully-covered-by-store-credit?
                                 (get-in app-state keypaths/order)
                                 (get-in app-state keypaths/user))
        selected-saved-card-id (get-in app-state keypaths/checkout-credit-card-selected-id)]
    (if (and use-store-credit covered-by-store-credit)
      ;; command waiter w/ payment methods(success handler navigate to confirm)
      (api/update-cart-payments
       (get-in app-state keypaths/session-id)
       {:order (-> app-state
                   (get-in keypaths/order)
                   (select-keys [:token :number])
                   (merge {:cart-payments (get-in app-state keypaths/checkout-selected-payment-methods)}))
        :navigate events/navigate-checkout-confirmation})
      ;; create stripe token (success handler commands waiter w/ payment methods (success  navigates to confirm))
      (if (and selected-saved-card-id (not= selected-saved-card-id "add-new-card"))
        (api/update-cart-payments
         (get-in app-state keypaths/session-id)
         {:order (-> app-state
                     (get-in keypaths/order)
                     (select-keys [:token :number])
                     (merge {:cart-payments (get-in app-state keypaths/checkout-selected-payment-methods)})
                     (assoc-in [:cart-payments :stripe :source] selected-saved-card-id))
          :navigate events/navigate-checkout-confirmation})
        (create-stripe-token app-state args)))))

(defmethod perform-effects events/control-checkout-remove-promotion [_ _ {:keys [code]} _ app-state]
  (api/remove-promotion-code (get-in app-state keypaths/session-id) (get-in app-state keypaths/order) code))

(defmethod perform-effects events/control-checkout-confirmation-submit [_ event {:keys [place-order?] :as args} _ app-state]
  (if place-order?
    (create-stripe-token app-state args)
    (api/place-order (get-in app-state keypaths/session-id)
                     (get-in app-state keypaths/order)
                     (cookie-jar/retrieve-utm-params (get-in app-state keypaths/cookie)))))

(defmethod perform-effects events/api-success-auth [_ _ _ _ app-state]
  (letfn [(fetch-most-recent-order [app-state]
            (when-not (get-in app-state keypaths/order-number)
              (refresh-current-order app-state)))]
    (doto app-state
      save-cookie
      fetch-most-recent-order
      redirect-to-return-navigation)))

(defmethod perform-effects events/api-success-auth-sign-in [_ _ _ _ app-state]
  (if (get-in app-state keypaths/telligent-community-url)
    (redirect-to-telligent-as-user app-state)
    (handle-message events/flash-later-show-success {:message "Logged in successfully"})))

(defmethod perform-effects events/api-success-auth-sign-up [dispatch event args _ app-state]
  (handle-message events/flash-later-show-success {:message "Welcome! You have signed up successfully."}))

(defmethod perform-effects events/api-success-auth-reset-password [dispatch event args _ app-state]
  (handle-message events/flash-later-show-success {:message "Your password was changed successfully. You are now signed in."}))

(defmethod perform-effects events/api-success-forgot-password [_ event args _ app-state]
  (history/enqueue-navigate events/navigate-home)
  (handle-message events/flash-later-show-success {:message "You will receive an email with instructions on how to reset your password in a few minutes."}))

(defmethod perform-effects events/api-success-manage-account [_ event args _ app-state]
  (save-cookie app-state)
  (history/enqueue-navigate events/navigate-home)
  (handle-message events/flash-later-show-success {:message "Account updated"}))

(defmethod perform-effects events/poll-stylist-portrait [_ event args _ app-state]
  (api/refresh-stylist-portrait (get-in app-state keypaths/user-id)
                                (get-in app-state keypaths/user-token)))

(defmethod perform-effects events/poll-gallery [_ event args _ app-state]
  (when (stylists/own-store? app-state)
    (api/get-gallery {:user-id    (get-in app-state keypaths/user-id)
                      :user-token (get-in app-state keypaths/user-token)})))

(defmethod perform-effects events/api-success-stylist-account
  [_ event {:keys [stylist]} previous-app-state app-state]
  (when (:green_dot_available stylist)
    (handle-message events/enable-feature {:feature "green-dot"}))
  ;; Portrait becomes pending either when the user navigates to an account page
  ;; or when they change their portrait.
  ;; In either case, we start the poll loop.
  (when-let [became-pending? (and
                              (changed? previous-app-state app-state keypaths/stylist-portrait-status)
                              (= "pending" (get-in app-state keypaths/stylist-portrait-status)))]
    (handle-later events/poll-stylist-portrait {} 5000))
  (save-cookie app-state))

(defmethod perform-effects events/api-success-stylist-account-profile [_ event args _ app-state]
  (handle-message events/flash-show-success {:message "Profile updated"}))

(defmethod perform-effects events/api-success-stylist-account-password [_ event args _ app-state]
  (handle-message events/flash-show-success {:message "Password updated"}))

(defmethod perform-effects events/api-success-stylist-account-commission [_ event args _ app-state]
  (handle-message events/flash-show-success {:message "Commission settings updated"}))

(defmethod perform-effects events/api-success-stylist-account-social [_ event args _ app-state]
  (handle-message events/flash-show-success {:message "Social settings updated"}))

(defmethod perform-effects events/api-success-stylist-account-portrait [_ event {:keys [updated?]} previous-app-state app-state]
  (when updated?
    (handle-message events/flash-show-success {:message "Photo updated"}))
  (when-let [still-pending? (= "pending"
                               (get-in previous-app-state keypaths/stylist-portrait-status)
                               (get-in app-state keypaths/stylist-portrait-status))]
    (handle-later events/poll-stylist-portrait {} 5000)))

(defmethod perform-effects events/api-success-send-stylist-referrals [_ event args _ app-state]
  (handle-later events/control-popup-hide {} 2000))

(defn add-pending-promo-code [app-state {:keys [number token] :as order}]
  (when-let [pending-promo-code (get-in app-state keypaths/pending-promo-code)]
    (api/add-promotion-code (get-in app-state keypaths/session-id) number token pending-promo-code true)))

(defmethod perform-effects events/api-success-get-order [_ event order _ app-state]
  (ensure-products app-state (map :product-id (orders/product-items order)))
  (if (and (orders/incomplete? order)
           (= (:number order)
              (get-in app-state keypaths/order-number)))
    (do
      (save-cookie app-state)
      (add-pending-promo-code app-state order))
    (cookie-jar/clear-order (get-in app-state keypaths/cookie))))

(defmethod perform-effects events/api-success-update-order-place-order [_ event {:keys [order]} _ app-state]
  (handle-message events/order-completed order))

(defmethod perform-effects events/order-completed [dispatch event order _ app-state]
  (cookie-jar/clear-order (get-in app-state keypaths/cookie))
  (talkable/show-pending-offer app-state))

(defmethod perform-effects events/api-success-update-order-update-cart-payments [_ event {:keys [order place-order?]} _ app-state]
  (when place-order?
    (api/place-order (get-in app-state keypaths/session-id)
                     order
                     (cookie-jar/retrieve-utm-params (get-in app-state keypaths/cookie)))))

(defmethod perform-effects events/api-success-update-order [_ event {:keys [order navigate event]} _ app-state]
  (save-cookie app-state)
  (when event
    (handle-message event {:order order}))
  (when navigate
    (history/enqueue-navigate navigate {:number (:number order)})))

(defmethod perform-effects events/api-failure-no-network-connectivity [_ event response _ app-state]
  (handle-message events/flash-show-failure {:message "Something went wrong. Please refresh and try again or contact customer service."}))

(defmethod perform-effects events/api-failure-bad-server-response [_ event response _ app-state]
  (handle-message events/flash-show-failure {:message "Uh oh, an error occurred. Reload the page and try again."}))

(defmethod perform-effects events/flash-show [_ event {:keys [scroll?] :or {scroll? true}} _ app-state]
  (when scroll?
    (scroll/snap-to-top)))

(defmethod perform-effects events/snap [_ _ {:keys [top]} _ app-state]
  (scroll/snap-to top))

(defmethod perform-effects events/api-failure-pending-promo-code [_ event args _ app-state]
  (cookie-jar/clear-pending-promo-code (get-in app-state keypaths/cookie)))

(defmethod perform-effects events/api-failure-order-not-created-from-shared-cart [_ event args _ app-state]
  (history/enqueue-navigate events/navigate-home))

(defmethod perform-effects events/api-failure-errors [_ event errors _ app-state]
  (condp = (:error-code errors)
    "stripe-card-failure"      (when (= (get-in app-state keypaths/navigation-event)
                                        events/navigate-checkout-confirmation)
                                 (redirect events/navigate-checkout-payment)
                                 (handle-later events/api-failure-errors errors)
                                 (scroll/snap-to-top))
    "promotion-not-found"      (scroll-promo-field-to-top)
    "ineligible-for-promotion" (scroll-promo-field-to-top)
    (scroll/snap-to-top)))

(defmethod perform-effects events/api-success-add-to-bag [dispatch event args _ app-state]
  (save-cookie app-state)
  (add-pending-promo-code app-state (get-in app-state keypaths/order))
  (handle-later events/added-to-bag))

(defmethod perform-effects events/added-to-bag [_ _ _ _ app-state]
  (when-let [el (.querySelector js/document "[data-ref=cart-button]")]
    (scroll/scroll-to-elem el)))

(defmethod perform-effects events/reviews-component-mounted [_ event args _ app-state]
  (let [expected-reviews-count 2
        actual-reviews-count (get-in app-state keypaths/review-components-count)]
    (when (= expected-reviews-count actual-reviews-count)
      (reviews/start))))

(defmethod perform-effects events/reviews-component-will-unmount [_ event args _ app-state]
  (when (= 0 (get-in app-state keypaths/review-components-count))
    (reviews/stop)))

(defmethod perform-effects events/checkout-address-component-mounted
  [_ event {:keys [address-elem address-keypath]} _ app-state]
  (places-autocomplete/attach address-elem address-keypath))

(defmethod perform-effects events/api-success-update-order-remove-promotion-code [_ _ _ _ app-state]
  (handle-message events/flash-show-success {:message "The coupon code was successfully removed from your order."
                                             :scroll? false}))

(defmethod perform-effects events/api-success-update-order-add-promotion-code [_ _ {allow-dormant? :allow-dormant?} _ app-state]
  (when-not allow-dormant?
    (handle-message events/flash-show-success {:message "The coupon code was successfully applied to your order."
                                               :scroll? false})
    (scroll-promo-field-to-top))
  (api/get-promotions (get-in app-state keypaths/api-cache)
                      (first (get-in app-state keypaths/order-promotion-codes))))

(defmethod perform-effects events/inserted-talkable [_ event args _ app-state]
  (talkable/show-pending-offer app-state)
  (when (#{events/navigate-friend-referrals events/navigate-account-referrals}
         (get-in app-state keypaths/navigation-event))
    (talkable/show-referrals app-state)))

(defmethod perform-effects events/inserted-stripe [_ event args _ app-state]
  (apple-pay/verify-eligible app-state))

(defmethod perform-effects events/control-email-captured-dismiss [_ event args _ app-state]
  (update-email-capture-session app-state))

(defmethod perform-effects events/control-stylist-community [_ event args _ app-state]
  (api/telligent-sign-in (get-in app-state keypaths/session-id)
                         (get-in app-state keypaths/user-id)
                         (get-in app-state keypaths/user-token)))

(defmethod perform-effects events/sign-out [_ event args app-state-before app-state]
  (cookie-jar/clear-account (get-in app-state keypaths/cookie))
  (handle-message events/control-menu-collapse-all)
  (abort-pending-requests (get-in app-state keypaths/api-requests))
  (if (= events/navigate-home (get-in app-state keypaths/navigation-event))
    (handle-message events/flash-show-success {:message "Logged out successfully"})
    (do
      (history/enqueue-navigate events/navigate-home)
      (handle-message events/flash-later-show-success {:message "Logged out successfully"})))
  (api/sign-out (get-in app-state keypaths/session-id)
                (stringer/browser-id)
                (get-in app-state-before keypaths/user-id)
                (get-in app-state-before keypaths/user-token)))

(defmethod perform-effects events/api-success-shared-cart-fetch [_ event {:keys [cart]} _ app-state]
  (ensure-products app-state (map :product-id (:line-items cart))))
