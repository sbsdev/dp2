(ns daisyproducer2.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [daisyproducer2.core-test]))

(doo-tests 'daisyproducer2.core-test)

