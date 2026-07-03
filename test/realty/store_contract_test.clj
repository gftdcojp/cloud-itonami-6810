(ns realty.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and the
  Datomic-backed (langchain.db) store satisfy the same contract is what
  makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `formation.store-contract-test` /
  `talent.store-contract-test` for the same pattern on the other actors
  in this family."
  (:require [clojure.test :refer [deftest is testing]]
            [realty.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "東京都千代田区1-1-1" (:address (store/listing s "listing-1"))))
      (is (= "JPN" (:jurisdiction (store/listing s "listing-1"))))
      (is (= ["party-1"] (:parties (store/listing s "listing-1"))))
      (is (= "山田 太郎" (:name (store/party s "party-1"))))
      (is (false? (:sanctions-hit? (store/party s "party-1"))))
      (is (true? (:sanctions-hit? (store/party s "party-2"))))
      (is (= ["listing-1" "listing-2"] (mapv :id (store/all-listings s))))
      (is (nil? (store/kyc-of s "party-1")))
      (is (nil? (store/disclosure-of s "listing-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/closing-history s)))
      (is (zero? (store/next-sequence s "JPN"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :listing/upsert
                                 :value {:id "listing-1" :status :ready}})
        (is (= :ready (:status (store/listing s "listing-1"))))
        (is (= "東京都千代田区1-1-1" (:address (store/listing s "listing-1"))) "address preserved"))
      (testing "disclosure / kyc payloads commit and read back"
        (store/commit-record! s {:effect :disclosure/set :path ["listing-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/disclosure-of s "listing-1")))
        (store/commit-record! s {:effect :kyc/set :path ["party-1"]
                                 :payload {:party-id "party-1" :verdict :clear}})
        (is (= {:party-id "party-1" :verdict :clear} (store/kyc-of s "party-1"))))
      (testing "closing drafts a closing record and advances the sequence"
        (store/commit-record! s {:effect :closing/mark-recorded :path ["listing-1"]})
        ;; closing-history holds the inner "record" sub-map (registry/append's
        ;; convention), whose closing-number key is "record_id".
        (is (= "JPN-00000000" (get (first (store/closing-history s)) "record_id")))
        (is (= "closing-draft" (get (first (store/closing-history s)) "kind")))
        (is (= :closed (:status (store/listing s "listing-1"))))
        (is (= 1 (count (store/closing-history s))))
        (is (= 1 (store/next-sequence s "JPN"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/listing s "nope")))
    (is (= [] (store/all-listings s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/closing-history s)))
    (is (zero? (store/next-sequence s "JPN")))
    (store/with-listings s {"x" {:id "x" :address "X" :jurisdiction "JPN"
                                 :parties [] :price 0 :currency "JPY"
                                 :listing-type :sale :status :intake}})
    (is (= "X" (:address (store/listing s "x"))))))
