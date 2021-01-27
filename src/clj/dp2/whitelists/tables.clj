(ns dp2.whitelists.tables
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dp2.config :refer [env]]
            [dp2.db.core :as db]
            [dp2.louis :as louis]
            [dp2.metrics :as metrics]
            [dp2.words :as words]
            [iapetos.collector.fn :as prometheus]))

(def ascii-to-dots
  {\A "1"
   \B "12"
   \C "14"
   \D "145"
   \E "15"
   \F "124"
   \G "1245"
   \H "125"
   \I "24"
   \J "245"
   \K "13"
   \L "123"
   \M "134"
   \N "1345"
   \O "135"
   \P "1234"
   \Q "12345"
   \R "1235"
   \S "234"
   \T "2345"
   \U "136"
   \V "1236"
   \X "1346"
   \Y "13456"
   \Z "1356"
   \0 "346"
   \1 "16"
   \2 "126"
   \3 "146"
   \4 "1456"
   \5 "156"
   \6 "1246"
   \7 "12456"
   \8 "1256"
   \9 "246"
   \& "12346"
   \% "123456"
   \[ "12356"
   \^ "2346"
   \] "23456"
   \W "2456"
   \, "2"
   \; "23"
   \: "25"
   \/ "256"
   \? "26"
   \+ "235"
   \= "2356"
   \( "236"
   \* "35"
   \) "356"
   \. "3"
   \\ "34"
   \@ "345"
   \# "3456"
   \" "4"
   \! "5"
   \> "45"
   \$ "46"
   \_ "456"
   \< "56"
   \' "6"
   \à "123568"
   \á "168"
   \â "1678"
   \ã "34678"
   \å "345678"
   \æ "478"
   \ç "1234678"
   \è "23468"
   \é "1234568"
   \ê "12678"
   \ë "12468"
   \ì "348"
   \í "1468"
   \î "14678"
   \ï "124568"
   \ð "23458"
   \ñ "13458"
   \ò "3468"
   \ó "14568"
   \ô "145678"
   \õ "1358"
   \ø "24678"
   \ù "234568"
   \ú "1568"
   \û "15678"
   \ý "24568"
   \þ "12348"
   \ÿ "134568"
   \œ "246789"
   ;; FIXME: is the following char even in words in the db?
   \- "36a"
   \ā "4-1"
   \ă "4-1"
   \ą "4-1"
   \ć "4-14"
   \ĉ "4-14"
   \ċ "4-14"
   \č "4-14"
   \ď "4-145"
   \đ "4-145"
   \ē "4-15"
   \ė "4-15"
   \ę "4-15"
   \ğ "4-1245"
   \ģ "4-1245"
   \ĥ "4-125"
   \ħ "4-125"
   \ĩ "4-24"
   \ī "4-24"
   \į "4-24"
   \ı "4-24"
   \ĳ "4-245"
   \ĵ "4-245"
   \ķ "4-13"
   \ĺ "4-123"
   \ļ "4-123"
   \ľ "4-123"
   \ŀ "4-123"
   \ł "4-123"
   \ń "4-1345"
   \ņ "4-1345"
   \ň "4-1345"
   \ŋ "4-1345"
   \ō "4-135"
   \ŏ "4-135"
   \ő "4-135"
   \ŕ "4-1235"
   \ŗ "4-1235"
   \ř "4-1235"
   \ś "4-234"
   \ŝ "4-234"
   \ş "4-234"
   \š "4-234"
   \ţ "4-2345"
   \ť "4-2345"
   \ŧ "4-2345"
   \ũ "4-136"
   \ū "4-136"
   \ŭ "4-136"
   \ů "4-136"
   \ű "4-136"
   \ų "4-136"
   \ŵ "4-2456"
   \ŷ "4-13456"
   \ź "4-1356"
   \ż "4-1356"
   \ž "4-1356"
   \ǎ "4-1"
   \ẁ "4-2456"
   \ẃ "4-2456"
   \ẅ "4-2456"
   \ỳ "4-13456"
   \┊ "abcdef"
   })

(defn to-dots [s]
  (->>
   s
   (map ascii-to-dots)
   (string/join "-")))

(defn table-filename
  ([grade opts]
   (table-filename grade nil opts))
  ([grade document-id opts]
   (let [tables-dir (env :tables-dir)
         key (cond
               (= grade 1) "g1"
               (and (= grade 2) (:name opts)) "g2-name"
               (and (= grade 2) (:place opts)) "g2-place"
               :else "g2")
         document-extension (if document-id (str "-" document-id) "")]
     (format "%s/sbs-de-%s-white%s.mod" tables-dir key document-extension))))

(defn write-table [w grade translator words]
  (doseq [{:keys [untranslated uncontracted contracted homograph-disambiguation] :as word} words]
    (let [untranslated (if (words/is-homograph? word)
                         (string/replace homograph-disambiguation "|" words/braille-dummy-text)
                         untranslated)
          braille (if (= grade 1) uncontracted contracted)]
      (when (not= braille (translator untranslated))
        (let [line (format "word %s\t%s" untranslated (to-dots braille))]
          (.write w line)
          (.newLine w))))))

(defn write-local-table [document-id grade opts words]
  (let [filename (table-filename grade document-id opts)
        tables (louis/get-tables grade opts)
        translator #(louis/translate % tables)]
    (with-open [w (io/writer filename)]
      (write-table w grade translator words))))

(defn write-global-table [grade opts words]
  (let [filename (table-filename grade opts)
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

(defn write-global-tables []
  (write-global-table 1 {} (db/get-global-words {:grade 1}))
  (write-global-table 2 {} (db/get-global-words {:grade 2 :types [0 1 3 5]}))
  (write-global-table 2 {:name true} (db/get-global-words {:grade 2 :types [1 2]}))
  (write-global-table 2 {:place true} (db/get-global-words {:grade 2 :types [3 4]})))

(defn export-local-tables [document-id]
  (log/infof "Exporting local tables for %s" document-id)
  (write-local-tables document-id (db/get-local-words-aggregated {:id document-id})))

(defn export-global-tables []
  (log/info "Exporting global tables")
  (write-global-tables)
  (log/info "Finished exporting global tables"))

(prometheus/instrument! metrics/registry #'export-local-tables)
(prometheus/instrument! metrics/registry #'export-global-tables)
