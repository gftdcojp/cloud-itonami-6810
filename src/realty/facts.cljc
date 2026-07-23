(ns realty.facts
  "Per-jurisdiction property-transfer disclosure/title requirement catalog
  -- the G2-style spec-basis table the RealtorGovernor checks every
  jurisdiction/assess proposal against ('did the advisor cite an OFFICIAL
  public source for this jurisdiction's requirements, or did it invent
  one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  `cloud-itonami-M6910`'s `formation.facts` uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official land-registry /
  real-estate authority (see `:provenance`); they are a STARTING catalog,
  not a from-scratch survey of all ~194 jurisdictions. Extending coverage
  is additive: add one map to `catalog`, cite a real source, done -- never
  invent a jurisdiction's requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-docs` mirrors the generic closing
  checklist every land registry asks for in some form; `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any :jurisdiction/assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "法務局 (Legal Affairs Bureau)"
          :legal-basis "不動産登記法 (Real Property Registration Act)"
          :national-spec "登記・供託オンライン申請システム"
          :provenance "https://www.moj.go.jp/MINJI/"
          :required-docs ["登記識別情報または登記済証 (title certificate)"
                          "売買契約書 (purchase agreement)"
                          "固定資産評価証明書 (fixed-asset valuation certificate)"
                          "本人確認書類"
                          "住民票"]}
   "USA-CA" {:name "United States -- California (exemplar; federalism note below)"
             :owner-authority "County Recorder's Office"
             :legal-basis "California Civil Code §1102 et seq. (Transfer Disclosure Statement)"
             :national-spec "County e-recording"
             :provenance "https://www.dre.ca.gov/"
             :notes "No federal land registry -- real-property recording is per-county; California is an exemplar, not a national authority."
             :required-docs ["Grant Deed"
                             "Transfer Disclosure Statement (TDS)"
                             "Preliminary Title Report"
                             "Natural Hazard Disclosure Statement"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "HM Land Registry"
          :legal-basis "Land Registration Act 2002"
          :national-spec "HM Land Registry Business e-services"
          :provenance "https://www.gov.uk/government/organisations/land-registry"
          :required-docs ["TR1 transfer form"
                          "ID1/ID2 identity verification"
                          "Property information form (TA6)"
                          "Fittings and contents form (TA10)"]}
   "DEU" {:name "Germany"
          :owner-authority "Grundbuchamt (Land Registry, local court)"
          :legal-basis "§311b BGB (notarisation requirement) + Grundbuchordnung"
          :national-spec "Grundbuch (via notary submission)"
          :provenance "https://www.gesetze-im-internet.de/bgb/__311b.html"
          :required-docs ["Notariell beurkundeter Kaufvertrag (notarised purchase agreement)"
                          "Grundbuchauszug (land register extract)"
                          "Auflassungsvormerkung (priority notice of conveyance)"
                          "Identification of parties"]}
   "NLD" {:name "Netherlands"
          :owner-authority "Kadaster (Basisregistratie Kadaster / openbare registers)"
          :legal-basis "Burgerlijk Wetboek Boek 3 art. 89 (notarial deed and registration); Burgerlijk Wetboek Boek 7 art. 2 (written residential sale and consumer cooling-off period); Wwft"
          :national-spec "Akte van levering, submitted by a Dutch civil-law notary to Kadaster"
          :provenance "https://www.kadaster.nl/situaties/regelen-via-de-notaris/akte-inschrijven-bij-koop-van-een-huis"
          :official-sources
          {:transfer-law "https://wetten.overheid.nl/BWBR0005291/"
           :residential-sale-law "https://wetten.overheid.nl/BWBR0005290/"
           :registration-process "https://www.kadaster.nl/situaties/regelen-via-de-notaris/akte-inschrijven-bij-koop-van-een-huis"
           :energy-label "https://www.rvo.nl/onderwerpen/wetten-en-regels-gebouwen/energielabel-woningen"
           :aml-notary "https://www.knb.nl/ons-beroep/toezicht-tuchtrecht/wet-en-regelgeving/wwft"}
          :actuation-authority "A Netherlands civil-law notary (notaris); Kadaster registration completes legal transfer"
          :human-gates #{:notary-approved :deed-signed :kadaster-registered :funds-released-by-notary}
          :notes "The broker is optional. The actor may prepare and verify a closing package, but may not execute the notarial deed, submit it to Kadaster, or release funds. Conditional property evidence is satisfied by the applicable document or an explicit, human-verified not-applicable declaration."
          :required-docs
          ["Kadaster title/parcel extract and seller ownership verification"
           "Kadaster mortgage, attachment and limited-rights search"
           "Signed written purchase agreement (koopovereenkomst), with cooling-off receipt/timeline where BW 7:2 applies"
           "Notary-approved draft deed of transfer (concept akte van levering)"
           "Notary client/party identity, authority, UBO and Wwft review evidence"
           "Mortgage redemption/discharge statement, or human-verified not-applicable declaration"
           "Valid EP-online energy-performance label and advertisement/hand-over evidence, or documented statutory exemption"
           "VvE division deed, regulations, financial/reserve, minutes and arrears package, or human-verified not-applicable declaration"
           "Erfpacht/opstal/servitude terms and current canon evidence, or human-verified not-applicable declaration"
           "Occupancy and tenancy status, lease documents and tenant-impact review, or human-verified vacant-possession declaration"
           "Municipal permit, destination/environment-plan and known-defect disclosure package"
           "Notary closing statement and funds-flow instructions"]}
   "AUS-NSW" {:name "Australia -- New South Wales (exemplar; federalism note below)"
              :owner-authority "NSW Land Registry Services"
              :legal-basis "Real Property Act 1900 (NSW) -- Torrens title"
              :national-spec "NSW electronic lodgment network (PEXA)"
              :provenance "https://www.legislation.nsw.gov.au/"
              :notes "Australia's Torrens-title land registries are state/territory-based; NSW is an exemplar, not a national authority."
              :required-docs ["Contract for Sale of Land"
                             "Transfer form (NSW Form 01T)"
                             "Land tax clearance certificate"
                             "Verification of Identity (VOI) evidence"]}
   "CAN-ON" {:name "Canada -- Ontario (exemplar; federalism note below)"
             :owner-authority "Real Estate Council of Ontario (RECO)"
             :legal-basis "Trust in Real Estate Services Act, 2002 (TRESA); O. Reg. 567/05 (General)"
             :national-spec "RECO MyWeb registration portal (salesperson/broker/brokerage registration and renewal)"
             :provenance "https://www.reco.on.ca/agents-and-brokerages/becoming-a-real-estate-agent"
             :official-sources
             {:registration-requirements "https://www.reco.on.ca/agents-and-brokerages/becoming-a-real-estate-agent"
              :tresa-legislation-index "https://www.reco.on.ca/agents-and-brokerages/tresa-explained"
              :trust-account-bulletin "https://www.reco.on.ca/agents-and-brokerages/reco-bulletins/reco-bulletin-8-1-unclaimed-money-in-the-real-estate-trust-account"
              :ontario-e-laws-statute-page "https://www.ontario.ca/laws/statute/02r30"}
             :notes "Canada has no federal real-estate regulator -- registration/licensing of agents and brokerages, and real-estate trust-account handling, is regulated per-province; Ontario (RECO) is a provincial exemplar, not a 'Canada' national authority. RECO's own site cites the Act's short title as 'Trust in Real Estate Services Act, 2002 (TRESA)' consistently across multiple pages (see :quotes below, verbatim, not a transcription error introduced here), and RECO's tresa-explained page states the Act 'and its associated regulations' include O. Reg. 567/05 (General), O. Reg. 579/05, O. Reg. 365/22 (Code of Ethics), O. Reg. 536/20 (Personal Real Estate Corporations) and O. Reg. 367/22 (Discipline Committee), linking the Act itself to ontario.ca's e-laws identifier '02r30'. That RECO page also states 'key changes ... took effect December 1, 2023.' ontario.ca's own e-laws pages (both /laws/statute/02r30 and /laws/statute/20t01) require client-side JavaScript to render body text and could not be directly text-extracted this session, so no specific chapter/schedule citation (e.g. an 'S.O. YYYY, c. NN' cite, or the prior Act's pre-TRESA name) is asserted here -- only what RECO's own fetched page text and links state. RECO is the delegated administering authority for this Act, cited the same way the existing USA-CA entry above cites its state regulator (dre.ca.gov) rather than raw statute text."
             :quotes
             {:registration-required "Real estate is a regulated profession in Ontario, so all real estate agents must be registered with the Real Estate Council of Ontario (RECO). RECO regulates the real estate brokerages and agents who trade in real estate in Ontario."
              :trust-account-unclaimed-money "All unclaimed money held in trust for more than two years must be paid to RECO."}
             :required-docs ["RECO registration certificate (salesperson or broker), current and in good standing"
                             "Confirmation registrant is employed by a registered real estate brokerage (no independent trading permitted)"
                             "Criminal Record and Judicial Matters Check (CRJMC), where required under RECO registration policy"
                             "Real estate trust account transaction record for any deposit or other money received in trust"
                             "Agreement of purchase and sale and trade record sheet supporting any trust-account entry"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to close on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-6810 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `realty.facts/catalog`, never "
                 "fabricate a jurisdiction's requirements.")})))

(defn required-docs-satisfied?
  "Does `submitted` (a set/coll of doc keywords or strings) satisfy every
  required doc listed for `iso3`? Missing spec-basis -> never satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-docs]} (spec-basis iso3)]
    (let [need (count required-docs)
          have (count (filter (set submitted) required-docs))]
      (= need have))))

(defn doc-checklist [iso3]
  (:required-docs (spec-basis iso3) []))

(defn actuation-authority [iso3]
  (:actuation-authority (spec-basis iso3)))

(defn human-gates [iso3]
  (:human-gates (spec-basis iso3) #{}))
