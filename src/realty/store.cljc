(ns realty.store
  "SSoT for the realty actor, behind a `Store` protocol so the backend is a
  swap, not a rewrite -- the same seam `cloud-itonami-M6910` (`formation.store`)
  / `gftd-talent-actor` / `ai-gftd-itonami` use:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/realty/store_contract_test.clj), which is the whole point: the
  actor, the RealtorGovernor and the audit ledger never know which SSoT
  they run on.

  The ledger stays append-only on every backend: 'who closed what, for
  which property, on what jurisdictional basis, approved by whom' is
  always a query over an immutable log -- the audit trail a buyer/seller
  trusting an operator with their closing needs, and the evidence an
  operator needs if a closing is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [realty.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (listing [s id])
  (all-listings [s])
  (party [s id])
  (kyc-of [s party-id] "committed KYC screening verdict for a party, or nil")
  (disclosure-of [s listing-id] "committed jurisdiction disclosure/title assessment, or nil")
  (ledger [s])
  (closing-history [s] "the append-only closing-record history (realty.registry drafts)")
  (next-sequence [s jurisdiction] "next closing-number sequence for a jurisdiction")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-listings [s listings] "replace/seed the listing directory (map id->listing)")
  (with-parties [s parties] "replace/seed the party directory (map id->party)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained property set so the actor + tests run offline."
  []
  {:listings
   {"listing-1" {:id "listing-1" :address "東京都千代田区1-1-1" :jurisdiction "JPN"
                 :parties ["party-1"] :price 50000000 :currency "JPY"
                 :listing-type :sale :status :intake}
    "listing-2" {:id "listing-2" :address "unknown" :jurisdiction "ATL"
                 :parties ["party-2"] :price 100 :currency "USD"
                 :listing-type :sale :status :intake}}
   :parties
   {"party-1" {:id "party-1" :name "山田 太郎" :role :seller :sanctions-hit? false :id-doc "passport-jp-****5678"}
    "party-2" {:id "party-2" :name "J. Doe" :role :buyer :sanctions-hit? true :id-doc nil}}})

;; ----------------------------- shared closing logic -----------------------------

(defn- close!
  "Backend-agnostic `:closing/mark-recorded` -- looks up the listing + its
  parties via the protocol, drafts the closing record, and returns
  {:result .. :listing-patch ..} for the caller to persist. Pure w.r.t. any
  particular backend's transaction mechanics."
  [s listing-id]
  (let [l (listing s listing-id)
        seq-n (next-sequence s (:jurisdiction l))
        result (registry/register-closing
                (:address l) (mapv #(party s %) (:parties l))
                (:price l) (:jurisdiction l) seq-n)]
    {:result result
     :listing-patch {:status :closed
                      :closing-number (get result "closing_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (listing [_ id] (get-in @a [:listings id]))
  (all-listings [_] (sort-by :id (vals (:listings @a))))
  (party [_ id] (get-in @a [:parties id]))
  (kyc-of [_ id] (get-in @a [:kyc id]))
  (disclosure-of [_ listing-id] (get-in @a [:disclosures listing-id]))
  (ledger [_] (:ledger @a))
  (closing-history [_] (:closings @a))
  (next-sequence [_ jurisdiction]
    (get-in @a [:sequences jurisdiction] 0))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :listing/upsert
      (swap! a update-in [:listings (:id value)] merge value)

      :disclosure/set
      (swap! a assoc-in [:disclosures (first path)] payload)

      :kyc/set
      (swap! a assoc-in [:kyc (first path)] payload)

      :closing/mark-recorded
      (let [listing-id (first path)
            {:keys [result listing-patch]} (close! s listing-id)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences (:jurisdiction (get-in state [:listings listing-id]))] (fnil inc 0))
                       (update-in [:listings listing-id] merge listing-patch)
                       (update :closings registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-listings [s listings] (when (seq listings) (swap! a assoc :listings listings)) s)
  (with-parties [s parties] (when (seq parties) (swap! a assoc :parties parties)) s))

(defn seed-db
  "A MemStore seeded with the demo property set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :disclosures {} :kyc {} :ledger [] :sequences {} :closings []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (party id lists, KYC/disclosure payloads, ledger
  facts, closing records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention
  `formation.store` uses for its officer/assessment payloads."
  {:listing/id       {:db/unique :db.unique/identity}
   :party/id         {:db/unique :db.unique/identity}
   :kyc/party-id     {:db/unique :db.unique/identity}
   :disclosure/listing-id {:db/unique :db.unique/identity}
   :ledger/seq       {:db/unique :db.unique/identity}
   :closing/seq      {:db/unique :db.unique/identity}
   :sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- listing->tx [{:keys [id address jurisdiction parties price currency
                            listing-type status closing-number]}]
  (cond-> {:listing/id id}
    address         (assoc :listing/address address)
    jurisdiction     (assoc :listing/jurisdiction jurisdiction)
    parties          (assoc :listing/parties (enc parties))
    price            (assoc :listing/price price)
    currency         (assoc :listing/currency currency)
    listing-type     (assoc :listing/type listing-type)
    status           (assoc :listing/status status)
    closing-number   (assoc :listing/closing-number closing-number)))

(def ^:private listing-pull
  [:listing/id :listing/address :listing/jurisdiction :listing/parties :listing/price
   :listing/currency :listing/type :listing/status :listing/closing-number])

(defn- pull->listing [m]
  (when (:listing/id m)
    {:id (:listing/id m) :address (:listing/address m) :jurisdiction (:listing/jurisdiction m)
     :parties (or (dec* (:listing/parties m)) []) :price (:listing/price m)
     :currency (:listing/currency m) :listing-type (:listing/type m) :status (:listing/status m)
     :closing-number (:listing/closing-number m)}))

(defn- party->tx [{:keys [id name role sanctions-hit? id-doc]}]
  (cond-> {:party/id id}
    name (assoc :party/name name)
    role (assoc :party/role role)
    (some? sanctions-hit?) (assoc :party/sanctions-hit? sanctions-hit?)
    id-doc (assoc :party/id-doc id-doc)))

(defn- pull->party [m]
  (when (:party/id m)
    {:id (:party/id m) :name (:party/name m) :role (:party/role m)
     :sanctions-hit? (boolean (:party/sanctions-hit? m)) :id-doc (:party/id-doc m)}))

(defrecord DatomicStore [conn]
  Store
  (listing [_ id]
    (pull->listing (d/pull (d/db conn) listing-pull [:listing/id id])))
  (all-listings [_]
    (->> (d/q '[:find [?id ...] :where [?e :listing/id ?id]] (d/db conn))
         (map #(pull->listing (d/pull (d/db conn) listing-pull [:listing/id %])))
         (sort-by :id)))
  (party [_ id]
    (pull->party (d/pull (d/db conn)
                         [:party/id :party/name :party/role :party/sanctions-hit? :party/id-doc]
                         [:party/id id])))
  (kyc-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?pid
                :where [?k :kyc/party-id ?pid] [?k :kyc/payload ?p]]
              (d/db conn) id)))
  (disclosure-of [_ listing-id]
    (dec* (d/q '[:find ?p . :in $ ?lid
                :where [?a :disclosure/listing-id ?lid] [?a :disclosure/payload ?p]]
              (d/db conn) listing-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (closing-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :closing/seq ?s] [?e :closing/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :sequence/jurisdiction ?j] [?e :sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :listing/upsert
      (d/transact! conn [(listing->tx value)])

      :disclosure/set
      (d/transact! conn [{:disclosure/listing-id (first path) :disclosure/payload (enc payload)}])

      :kyc/set
      (d/transact! conn [{:kyc/party-id (first path) :kyc/payload (enc payload)}])

      :closing/mark-recorded
      (let [listing-id (first path)
            {:keys [result listing-patch]} (close! s listing-id)
            jurisdiction (:jurisdiction (listing s listing-id))
            next-n (inc (next-sequence s jurisdiction))]
        (d/transact! conn
                     [(listing->tx (assoc listing-patch :id listing-id))
                      {:sequence/jurisdiction jurisdiction :sequence/next next-n}
                      ;; store just the "record" sub-map, matching MemStore's
                      ;; `registry/append` convention -- closing-history is a
                      ;; history of RECORDS, not of the full closing result.
                      {:closing/seq (count (closing-history s)) :closing/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-listings [s listings]
    (when (seq listings) (d/transact! conn (mapv listing->tx (vals listings)))) s)
  (with-parties [s parties]
    (when (seq parties) (d/transact! conn (mapv party->tx (vals parties)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:listings .. :parties ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [listings parties]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (-> s (with-listings listings) (with-parties parties)))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo property set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
