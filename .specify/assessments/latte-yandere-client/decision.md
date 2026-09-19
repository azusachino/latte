# Decision: Latte yande.re client

- **Slug**: `latte-yandere-client`
- **Decided**: 2026-09-20
- **Verdict**: go
- **Artifacts reviewed**: `intake.md`, `research.md`, `problem.md`, `concept.md`

## Scorecard

| Criterion | Rating | Justification |
| --- | --- | --- |
| Problem validity | adequate | The owner has direct unmet need and two existing alternatives that have not produced the desired experience; broader demand is unproven and unnecessary for a personal tool. |
| Evidence strength | adequate | The domain, protocol, prior implementations, current endpoints, and host tooling have primary evidence; real usage and Dart-on-device behavior remain unproven. |
| Value vs. inaction | adequate | A bounded daily-use client serves the owner and Dart-learning goal; doing nothing avoids cost but preserves the current dissatisfaction. |
| Feasibility / appetite | adequate | The browse/search/detail/save slice fits a medium appetite if transport and Android toolchain gates pass before feature work. |
| Strategic fit | strong | The concept matches the requested Dart rewrite, TDD practice, smallest-slice principle, and future site boundary without demanding parity. |
| Risk posture | adequate | External API drift and rewrite duplication are explicit; fixture tests, live receipts, a transport spike, and strict deferred scope mitigate them. |

## Verdict & Rationale

Proceed to specification with the Android-first vertical slice. The problem and
evidence clear the `adequate` threshold for a personal product, and Option B is
credible within a medium appetite. This is not approval for the full historical
Moebooru or Dreamland surface. The specification must make the two feasibility
gates—the Dart transport probe and Android toolchain smoke—blocking acceptance
conditions before feature implementation expands.

## If go — Handoff to `$speckit-specify`

- **Problem**: The owner lacks a maintained, focused way to browse, search,
  inspect, and save yande.re art; earlier clients are aging or too broad.
- **Chosen approach**: An Android-first Flutter client with one yande.re product
  adapter and only the site seams exercised by the first journey.
- **In scope**: anonymous browse, opaque tag search, detail/media selection,
  local save, explicit loading/empty/offline/malformed/throttled/existing-file
  outcomes, and a test-only adapter substitution check.
- **Out of scope**: accounts, favorites, pools, uploads, persistence, queues,
  background work, second production site, and non-Android release claims.
- **Success metrics**: owner completes the four-stage journey within two
  minutes; every failure class has deterministic acceptance coverage; no
  deferred subsystem appears in production code; the site boundary accepts a
  test adapter without presentation changes.
- **Carried-forward open questions**: exact default content policy and whether
  the first save experience uses one library directory or per-save selection.
