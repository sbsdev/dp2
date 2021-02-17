(ns daisyproducer2.app
  (:require [daisyproducer2.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
