# cloud-itonami-6810

Open Business Blueprint for **ISIC Rev.5 6810**: real-estate agency
activities (community property — listings, lettings, lease management).

This repository designs a forkable OSS business for community real-estate:
parcel and listing management, lease scheduling with conflict detection,
and tenancy records — run by a qualified operator so a local agency keeps
its own property and tenancy data instead of renting a closed CRM SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a showing, cleaning and inspection robot performs the physical property work under an actor that proposes
actions and an independent **Property Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions require
human sign-off.

## Core Contract

```text
intake + identity + parcel + listing
        |
        v
Property Advisor -> Property Governor -> list, lease, or human approval
        |
        v
listing + lease (conflict-checked) + tenancy record + audit ledger
```

No automated advice can publish a listing without an identified parcel,
overlap an existing lease term, or release a tenancy record without governor
approval and audit evidence.

A live sample of the operator console is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of the kotoba-lang capability UI.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `6810`). Implemented by:

- [`kotoba-lang/property`](https://github.com/kotoba-lang/property) — parcel, listing, lease, term-overlap

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
