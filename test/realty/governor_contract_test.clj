(ns realty.governor-contract-test
  "The governor contract as executable tests -- the real-estate analog of
  `cloud-itonami-M6910`'s `formation.governor-contract-test` /
  robotaxi's safety_contract_test / gftd-talent-actor's
  policy_contract_test. The single invariant under test:

    Realtor-LLM never closes/disburses a record the RealtorGovernor would
    reject, `:closing/submit` NEVER auto-commits at any phase, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [realty.store :as store]
            [realty.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :realtor :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :listing/intake :subject "listing-1"
                   :patch {:id "listing-1" :status :ready}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= :ready (:status (store/listing db "listing-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "listing-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (g/run* actor {:approval {:status :approved :by "op-1"}}
                       {:thread-id "t2" :resume? true})]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/disclosure-of db "listing-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "listing-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/disclosure-of db "listing-1")) "no disclosure written"))))

(deftest sanctions-hit-is-held-and-unoverridable
  (testing "a sanctions/PEP hit on a party -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :kyc/screen :subject "party-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:sanctions-hit} (-> (store/ledger db) first :basis)))
      (is (nil? (store/kyc-of db "party-2")) "no KYC clearance written"))))

(deftest closing-without-disclosure-is-held
  (testing "closing/submit before any jurisdiction disclosure assessment -> HOLD (incomplete documents)"
    (let [[db actor] (fresh)
          res (exec-op actor "t5" {:op :closing/submit :subject "listing-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:incomplete-documents} (-> (store/ledger db) first :basis))))))

(deftest closing-submit-always-escalates-then-human-decides
  (testing "a clean, fully-disclosed closing still ALWAYS interrupts for human approval -- actuation is never auto"
    (let [[db actor] (fresh)
          _ (exec-op actor "t6a" {:op :jurisdiction/assess :subject "listing-1"} operator)
          _ (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id "t6a" :resume? true})
          _ (exec-op actor "t6b" {:op :kyc/screen :subject "party-1"} operator)
          _ (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id "t6b" :resume? true})
          r1 (exec-op actor "t6" {:op :closing/submit :subject "listing-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, closing record drafted"
        (let [r2 (g/run* actor {:approval {:status :approved :by "op-1"}}
                         {:thread-id "t6" :resume? true})]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (= :closed (:status (store/listing db "listing-1"))))
          (is (= 1 (count (store/closing-history db))) "one draft closing record")))))
  (testing "reject -> hold, nothing closed"
    (let [[db actor] (fresh)
          _ (exec-op actor "t7a" {:op :jurisdiction/assess :subject "listing-1"} operator)
          _ (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id "t7a" :resume? true})
          _  (exec-op actor "t7" {:op :closing/submit :subject "listing-1"} operator)
          r2 (g/run* actor {:approval {:status :rejected :by "op-1"}}
                     {:thread-id "t7" :resume? true})]
      (is (= :hold (get-in r2 [:state :disposition])))
      (is (empty? (store/closing-history db)) "nothing drafted on reject"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :listing/intake :subject "listing-1"
                       :patch {:id "listing-1" :status :ready}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "listing-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
