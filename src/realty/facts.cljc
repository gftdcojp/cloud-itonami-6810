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
   "AUS-NSW" {:name "Australia -- New South Wales (exemplar; federalism note below)"
              :owner-authority "NSW Land Registry Services"
              :legal-basis "Real Property Act 1900 (NSW) -- Torrens title"
              :national-spec "NSW electronic lodgment network (PEXA)"
              :provenance "https://www.legislation.nsw.gov.au/"
              :notes "Australia's Torrens-title land registries are state/territory-based; NSW is an exemplar, not a national authority."
              :required-docs ["Contract for Sale of Land"
                             "Transfer form (NSW Form 01T)"
                             "Land tax clearance certificate"
                             "Verification of Identity (VOI) evidence"]}})

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
      :note (str "cloud-itonami-L6810 R0: " (count catalog)
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
