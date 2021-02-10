(ns dp2.whitelists.hyphenation
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dp2.db.core :as db]
            [dp2.hyphenate :as hyphenate]
            [dp2.metrics :as metrics]
            [iapetos.collector.fn :as prometheus]
            [mount.core :refer [defstate]]
            [org.tobereplaced.nio.file :as nio])
  (:import java.nio.file.StandardCopyOption))

(def ^:private substrings-program
  "Program to prepare the hyphenation dics. Expected to be installed
  on the system."
  "/usr/share/libhyphen/substrings.pl")

(def dictionaries {0 {:whitelist "/tmp/whitelist_de_DE_OLDSPELL.txt"
                      :dictionary "/usr/share/hyphen/generated/hyph_de_DE_OLDSPELL.dic"
                      :original "/usr/share/hyphen/hyph_de_DE_OLDSPELL_base.dic"}
                   1 {:whitelist "/tmp/whitelist_de.txt"
                      :dictionary "/usr/share/hyphen/generated/hyph_de_DE.dic"
                      :original "/usr/share/hyphen/hyph_de_DE_base.dic"}})

(defn- prepare-for-libhyphen
  "Prepare a hyphenation string for consumption by libhyphen"
  [s]
  (-> s
   (string/replace #"(.)" "$18") ; place "8" between each char
   (string/replace #"^(.*)$" ".$1.") ; suround with .
   (string/replace "8-8" "9") ; give hyphens more weight
   (string/replace "8." "."))) ; drop the last 8

(defn- get-hyphenations [spelling]
  (->>
   ;; get words for given spelling from db
   (db/get-hyphenation {:spelling spelling})
   ;; filter the ones that aren't hyphenated correctly
   (remove
    (fn [{:keys [word hyphenation]}]
      (= (hyphenate/hyphenate word spelling) hyphenation)))
   (map :hyphenation)
   (remove string/blank?) ; drop empty ones
   (map string/lower-case) ; make sure it's lowercase
   sort
   (map prepare-for-libhyphen)))

(defn- write-file [words file-name original-dict]
  (with-open [w (io/writer file-name :encoding "ISO-8859-1")]
    ;; insert the original dict
    (io/copy (io/file original-dict) w :encoding "ISO-8859-1")
    (doseq [word words]
      (.write w word)
      (.newLine w))))

(defn- set-file-permissions!
  "Make sure `file` is readable for group and others"
  [file]
  (let [permissions (conj (nio/posix-file-permissions file)
                          (nio/posix-file-permission :group-read)
                          (nio/posix-file-permission :others-read))]
    (nio/set-posix-file-permissions! file permissions)))

(defn- export*
  "Export all hyphenation patterns from the database and prepare for
  libhyphen consumption, i.e. run them through substrings.pl"
  []
  ;; I tried to run this in parallel (simply by using (dorun (pmap))
  ;; instead of (doseq)) but as it turns out the jobs are so uneven,
  ;; i.e. the first one is very small compared to the second one, we
  ;; end up waiting the same amount of time.
  (doseq [[spelling {:keys [whitelist dictionary original]}] dictionaries]
    (->
     spelling
     get-hyphenations
     (write-file whitelist original))
    (log/info "Exporting hyphenation whitelist for" dictionary)
    (log/debug "Wrote the whitelist" whitelist)
    (let [tmp-file (nio/absolute-path (nio/create-temp-file! "hyphen-" ".dic"))
          result (sh substrings-program whitelist (str tmp-file))]
      (log/debugf "substrings.pl %s %s returned %s" whitelist tmp-file result)
      (set-file-permissions! tmp-file)
      (nio/move! tmp-file dictionary StandardCopyOption/REPLACE_EXISTING)
      (log/debugf "Move %s to %s" tmp-file dictionary))))

(defn- exporter
  "Create a channel and attach a listener to it so that events can be
  debounced, i.e. while an export is pending only store one more
  request. Return the channel where export requests can be sent to."
  []
  ;; we want to debounce, i.e. rate limit the amount of exports (see
  ;; https://en.wikipedia.org/wiki/Switch#Contact_bounce.) In other
  ;; words do not initiate another export while you are still doing
  ;; one. For that we simply use a dropping buffer of size one which
  ;; makes sure that we remember any request that came in while we
  ;; were blocked doing the export.
  (let [debounce-chan (async/chan (async/dropping-buffer 1))]
    (async/go-loop []
      (when-let [_ (async/<! debounce-chan)]
        (export*)
        (recur)))
    debounce-chan))

(defstate export-chan
  :start (exporter)
  :stop (when export-chan (async/close! export-chan)))

(defn export
  "Like [export*] but with debouncing"
  []
  (async/>!! export-chan true))

(prometheus/instrument! metrics/registry #'export*)

