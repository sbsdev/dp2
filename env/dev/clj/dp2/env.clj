(ns dp2.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [dp2.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[dp2 started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[dp2 has shut down successfully]=-"))
   :middleware wrap-dev})
