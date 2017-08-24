(ns catalog.selector
  (:require [datascript.core :as d]))

(defn ^:private ->clauses
  [m] (mapv (fn [[k v]] ['?s k v]) m))

;; TODO selector/query should understand sets as values
(defn query [db & criteria]
  (let [query (->> criteria
                   (reduce merge)
                   ->clauses
                   (concat [:find '(pull ?s [*])
                            :where]))]
    (some->> db
             deref
             (d/q query)
             (map first))))

(defn new-db [coll]
  (d/db-with (d/empty-db) coll))