(ns realty.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean listing through
  intake -> jurisdiction disclosure assessment -> KYC screening -> closing
  proposal (always escalates) -> human approval -> commit, then shows a
  HARD hold (a sanctions hit) that never reaches a human at all, and
  prints the audit ledger + the draft closing record."
  (:require [langgraph.graph :as g]
            [realty.store :as store]
            [realty.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :realtor :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== intake listing-1 (JPN, clean party) ==")
    (println (exec! actor "t1" {:op :listing/intake :subject "listing-1"
                                :patch {:id "listing-1" :status :ready}} operator))

    (println "== jurisdiction/assess listing-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "listing-1"} operator))
    (println (approve! actor "t2"))

    (println "== kyc/screen party-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :kyc/screen :subject "party-1"} operator))
    (println (approve! actor "t3"))

    (println "== closing/submit listing-1 (always escalates -- actuation) ==")
    (let [r (exec! actor "t4" {:op :closing/submit :subject "listing-1"} operator)]
      (println r)
      (println "-- human operator approves --")
      (println (approve! actor "t4")))

    (println "== kyc/screen party-2 (sanctions hit -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t5" {:op :kyc/screen :subject "party-2"} operator))

    (println "== jurisdiction/assess listing-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "listing-2" :no-spec? true} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft closing records ==")
    (doseq [r (store/closing-history db)] (println r))))
