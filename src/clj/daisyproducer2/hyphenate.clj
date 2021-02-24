(ns daisyproducer2.hyphenate
  "Functionality to hyphenate words for a given spelling.

  The list of provided hyphenators is stateful. After a change of the
  hyphenation dictionaries in the file system the hyphenators list
  needs to be reloaded."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [daisyproducer2.config :refer [env]]
            [mount.core :refer [defstate]])
  (:import ch.sbs.jhyphen.Hyphenator))

(defn- load-hyphenators
  "Given a map of keys for spelling and paths to hyphenation
  dictionaries return a map of keys and materialized Hyphenators
  constructed from the dictionaries in the paths."
  [dics]
  (zipmap
    (keys dics)
    (map #(new Hyphenator (io/file %)) (vals dics))))

(defstate hyphenators
  :start
  (if-let [hyphen-dictionaries (env :hyphen-dictionaries)]
    (load-hyphenators hyphen-dictionaries)
    (log/warn "Hyphen dictionaries not found, please set :hyphen-dictionaries in the config file"))
  :stop
  (when hyphenators
    (doseq [[_ hyphenator] hyphenators]
      (.close hyphenator))))

(defn- hyphenate*
  "Hyphenate given `text` using a given `hyphenator`"
  [text hyphenator]
  (if (string/blank? text)
    ""
    (.hyphenate hyphenator text \- nil)))

(defn hyphenate
  "Hyphenate the given `text` for given `spelling` against the base
  upstream dictionaries which contain no exceptions"
  [text spelling]
  (let [default (get hyphenators 1) ;; new spelling
        hyphenator (get hyphenators spelling default)]
    (hyphenate* text hyphenator)))
