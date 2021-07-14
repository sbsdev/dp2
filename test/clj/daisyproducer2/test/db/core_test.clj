(ns daisyproducer2.test.db.core-test
  (:require
   [daisyproducer2.db.core :refer [*db*] :as db]
   [luminus-migrations.core :as migrations]
   [clojure.test :refer :all]
   [next.jdbc :as jdbc]
   [daisyproducer2.config :refer [env]]
   [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'daisyproducer2.config/env
     #'daisyproducer2.db.core/*db*)
    #_(migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(deftest ^:database test-global-words
  (jdbc/with-transaction [t-conn *db* {:rollback-only true}]
    (is (= 1 (count (db/find-global-words t-conn {:limit 1 :offset 0 :untranslated "hausboot"} {}))))
    (is (= 2 (count (db/get-confirmable-words t-conn {:limit 2 :offset 0} {}))))))

(deftest ^:database test-confirmable
  (jdbc/with-transaction [t-conn *db* {:rollback-only true}]
    (let [confirmable (count (db/get-confirmable-words t-conn {:limit 10000 :offset 0} {}))
          word {:untranslated "hahaha2" :contracted "H+H+H+2" :uncontracted "HAHAHA2"
                :type 0 :homograph_disambiguation "" :document_id 644 :islocal false}
          _ (db/insert-local-word t-conn word)]
      (is (= (inc confirmable) (count (db/get-confirmable-words t-conn {:limit 10000 :offset 0} {})))))))
