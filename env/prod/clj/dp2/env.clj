(ns dp2.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[dp2 started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[dp2 has shut down successfully]=-"))
   :middleware identity})
