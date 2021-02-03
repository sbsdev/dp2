(ns dp2.test.words
  (:require
   [clojure.test :refer :all]
   [dp2.words.unknown :refer :all]
   [mount.core :as mount]))


(deftest test-words
  (testing "Unknown words"

    (testing "Extracting supplement hyphen words"
      (is (= (extract-hyphen-words "Hallo- wie geh't heute oder -morgen aber nicht-so-schnell-")
             #{"hallo┊" "┊morgen" "schnell┊"}))

      (is (= (extract-hyphen-words "geht's- noch schneller")
             #{"geht's┊"})))

    (testing "Extracting ellipsis words"
      (is (= (extract-ellipsis-words "...HĘllo Leute ...wie gehts'... euch hEute...")
             #{"┊hęllo" "┊wie" "gehts'┊" "heute┊"})))

    (testing "Filtering special words"
      (is (= (filter-special-words "...HĘllo Leute ...wie gehts'... euch hEute... wahrlich gross- äh, nein gross-artig")
             " Leute   euch  wahrlich  äh, nein gross-artig")))

    (testing "Extracting plain words"
      (is (= (extract-words "...HĘllo Leute ...wie gehts'... euch hEute... wahrlich gross- äh, nein gross-artig")
             #{"wahrlich" "gross" "artig" "euch" "leute" "nein"}))

