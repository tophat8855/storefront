(ns leads.a1.applied-thank-you
  (:require #?@(:clj [[storefront.component-shim :as component]]
                :cljs [[storefront.browser.cookie-jar :as cookie-jar]
                       [storefront.component :as component]])
            [leads.header :as header]
            [storefront.components.footer :as footer]
            [storefront.assets :as assets]
            [storefront.config :as config]
            [storefront.effects :as effects]
            [storefront.events :as events]
            [storefront.keypaths]
            [leads.keypaths :as keypaths]
            [storefront.components.ui :as ui]))

(defn social-link [url image-path]
  [:a {:item-prop "sameAs"
       :href url}
   [:img {:src (assets/path image-path)}]])

(defn ^:private component [data owner opts]
  (component/create
   [:div
    (header/built-component data nil)
    [:div.bg-teal.white
     [:div.max-580.center.mx-auto
      [:div.h4.py3.px4
       [:p.m2 "Thank you! We received your request to become a Mayvenn Stylist. "
        "Stay tuned – we're reviewing your info and will get back to you shortly with next steps."]]
      [:div.h4.py1
       [:p.my2 "We'd love to connect on social media:"]
       [:div
        (social-link "https://www.facebook.com/MayvennHair" "//ucarecdn.com/5d322fe0-d0cb-4d62-a014-342b46fad2c1/-/format/auto/facebookicon.png")
        (social-link "http://instagram.com/mayvennhair" "//ucarecdn.com/05a11abb-b2e3-4b89-a43a-68d40f71242d/-/format/auto/instagramiconwhite.png")
        (social-link "http://www.pinterest.com/mayvennhair/" "//ucarecdn.com/dc77e98c-1fda-4267-aba2-dce4b1dd0ecc/-/format/auto/pinteresticon.png")
        (social-link "https://twitter.com/mayvennhair" "//ucarecdn.com/41683ed1-1494-4c44-a3b0-41a25eab744e/-/format/auto/twittericon.png")]]
      [:div.col-10.mx-auto.py8
       (ui/youtube-responsive "https://www.youtube.com/embed/MjhjIB2s1Uk")]]]
    (component/build footer/minimal-component (:footer data) nil)]))

(defn ^:private query [data]
  {:footer {:call-number config/mayvenn-leads-a1-call-number}
   :faq    {:text-number config/mayvenn-leads-sms-number
            :call-number config/mayvenn-leads-a1-call-number}})

(defn built-component [data opts]
  (component/build component (query data) opts))
