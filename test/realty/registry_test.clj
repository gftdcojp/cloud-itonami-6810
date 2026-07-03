(ns realty.registry-test
  (:require [clojure.test :refer [deftest is]]
            [realty.registry :as r]))

(deftest certificate-is-a-draft-not-a-real-recording
  (let [result (r/register-closing "addr" ["p"] 0 "JPN" 1)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest closing-assigns-closing-number
  (let [result (r/register-closing "東京都" ["party:yamada"] 50000000 "JPN" 7)]
    (is (= (get result "closing_number") "JPN-00000007"))
    (is (= (get-in result ["record" "immutable"]) true))
    (is (= (get-in result ["record" "kind"]) "closing-draft"))))

(deftest closing-validation-rules
  (let [bad-args [["" ["p"] 0 "JPN"]
                  ["addr" [] 0 "JPN"]
                  ["addr" ["p"] -1 "JPN"]
                  ["addr" ["p"] 0 ""]]]
    (doseq [[address parties price jurisdiction] bad-args]
      (is (thrown? Exception
                   (r/register-closing address parties price jurisdiction 1))))))

(deftest amendment-is-append-only
  (let [c (r/register-closing "addr" ["p"] 0 "JPN" 1)
        hist (r/append [] c)
        chg (r/register-amendment (get c "closing_number") {"address" "new"} "2026-07-03")
        hist2 (r/append hist chg)]
    (is (and (= (count hist) 1) (= (count hist2) 2)))
    (is (= (get-in hist2 [0 "kind"]) "closing-draft"))
    (is (= (get-in hist2 [1 "kind"]) "amendment-draft"))))
