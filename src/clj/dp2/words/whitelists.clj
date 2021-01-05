(ns dp2.words.whitelists
  (:require [clojure.java.io :as io]
            [dp2.config :refer [env]]
            [dp2.louis :as louis]
            [dp2.words :as words]))

(defn word-to-dots [word]
  )

(defn table-filename [grade document-id opts]
  (let [tables-dir (env :tables-dir)
        key (cond
              (= grade 1) "g1"
              (and (= grade 2) (:name opts)) "g2-name"
              (and (= grade 2) (:place opts)) "g2-place"
              :else "g2")]
    (format "%s/sbs-de-%s-white-%s.mod" tables-dir key document-id)))

(defn write-table [w grade translator words]
  (doseq [{:keys [untranslated uncontracted contracted]} words]
    (let [braille (if (= grade 1) uncontracted contracted)]
      (when (not= braille (translator untranslated))
        (let [line (format "word %s\t%s" untranslated braille)]
          (.write w line)
          (.newLine w))))))

(defn write-local-table [document-id grade opts words]
  (let [filename (table-filename grade document-id opts)
        tables (louis/get-tables grade opts)
        translator #(louis/translate % tables)]
    (with-open [w (io/writer filename)]
      (write-table w grade translator words))))

(defn is-plain? [{:keys [type]}] (some? (#{0 1 3 5} type)))

(defn write-local-tables [document-id words]
  (let [contracted-words (filter :contracted words)]
    (write-local-table document-id 1 {} (filter :uncontracted words))
    (write-local-table document-id 2 {} (filter is-plain? contracted-words))
    (write-local-table document-id 2 {:name true} (filter words/is-name? contracted-words))
    (write-local-table document-id 2 {:place true} (filter words/is-place? contracted-words))))
