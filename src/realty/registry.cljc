(ns realty.registry
  "Pure-function closing-record construction -- an append-only property
  title-transfer / closing draft.

  Unlike `cloud-itonami-M6910`'s `formation.registry` (which ports a real
  international standard, ISO 17442 LEI, for legal-entity identifiers),
  there is no single international check-digit standard for a property
  closing/recording number -- every jurisdiction's land registry assigns
  its own reference format. This namespace therefore does NOT invent one;
  it builds a jurisdiction-scoped sequence number (the same honest,
  non-fabricating discipline `formation.registry` and `formation.facts`
  use) and validates the record's required fields.

  This namespace is pure data + pure functions -- no I/O, no network call
  to any land registry. It builds the RECORD an operator would keep, not
  the act of recording itself (that is `realty.operation`'s
  `:closing/submit`, which is always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  land registry's act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-closing
  "Validate + construct a property closing/title-transfer registration
  DRAFT. Pure function -- does not touch any real land registry or move
  any real escrow funds."
  [address parties price jurisdiction sequence]
  (when-not (and address (not= address ""))
    (throw (ex-info "closing: address required" {})))
  (when-not (seq parties)
    (throw (ex-info "closing: at least one party required" {})))
  (when (< price 0)
    (throw (ex-info "closing: price must be >= 0" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "closing: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "closing: sequence must be >= 0" {})))
  (let [closing-number (str (str/upper-case jurisdiction) "-" (zero-pad sequence 8))
        record {"record_id" closing-number
                "kind" "closing-draft"
                "address" address
                "parties" (vec parties)
                "price" price
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "closing_number" closing-number
     "certificate" (unsigned-certificate "TransferCertificate" closing-number closing-number)}))

(defn register-amendment
  "Append-only amendment draft (e.g. a post-closing correction to the
  recorded address or party list). Never overwrites the closing record."
  [closing-number changed-fields effective-date]
  (when-not (and closing-number (not= closing-number ""))
    (throw (ex-info "amendment: closing_number required" {})))
  (when-not (seq changed-fields)
    (throw (ex-info "amendment: changed_fields required" {})))
  {"record" {"record_id" (str closing-number "#chg@" effective-date)
             "kind" "amendment-draft"
             "closing_number" closing-number
             "changed" (into {} changed-fields)
             "effective_date" effective-date
             "immutable" true}})

(defn append
  "Append a closing record, returning a NEW list (never mutate history in place)."
  [history result]
  (conj (vec history) (get result "record")))
