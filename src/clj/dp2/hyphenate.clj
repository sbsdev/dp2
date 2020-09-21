(ns dp2.hyphenate
  "Functionality to hyphenate words for a given spelling.

  The list of provided hyphenators is stateful. After a change of the
  hyphenation dictionaries in the file system the hyphenators list
  needs to be reloaded."
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import ch.sbs.jhyphen.Hyphenator))


(def hyphen-dictionaries
  {:default "/usr/share/hyphen/hyph_de_DE.dic"})

(defn- load-hyphenators
  "Given a map of keys for spelling and paths to hyphenation
  dictionaries return a map of keys and materialized Hyphenators
  constructed from the dictionaries in the paths."
  [dics]
  (zipmap
    (keys dics)
    (map #(new Hyphenator (io/file %)) (vals dics))))

(def hyphenators
  "The \"base hyphenators\" that use the hyphen tables as they are
  provided by upstream without the exceptions from the database"
  (load-hyphenators hyphen-dictionaries))

(defn- hyphenate*
  "Hyphenate given `text` using a given `hyphenator`"
  [text hyphenator]
  (if (string/blank? text)
    ""
    (.hyphenate hyphenator text \- nil)))

(defn hyphenate
  "Hyphenate the given `text` for given `spelling`. If no `spelling` is
  specified the default dictionary is used."
  ([text]
   (hyphenate text :default))
  ([text spelling]
   (let [default (get hyphenators :default) 
         hyphenator (get hyphenators spelling default)]
     (hyphenate* text hyphenator))))
