(ns storefront.components.svg
  (:require [sablono.core :refer-macros [html]]))

(def micro-dollar-sign
  (html
   [:svg {:viewBox "0 0 14 13", :height "13", :width "14"}
    [:g {:stroke-width "1" :stroke "#9B9B9B" :fill "none"}
     [:path {:d "M13 6.5c0 3.3-2.7 6-6 6s-6-2.7-6-6 2.7-6 6-6 6 2.7 6 6z"}]
     [:path {:d "M5.7 7.8c0 .72.58 1.3 1.3 1.3.72 0 1.3-.58 1.3-1.3 0-.72-.58-1.3-1.3-1.3-.72 0-1.3-.58-1.3-1.3 0-.72.58-1.3 1.3-1.3.72 0 1.3.58 1.3 1.3M7 3.1v6.8"}]]]))

(def large-dollar
  (html
   [:svg {:version "1.1" :viewBox "0 0 72 72" :height "72px" :width "72px"}
    [:g {:fill "none" :stroke-width "1" :stroke "#FFFFFF"}
     [:circle {:cx "36" :cy "36" :r "35"}]
     [:g {:transform "translate(1, 1)"}
      [:path {:d "M27.3913043,42.6086957 C27.3913043,46.8117391 30.7969565,50.2173913 35,50.2173913 C39.2030435,50.2173913 42.6086957,46.8117391 42.6086957,42.6086957 C42.6086957,38.4071739 39.2030435,35 35,35 C30.7969565,35 27.3913043,31.5943478 27.3913043,27.3913043 C27.3913043,23.1897826 30.7969565,19.7826087 35,19.7826087 C39.2030435,19.7826087 42.6086957,23.1897826 42.6086957,27.3913043"}]
      [:path {:d "M35,15.2173913 V54.7826087"}]]]]))

(def large-percent
  (html
   [:svg {:version "1.1" :viewBox "0 0 72 72" :height "72px" :width "72px"}
    [:g {:fill "none" :stroke-width "1" :stroke "#FFFFFF"}
     [:g {:transform "translate(1, 1)"}
      [:path {:d "M22.826087,47.173913 L47.173913,22.826087"}]
      [:g
       [:path {:d "M30.4347826,25.8695652 C30.4347826,28.391087 28.3895652,30.4347826 25.8695652,30.4347826 C23.3495652,30.4347826 21.3043478,28.391087 21.3043478,25.8695652 C21.3043478,23.3495652 23.3495652,21.3043478 25.8695652,21.3043478 C28.3895652,21.3043478 30.4347826,23.3495652 30.4347826,25.8695652 L30.4347826,25.8695652 Z"}]
       [:path {:d "M48.6956522,44.1304348 C48.6956522,46.6519565 46.6504348,48.6956522 44.1304348,48.6956522 C41.6104348,48.6956522 39.5652174,46.6519565 39.5652174,44.1304348 C39.5652174,41.6104348 41.6104348,39.5652174 44.1304348,39.5652174 C46.6504348,39.5652174 48.6956522,41.6104348 48.6956522,44.1304348 L48.6956522,44.1304348 Z"}]
       [:path {:d "M70,35 C70,54.3306522 54.3306522,70 35,70 C15.6723913,70 0,54.3306522 0,35 C0,15.6708696 15.6723913,0 35,0 C54.3306522,0 70,15.6708696 70,35 L70,35 Z"}]]]]]))

(def large-payout
  (html
   [:svg {:version "1.1" :viewBox "0 0 62 60" :height "60px" :width "62px"}
    [:g {:fill "none" :stroke-width "1" :stroke "#FFFFFF"}
     [:g
      {:transform "translate(1, 1)"}
      [:path {:d "M0,54.0451909 L10.6086957,54.0451909 L10.6086957,34.2724636 L0,34.2724636 L0,54.0451909 Z"}]
      [:path {:d "M10.6086957,51.4090909 C38.4565217,60.6363636 29.173913,60.6363636 61,44.8181818 C58.1807391,42.0183636 55.9542391,41.3553182 53.0434783,42.1818182 L41.2837391,46.0599091"}]
      [:path {:d "M10.6086957,36.9090909 L18.5652174,36.9090909 C24.8044565,36.9090909 29.173913,40.8636364 30.5,42.1818182 L38.4565217,42.1818182 C42.6827609,42.1818182 42.6827609,47.4545455 38.4565217,47.4545455 L23.8695652,47.4545455"}]
      [:path {:d "M35.8043478,7.90909091 C35.8043478,12.2775455 39.3662174,15.8181818 43.7608696,15.8181818 C48.1555217,15.8181818 51.7173913,12.2775455 51.7173913,7.90909091 C51.7173913,3.54063636 48.1555217,0 43.7608696,0 C39.3662174,0 35.8043478,3.54063636 35.8043478,7.90909091 L35.8043478,7.90909091 Z"}]
      [:path {:d "M23.8695652,26.3636364 C23.8695652,30.7320909 27.4314348,34.2727273 31.826087,34.2727273 C36.2207391,34.2727273 39.7826087,30.7320909 39.7826087,26.3636364 C39.7826087,21.9951818 36.2207391,18.4545455 31.826087,18.4545455 C27.4314348,18.4545455 23.8695652,21.9951818 23.8695652,26.3636364 L23.8695652,26.3636364 Z"}]
      [:path {:d "M31.3714286,23.7272727 L31.3714286,29"}]
      [:path {:d "M43.5714286,5.27272727 L43.5714286,10.5454545"}]]]]))

(def large-mail
  (html
   [:svg
    {:viewBox "0 0 44 44" :height "44px" :width "44px"}
    [:defs [:path#path-1 {:d "M0,44 L44,44 L44,0 L0,0 L0,44 Z"}]]
    [:g {:fill-rule "evenodd" :fill "none" :stroke-width "1" :stroke "none"}
     [:path
      {:fill "#36B4D4"
       :d "M15.1482222,20.5186667 L30.5848889,20.5186667 C30.8508444,20.5186667 31.0664444,20.3030667 31.0664444,20.0371111 C31.0664444,19.7711556 30.8508444,19.5555556 30.5848889,19.5555556 L15.1482222,19.5555556 C14.8822667,19.5555556 14.6666667,19.7711556 14.6666667,20.0371111 C14.6666667,20.3030667 14.8822667,20.5186667 15.1482222,20.5186667 L15.1482222,20.5186667 Z"}]
     [:path
      {:fill "#36B4D4"
       :d "M22,43.0364 C15.6395556,43.0364 9.93373333,40.1949778 6.07346667,35.7192 L6.07346667,25.6260889 L21.6827111,39.2836889 C21.7741333,39.3633778 21.8865778,39.4034667 22,39.4034667 C22.1134222,39.4034667 22.2258667,39.3633778 22.3172889,39.2836889 L37.9260444,25.6260889 L37.9260444,35.7206667 C34.0657778,40.1954667 28.3599556,43.0364 22,43.0364 L22,43.0364 Z M0.9636,22 C0.9636,10.3996444 10.4001333,0.9636 22,0.9636 C33.6003556,0.9636 43.0364,10.4001333 43.0364,22 C43.0364,26.6894222 41.4929778,31.0239111 38.8896444,34.5258222 L38.8896444,24.5647111 C38.8896444,24.5358667 38.8823111,24.5089778 38.8779111,24.4816 C38.8955111,24.3295556 38.8485778,24.1726222 38.7253778,24.0640889 L33.4923111,19.4851556 C33.4351111,19.4357778 33.3696,19.4054667 33.3011556,19.3873778 L33.3011556,10.8440444 C33.3011556,10.2632444 32.8274222,9.79048889 32.2466222,9.79048889 L11.7528889,9.79048889 C11.1720889,9.79048889 10.6993333,10.2627556 10.6993333,10.8440444 L10.6993333,19.3971556 C10.6440889,19.4167111 10.5908,19.4450667 10.5443556,19.4856444 L5.31031111,24.0645778 C5.21791111,24.1457333 5.16853333,24.2547556 5.15435556,24.3672 C5.14995556,24.3774667 5.14702222,24.3872444 5.1436,24.398 C5.12355556,24.4503111 5.10986667,24.5060444 5.10986667,24.5652 L5.10986667,34.5248444 C2.50653333,31.0234222 0.9636,26.6884444 0.9636,22 L0.9636,22 Z M33.3011556,25.0467556 L37.1252444,25.0467556 L33.3011556,28.3932 L33.3011556,25.0467556 Z M33.3011556,20.5983556 L37.2836444,24.0836444 L33.3011556,24.0836444 L33.3011556,20.5983556 Z M11.6624444,29.1372889 L11.6624444,10.8440444 C11.6624444,10.7961333 11.7044889,10.7540889 11.7524,10.7540889 L32.2461333,10.7540889 C32.2945333,10.7540889 32.3370667,10.7961333 32.3370667,10.8440444 L32.3370667,28.9045778 C32.3370667,29.0013778 32.3722667,29.0864444 32.4216444,29.1617333 L22,38.2809778 L11.6453333,29.2218667 C11.6512,29.1930222 11.6624444,29.1671111 11.6624444,29.1372889 L11.6624444,29.1372889 Z M10.6993333,25.0467556 L10.6993333,28.3936889 L6.87426667,25.0467556 L10.6993333,25.0467556 Z M10.6993333,24.0836444 L6.75204444,24.0836444 L10.6993333,20.6301333 L10.6993333,24.0836444 L10.6993333,24.0836444 Z M38.8867111,36.0819556 C42.0762222,32.2632444 44,27.3523556 44,22 C44,9.86871111 34.1308,0 22,0 C9.8692,0 0,9.86871111 0,22 C0,27.3508889 1.92231111,32.2598222 5.10986667,36.0780444 L5.10986667,36.1572444 C5.10986667,36.4241778 5.32546667,36.6392889 5.59191111,36.6392889 C5.59337778,36.6392889 5.59484444,36.6383111 5.59631111,36.6383111 C9.6272,41.1522222 15.4860444,44 22,44 C28.5638222,44 34.4617778,41.1062667 38.4960889,36.5332 C38.7141333,36.4916444 38.8798667,36.3102667 38.8867111,36.0819556 L38.8867111,36.0819556 Z"}]
     [:path
      {:fill "#36B4D4"
       :d "M31.0664444,27.8593333 C31.0664444,27.5928889 30.8508444,27.3777778 30.5848889,27.3777778 L15.1482222,27.3777778 C14.8822667,27.3777778 14.6666667,27.5928889 14.6666667,27.8593333 C14.6666667,28.1252889 14.8822667,28.3408889 15.1482222,28.3408889 L30.5848889,28.3408889 C30.8508444,28.3408889 31.0664444,28.1252889 31.0664444,27.8593333"}]
     [:path
      {:fill "#36B4D4"
       :d "M15.1482222,24.4297778 L30.5848889,24.4297778 C30.8508444,24.4297778 31.0664444,24.2141778 31.0664444,23.9487111 C31.0664444,23.6822667 30.8508444,23.4666667 30.5848889,23.4666667 L15.1482222,23.4666667 C14.8822667,23.4666667 14.6666667,23.6822667 14.6666667,23.9487111 C14.6666667,24.2136889 14.8822667,24.4297778 15.1482222,24.4297778"}]
     [:mask#mask-2 {:fill "white"} [:use {:xlinkHref "#path-1"}]]
     [:path#Fill-9
      {:mask "url(#mask-2)"
       :fill "#36B4D4"
       :d "M15.6302667,16.2785333 L24.8345778,16.2785333 L24.8345778,14.6529778 L15.6302667,14.6529778 L15.6302667,16.2785333 Z M15.5584,17.2416444 L24.9059556,17.2416444 C25.3977778,17.2416444 25.7972,16.8417333 25.7972,16.3504 L25.7972,14.5801333 C25.7972,14.0892889 25.3977778,13.6888889 24.9059556,13.6888889 L15.5584,13.6888889 C15.0665778,13.6888889 14.6666667,14.0888 14.6666667,14.5801333 L14.6666667,16.3504 C14.6666667,16.8417333 15.0665778,17.2416444 15.5584,17.2416444 L15.5584,17.2416444 Z"}]]]))

(defn adjustable-check [svg-options]
  [:svg (merge {:viewBox "0 0 14 14"} svg-options)
   [:g {:stroke-linejoin "round"
        :stroke-linecap "round"
        :stroke-width "0.5"
        :fill "none"
        :transform "translate(1,1)"}
    [:path {:d "M8.61,4.17 L4.7,7.83 L3.39,6.52"}]
    [:circle {:cy "6" :cx "6" :r "6"}]]])

(defn bag [opts quantity]
  [:svg (merge {:version "1.1", :viewBox "0 0 25 35", :height "35px", :width "25px"}
               opts)
   [:g {:fill-rule "evenodd",
        :stroke-width "1",
        :stroke "none",
        :transform "translate(-721, -17)"
        :class (if (pos? quantity) "fill-teal" "fill-black")}
    [:path
     {:d "M738.309958,26.7716667 C738.569958,26.7716667 738.808292,26.9883333 738.808292,27.27 C738.808292,27.5516667 738.569958,27.7683333 738.309958,27.7683333 C738.028292,27.7683333 737.811625,27.5516667 737.811625,27.27 C737.811625,26.9883333 738.028292,26.7716667 738.309958,26.7716667 M727.953292,26.7716667 C728.234958,26.7716667 728.451625,26.9883333 728.451625,27.27 C728.451625,27.5516667 728.234958,27.7683333 727.953292,27.7683333 C727.693292,27.7683333 727.454958,27.5516667 727.454958,27.27 C727.454958,26.9883333 727.693292,26.7716667 727.953292,26.7716667 M726.024958,23.7166667 L727.671625,23.7166667 L727.671625,26.23 C727.216625,26.36 726.891625,26.7716667 726.891625,27.27 C726.891625,27.855 727.368292,28.3533333 727.953292,28.3533333 C728.559958,28.3533333 729.036625,27.855 729.036625,27.27 C729.036625,26.7716667 728.711625,26.36 728.256625,26.23 L728.256625,23.7166667 L730.921625,23.7166667 L735.341625,23.7166667 L738.006625,23.7166667 L738.006625,26.23 C737.551625,26.36 737.226625,26.7716667 737.226625,27.27 C737.226625,27.855 737.703292,28.3533333 738.309958,28.3533333 C738.894958,28.3533333 739.371625,27.855 739.371625,27.27 C739.371625,26.7716667 739.046625,26.36 738.591625,26.23 L738.591625,23.7166667 L740.238292,23.7166667 C741.646625,23.7166667 742.924958,24.8866667 743.076625,26.2733333 L744.658292,39.9233333 C744.831264,41.3313056 743.878292,42.4146389 742.469597,42.4146389 L733.153292,42.4146389 L733.131625,42.4146389 L733.109958,42.4146389 L723.793292,42.4146389 C722.384958,42.4146389 721.431625,41.3313056 721.583292,39.9233333 L723.164958,26.2733333 C723.338292,24.8866667 724.616625,23.7166667 726.024958,23.7166667 M733.131625,17.585 C735.839958,17.585 738.006625,19.7733333 738.006625,22.4816667 L738.006625,23.1533333 L735.341625,23.1533333 L730.921625,23.1533333 L728.256625,23.1533333 L728.256625,22.4816667 C728.256625,19.7733333 730.423292,17.585 733.131625,17.585 M733.131625,17 C730.119958,17 727.671625,19.4483333 727.671625,22.4816667 L727.671625,23.1533333 L726.024958,23.1533333 C724.313292,23.1533333 722.796625,24.4966667 722.601625,26.2083333 L721.019958,39.8583333 C720.824958,41.57 722.081625,43 723.793292,43 L733.109958,43 L733.153292,43 L742.469597,43 C744.181264,43 745.438292,41.57 745.243292,39.8583333 L743.661625,26.2083333 C743.466625,24.4966667 741.949958,23.1533333 740.238292,23.1533333 L738.591625,23.1533333 L738.591625,22.4816667 C738.591625,19.4483333 736.143292,17 733.131625,17"}]]])
