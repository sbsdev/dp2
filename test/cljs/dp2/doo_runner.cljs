(ns dp2.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [dp2.core-test]))

(doo-tests 'dp2.core-test)

