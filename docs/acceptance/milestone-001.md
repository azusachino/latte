# Latte 0.0.1 review handoff

> **Historical handoff**: This records the superseded Flutter/Dart 0.0.1
> release review. It is not evidence for the current native Kotlin/Compose
> implementation or the active 0.0.2 milestone.

**Snapshot**: 2026-09-20

**Review target**: the complete 0.0.1 implementation on
`docs/dart-rewrite-plan`, delivered through [PR #1](https://github.com/azusachino/latte/pull/1).

## Product contract to review

Latte is a Material 3 rework of the pinned Yande-compatible reference app.
Material 3 supplies visual tokens, accessibility, and platform components; it
does not replace the reference UX or dataflow.

- Explore keeps Popular/Newest, period selection, dense staggered thumbnails,
  settings, and column density.
- Search is an opaque tag expression. Detail tags are clickable and launch a
  tag-search intent.
- Explore cards show thumbnails only; rating UI is removed. Aggregate score is
  detail metadata when available. Authenticated personal scoring is roadmap
  work.
- Detail is image-first: thumbnail first, higher-quality replacement, pager
  swipe, previous/next fallback, Android back, two-finger zoom, metadata table,
  and a peekable action sheet.
- Download defaults to the best available quality, runs in WorkManager, resumes
  through pending MediaStore rows, handles duplicates, and reports progress and
  completion through separate native notifications. Completion includes a
  thumbnail and open/view action.
- The next three loaded detail images are prefetched using display-quality
  variants only.

## Evidence already available

| Evidence | Result |
| --- | --- |
| `make check` | PASS locally: format, analyze, 70 Flutter tests, and bounded probe test |
| `make validate` | Alias of `make check`; should produce the same result |
| CI on PR head `54004bb` | PASS: repository checks and Android debug build, run `35502524490` |
| Android launch | PASS on `0cadf428`, OnePlus 8 / Android 16 / API 36 |
| Live Yande browse/download | Not accepted: the connected device could not resolve `yande.re` |

The old failed CI runs are historical. The latest green run is the relevant
repository/build evidence.

## Review order

1. Read `specs/001-yandere-core-journey/contracts/product-experience.md` and
   `docs/spec-kit-evaluation.md` to establish the compatibility boundary.
2. Compare Explore and Detail composition with
   `refs/image-gallery-apps/moebooru`, especially pager/back behavior, the
   bottom action sheet, download feedback, and notification completion.
3. Inspect the shared seams in `lib/src/sites/`, the Explore controller/UI,
   settings, and the WorkManager/MediaStore implementation under `android/`.
4. Run `make validate`, then inspect the connected-device behavior when live
   Yande DNS/network access is available.
5. Reconcile the remaining task status before release; do not mark an item
   complete from unit tests alone when the task requires device or live proof.

## Explicit open items

- T044/T048: owner/device visual and gesture comparison against the pinned
  reference, including restart-resume, notification stacking/open behavior,
  and launcher rendering.
- T023-T032 and T034: historical instrumentation, fixture-backed 20-run,
  live acceptance, and owner launch acceptance evidence remain unrecorded.
- Confirm the current owner-provided PNG launcher asset remains the accepted
  icon; the rejected SVG/vector redraw is not required.

## Spec Kit disposition

Keep the assessment, constitution, active specification, plan, task graph,
contracts, and convergence record for the next review. Keep `.specify/` until
that review decides whether its generated workflow is worth the maintenance
cost. `docs/PROJECT-SPEC.md` is historical and superseded, not a second active
specification.

The provisional 0.0.1 decision is **keep Spec Kit with a smaller maintenance
scope**: it found eight material pre-code defects and made the seeded
inconsistency actionable, but maintenance percentage and end-to-end acceptance
are not yet proven.
