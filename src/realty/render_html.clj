(ns realty.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger seq 6): the previous `docs/samples/
  operator-console.html` in this repo was a hand-typed static placeholder
  (fabricated parcel \"P1\" / listing \"L1\" / lease \"LS1\" rows, and it
  referenced a `kotoba.property.ui` namespace that does not exist anywhere
  in this repo's `src/`). This namespace replaces it with a real
  generator that drives the REAL actor stack (`realty.operation` ->
  `realty.governor` -> `realty.store`) through a scenario built from this
  repo's own `realty.store/demo-data` (confirmed correct by first running
  `realty.sim`, `clojure -M:dev:run` -- unlike `cloud-itonami-isic-851`'s
  own `schoolops.sim`, THIS repo's sim driver's ids do match `demo-data`,
  so it was safe to use as a starting reference) and renders the result
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed (verified
  by diffing two consecutive runs before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [realty.store :as store]
            [realty.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :realtor :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach:

    listing-1 (JPN, clean seller party-1) -- full clean lifecycle:
    intake status-refresh (auto-commit at phase 3, no capital risk) ->
    jurisdiction disclosure assessment (ALWAYS escalates per
    `realty.phase` -- approved) -> KYC screen on the seller (ALWAYS
    escalates -- approved, clears) -> closing/submit (ALWAYS escalates --
    the `:actuation` high-stakes gate, structural at every phase --
    approved, drafts the closing record).

    listing-3 (new intake, JPN, party-9) -- intake (auto-commit) ->
    jurisdiction/assess in JPN (approved, commits a JPN disclosure
    checklist) -> a later intake corrects the listing's jurisdiction to
    GBR without re-running the assessment (auto-commit, a realistic
    relist/data-correction case) -> closing/submit now HARD-holds:
    the committed disclosure's checklist is JPN's, the listing's
    jurisdiction is now GBR, so `realty.facts/required-docs-satisfied?`
    fails -- :incomplete-documents, never reaches a human.

    party-2 (already on file with `:sanctions-hit? true`) -- kyc/screen
    HARD-holds :sanctions-hit, never reaches a human (un-overridable per
    `realty.governor`).

    listing-2 (ATL -- a jurisdiction with no entry in `realty.facts/
    catalog`) -- jurisdiction/assess HARD-holds :no-spec-basis, never
    reaches a human (the advisor must not invent a jurisdiction's law).

  Every op keyword used below (:listing/intake :jurisdiction/assess
  :kyc/screen :closing/submit) is a real member of `realty.phase/
  write-ops`. Returns the resulting store -- every field `render` below
  reads is real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "l1-intake" {:op :listing/intake :subject "listing-1"
                               :patch {:id "listing-1" :status :ready}})

    (exec! actor "l1-assess" {:op :jurisdiction/assess :subject "listing-1"})
    (approve! actor "l1-assess")

    (exec! actor "l1-kyc" {:op :kyc/screen :subject "party-1"})
    (approve! actor "l1-kyc")

    (exec! actor "l1-closing" {:op :closing/submit :subject "listing-1"})
    (approve! actor "l1-closing")

    (exec! actor "l3-intake" {:op :listing/intake :subject "listing-3"
                               :patch {:id "listing-3" :address "神奈川県横浜市西区2-2-2"
                                       :jurisdiction "JPN" :parties ["party-9"]
                                       :price 38000000 :currency "JPY"
                                       :listing-type :sale :status :intake}})

    (exec! actor "l3-assess" {:op :jurisdiction/assess :subject "listing-3"})
    (approve! actor "l3-assess")

    (exec! actor "l3-rejurisdict" {:op :listing/intake :subject "listing-3"
                                    :patch {:id "listing-3" :jurisdiction "GBR"}})

    (exec! actor "l3-closing" {:op :closing/submit :subject "listing-3"})

    (exec! actor "p2-kyc" {:op :kyc/screen :subject "party-2"})

    (exec! actor "l2-assess" {:op :jurisdiction/assess :subject "listing-2"})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- fmt-price [{:keys [price currency]}]
  (if price
    (str (esc price) " " (esc currency))
    "&mdash;"))

(defn- listing-row [ledger {:keys [id address jurisdiction status] :as l}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td class=\"amt\">%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc address) (esc jurisdiction) (fmt-price l)
          (esc (name (or status :n-a))) (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`realty.phase`/`realty.governor`, README `Actuation`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:listing/intake</code></td><td><span class=\"ok\">auto-commit when clean, no capital risk (phase 3)</span></td></tr>"
   "        <tr><td><code>:jurisdiction/assess</code></td><td><span class=\"warn\">ALWAYS human approval, even when clean</span></td></tr>"
   "        <tr><td><code>:kyc/screen</code></td><td><span class=\"warn\">ALWAYS human approval &middot; sanctions/PEP hit is un-overridable HOLD</span></td></tr>"
   "        <tr><td><code>:closing/submit</code></td><td><span class=\"warn\">ALWAYS human approval &middot; actuation gate, never auto at any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        listings (->> (store/all-listings db)
                      (filter #(#{"listing-1" "listing-2" "listing-3"} (:id %)))
                      (sort-by :id))
        listing-rows (str/join "\n" (map (partial listing-row ledger) listings))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-6810 &middot; real-estate closing/title-transfer</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "td.amt { font-variant-numeric: tabular-nums; text-align: right; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Real-estate closing / title-transfer (ISIC 6810) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · closing/title-transfer actuation always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Listings</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>realty.store</code> via <code>realty.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Listing</th><th>Address</th><th>Jurisdiction</th><th>Price</th><th>Status</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     listing-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (RealtorGovernor)</h2>\n"
     "    <p class=\"muted\">HARD holds (fabricated jurisdiction law, sanctions/PEP hit, incomplete disclosure documents) cannot be overridden by a human approver. A real title-transfer recording or escrow-fund disbursement is never autonomous, at any phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/closing-history db)) "draft closing records )")))
