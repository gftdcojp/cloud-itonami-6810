# ADR-0001: cloud-itonami-L6810 -- Realtor-LLM as a contained intelligence node

- Status: Accepted (2026-07-03)
- Related: `cloud-itonami-M6910` ADR-0001 (Registrar-LLM ⊣ RegistrarGovernor,
  the pattern this ADR ports), `cloud-itonami-6310` (HR-LLM ⊣
  PolicyGovernor), `ai-gftd-itonami` (ops-LLM ⊣ CertGovernor), `robotaxi-actor`
  ADR-0001 (sealing an unsafe research model behind an independent
  governor), langgraph-clj ADR-0001 (Pregel superstep + interrupt +
  Datomic checkpoint)
- Context: `cloud-itonami-6810` published a business/operator-model
  blueprint (ADR-2607011000) but stopped at `:blueprint` maturity -- no
  governed actor implementation. This ADR deepens it to `:implemented`,
  matching the two other actors in this family.

## Problem

Real-estate closing execution needs three different kinds of judgment:

1. **Jurisdiction disclosure/title correctness** -- are the required
   disclosure and title documents based on an official land-registry
   source?
2. **KYC/sanctions screening** -- does any buyer, seller or tenant party
   match a sanctions/PEP list? (Real estate is a well-documented
   money-laundering vector.)
3. **Real actuation** -- actually recording a title transfer and actually
   disbursing escrow funds: an irreversible real-world act.

An LLM has no authority or grounding for any of these. The design problem
is therefore not "run closings with an LLM" but "seal the LLM inside a
trust boundary and layer disclosure-authenticity, KYC/sanctions, audit
and human-approval on top of it, while structurally fixing real actuation
as human-only."

## Decision

### 1. Realtor-LLM is sealed into the bottom node; it never records or disburses directly

`realty.realtorllm` returns exactly four kinds of proposal: intake
normalization, jurisdiction disclosure/title checklist, KYC/sanctions
screening, and closing proposal. No proposal writes the SSoT or touches a
real land registry / escrow account directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 closing operation

`realty.operation/build` is the same StateGraph shape as
`cloud-itonami-M6910` / `cloud-itonami-6310` / `ai-gftd-itonami`
(intake → advise → govern → decide → commit | hold | request-approval).
One graph run corresponds to one closing operation, with no unbounded
inner loop.

### 3. RealtorGovernor is a separate system from Realtor-LLM

`realty.governor` has five checks: spec-basis · sanctions-hit ·
document-complete (HARD, un-overridable) + confidence-floor ·
actuation-gate (SOFT, human decides).

### 4. Real actuation is structurally always human-only (enforced by two independent layers)

`realty.governor`'s actuation gate (`:stake :actuation` always escalates)
and `realty.phase`'s phase table (`:closing/submit` is never a member of
any phase's `:auto` set) both prevent a real title-transfer recording or
escrow-fund disbursement from ever auto-committing. Neither depends on
the other being implemented correctly.

### 5. No fabricated international closing-record standard

Unlike `cloud-itonami-M6910`'s `formation.registry` (a real port of ISO
17442 LEI + ISO 7064 MOD 97-10 from `matsurigoto`'s corp-registry), there
is no single international check-digit standard for a property closing/
recording number -- every land registry assigns its own reference format.
`realty.registry` therefore does not invent one; it validates required
fields and assigns a jurisdiction-scoped sequence number only, keeping the
same "never fabricate a spec" discipline `realty.facts` uses for
jurisdiction requirements.

## Consequences

- (+) Real-estate closing execution gets the same governed,
  auditable-actor treatment as company incorporation (`cloud-itonami-M6910`)
  and HR (`cloud-itonami-6310`), without centralizing liability in one
  vendor -- any licensed operator can fork and run their own instance.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/realty/phase_test.clj`'s
  `closing-submit-never-auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by
  `test/realty/store_contract_test.clj`, the same `:db-api`-driven swap
  pattern `formation.store` / `talent.store` / `itonami.store` use.
- (-) This R0 seeds only 5 jurisdictions (JPN, USA-CA, GBR, DEU, AUS-NSW)
  with an official spec-basis, out of ~194 worldwide; `realty.facts/coverage`
  reports this honestly rather than claiming broader coverage.
- (-) Real land-registry portal integration, real escrow/payment
  integration, and real KYC/sanctions-screening provider integration are
  out of scope for this OSS actor -- each operator's responsibility.
- 24 tests / 103 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Keep `cloud-itonami-6810` at `:blueprint` only | ❌ | Leaves real estate without a single `:implemented` reference actor, unlike insurance/HR/legal peers once this ADR lands |
| Fork `formation.registry`'s LEI/MOD-97-10 math wholesale for closings | ❌ | LEI is a real, applicable international standard for legal entities; there is no equivalent for property closing numbers, so reusing that math here would be a fabricated conformance anchor |
| Model closings inside `kotoba-lang/property` itself (no separate actor) | ❌ | `kotoba-lang/property` is a capability lib (pure contracts, no governor/human-approval workflow); mixing the governed-actuation actor into it would blur the capability-layer/business-blueprint boundary this workspace otherwise keeps clean |
