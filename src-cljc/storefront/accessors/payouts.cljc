(ns storefront.accessors.payouts)

(defn cash-out-eligible? [payout-method]
  (boolean (#{"Mayvenn::GreenDotPayoutMethod"
              "Mayvenn::PaypalPayoutMethod"} (:type payout-method))))
