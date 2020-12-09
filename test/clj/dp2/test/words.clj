(ns dp2.test.words
  (:require
   [clojure.test :refer :all]
   [dp2.words.unknown :refer :all]
   [mount.core :as mount]))


(deftest test-words
  (testing "Unknown words"

    (testing "Supplement hyphen words"
      (is (extract-hyphen-words "Hallo- wie geh't heute oder -morgen aber nicht-so-schnell-")
          '("hallo┊" "┊morgen" "schnell┊"))

      (is (extract-hyphen-words "geh't- noch schneller")
          '("geht's┊")))

    (testing "Ellipsis words"
      (is (extract-ellipsis-words "...HĘllo Leute ...wie gehts'... euch hEute...")
          '("┊hęllo" "┊wie" "gehts'┊" "heute┊")))))
