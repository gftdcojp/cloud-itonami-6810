# Business Model: Community Real-Estate Agency

## Classification
- Repository: `cloud-itonami-6810`
- ISIC Rev.5: `6810` — real-estate agency activities
- Social impact: housing access, tenancy protection, transparent fees

## Customer
- independent property agencies
- housing cooperatives and community land trusts
- student and affordable-housing operators
- agencies leaving closed CRM SaaS

## Offer
- parcel and listing management
- sale and rental listings
- lease scheduling with term-overlap detection
- tenancy and rent records
- maintenance and handover workflows
- role-based access and immutable audit ledger
- governed closing execution: listing intake, per-jurisdiction disclosure/
  title checklisting, buyer/seller/tenant KYC-sanctions screening, and a
  human-approved closing (title-transfer recording + escrow disbursement)
  handoff (the Realtor-LLM ⊣ RealtorGovernor actor -- see README)

## Revenue
- self-host setup fee
- managed hosting subscription per agency
- support retainer with SLA
- listing and settlement integration
- per-closing execution fee (intake through closing handoff)
- jurisdiction-pack licensing: a maintained, spec-cited disclosure/title
  requirement catalog for a specific country, kept current

## Trust Controls
- listings require an identified parcel
- overlapping lease terms can never be committed
- tenancy disclosures require governor approval
- every listing, lease and handover path is auditable
- tenant personal data stays outside Git
- no closing is recorded and no escrow fund is disbursed without human
  sign-off (the RealtorGovernor's actuation gate -- never bypassable)
- a fabricated jurisdiction disclosure requirement or a sanctions/PEP hit
  forces an un-overridable hold
