# Tasks: yande.re core journey

**Input**: Design documents in `specs/001-yandere-core-journey/`

**Tests**: TDD is mandatory. For every story, the named tests must be observed
failing before the corresponding implementation task begins.

Tasks T001–T034 retain the original feasibility, foundation, and search history;
their completed checkboxes and wording are not rewritten. The owner's 2026-09-20
UX decision supersedes the historical Safe Mode portions. T035 onward is the
active Popular-first implementation graph.

## Phase 1: Setup and feasibility

**Purpose**: Prove the toolchain and remote read before product architecture.

- [x] T001 Pin Flutter 3.47.5 and create `Makefile`, `.mise.toml`, and
  `pubspec.yaml` with `package:http` as the only runtime dependency.
- [x] T002 Generate the stock Flutter Android application in `lib/main.dart`,
  `android/`, and `test/app_smoke_test.dart`; add `make doctor` and prove a
  debug launch on one API 29+ target. The debug APK was built and launched on
  the connected OnePlus 8 (Android 16 / API 36); see
  `docs/acceptance/feasibility.md`.
- [x] T003 Write a bounded anonymous transport probe test first in
  `tool/feasibility/yandere_probe_test.dart`, then implement
  `tool/feasibility/yandere_probe.dart`; record status, content type, redirect
  host, and normalized item count without response bodies.
- [x] T004 Record toolchain and transport outcomes in
  `docs/acceptance/feasibility.md`; stop feature work if either gate fails.

**Checkpoint**: The Dart client decodes one anonymous post, and the stock debug
APK launches on an API 29+ target.

## Phase 2: Foundational contracts

**Purpose**: Establish public seams and Material baseline shared by all stories.

- [x] T005 [P] Write equality, content-policy, Safe Mode, and state tests in
  `test/domain/post_test.dart` and `test/domain/explore_state_test.dart` before
  implementing normalized values in `lib/src/domain/`.
- [x] T006 [P] Write adapter contract tests in
  `test/contract/site_adapter_contract_test.dart` using
  `test/support/fake_site_adapter.dart`, then define
  `lib/src/sites/site_adapter.dart`.
- [x] T007 [P] Write theme and responsive-shell widget tests in
  `test/design/latte_theme_test.dart`, then implement semantic Material 3 roles
  in `lib/src/design/latte_theme.dart` and `lib/src/app.dart`.
- [x] T008 Add checked-in provenance-labelled yande.re JSON fixtures under
  `test/fixtures/yandere/`, excluding cookies, raw headers, and unreviewed data.
- [x] T009 Configure `make format`, `make analyze`, `make test`, and `make check`
  so deterministic repository gates never call yande.re. The Makefile now
  documents that only the opt-in `probe` target performs live access.

**Checkpoint**: Public contracts, theme roles, reviewed fixtures, and
deterministic gates are green.

## Phase 3: User Story 1 - Discover and inspect art (Priority: P1) MVP

**Goal**: Browse a Material 3 image grid, page, inspect a post, and return to the
same position with honest states.

**Independent Test**: Browse two fixture pages, open detail, change window/theme
conditions, return, and observe the same ordered feed and position.

### Tests for User Story 1

- [x] T010 [P] [US1] Write failing yande.re query/decoder and content-policy
  tests in `test/sites/yandere/yandere_adapter_test.dart`.
- [x] T011 [P] [US1] Write failing discovery pagination, deduplication, stale
  response, and failure-state tests in
  `test/features/explore/explore_controller_test.dart`.
- [x] T012 [P] [US1] Write failing semantics, loading, empty, error, grid,
  detail/back, light/dark, 200% text, reduced-motion, and compact/expanded tests
  in `test/features/explore/explore_screen_test.dart`.

### Implementation for User Story 1

- [x] T013 [US1] Implement private Moebooru parsing and yande.re policy in
  `lib/src/sites/moebooru/` and `lib/src/sites/yandere/` until T010 passes.
- [x] T014 [US1] Implement immutable discovery/detail state and stale-request
  rejection in `lib/src/features/explore/explore_controller.dart` until T011
  passes.
- [x] T015 [US1] Implement Material 3 Explore and Detail widgets in
  `lib/src/features/explore/` using theme roles and constraint-based layouts
  until T012 passes.
- [x] T016 [US1] Add reviewed golden baselines for key compact/expanded and
  light/dark states under `test/goldens/explore/`; run `make test-story
  STORY=discover` and `make check`.

**Checkpoint**: Story 1 is independently usable and accepted before search.

## Phase 4: User Story 2 - Search by yande.re tags (Priority: P2)

**Goal**: Search one opaque yande.re expression without stale results or content
policy bypass.

**Independent Test**: Submit a negative/meta-tag expression, replace it while
loading, reject explicit-content intent only in Safe Mode, and clear back to
discovery.

### Tests for User Story 2

- [x] T017 [P] [US2] Write failing query preservation and Safe Mode explicit-policy
  conflict tests in `test/sites/yandere/yandere_search_test.dart`.
- [x] T018 [P] [US2] Write failing replacement-race, no-results, and
  clear-to-discovery tests in `test/features/explore/search_controller_test.dart`.
- [x] T019 [US2] Write failing Material `SearchAnchor` semantics, focus, submit,
  clear, overflow, and error-state tests in
  `test/features/explore/search_view_test.dart`.

### Implementation for User Story 2

- [x] T020 [US2] Implement yande.re query construction and content-policy
  conflict mapping in `lib/src/sites/yandere/` until T017 passes.
- [x] T021 [US2] Add search intent/state transitions to
  `lib/src/features/explore/explore_controller.dart` until T018 passes.
- [x] T022 [US2] Integrate Material search behavior in
  `lib/src/features/explore/` until T019 passes; run `make test-story
  STORY=search` and `make check`.

**Checkpoint**: Stories 1 and 2 pass independently against the fake adapter.

## Phase 5: User Story 3 - Keep one image locally (Priority: P3)

**Goal**: Save one selected variant to `Pictures/Latte` without overwrite or
partial publication.

**Independent Test**: Save a fixture variant, open the content URI, repeat it,
and prove the original bytes remain unchanged.

### Blocking save feasibility

- [ ] T023 [US3] Write a failing Android MediaStore spike test under
  `android/app/src/androidTest/`, then prove fixture publication, deterministic
  site/post/variant duplicate detection after terminate/relaunch, unchanged
  bytes, and failed-write cleanup on API 29+; record the result in
  `docs/acceptance/feasibility.md` before save UI work.

### Tests for User Story 3

- [ ] T024 [P] [US3] Write failing Dart `PublicImageStore` contract and save-use
  case tests in `test/contract/public_image_store_contract_test.dart` and
  `test/features/save/save_controller_test.dart`.
- [ ] T025 [P] [US3] Write failing Kotlin metadata, pending-publication,
  collision, and cleanup tests under `android/app/src/test/`.
- [ ] T026 [US3] Write failing variant-sheet, progress, duplicate prevention,
  completion, and failure widget tests in
  `test/features/save/save_sheet_test.dart`.

### Implementation for User Story 3

- [ ] T027 [US3] Implement the Dart save port and foreground controller in
  `lib/src/platform/` and `lib/src/features/save/` until T024 passes; the port
  must treat MediaStore lookup, not session memory, as duplicate authority.
- [ ] T028 [US3] Implement the narrow Kotlin MediaStore channel under
  `android/app/src/main/kotlin/` until T025 passes.
- [ ] T029 [US3] Implement the Material 3 variant bottom sheet and save states
  in `lib/src/features/save/` until T026 passes; run `make test-story STORY=save`
  and `make check`.

**Checkpoint**: All three stories are independently accepted.

## Phase 6: Integrated evidence and workflow evaluation

- [ ] T030 Write the failing full owner-journey test in
  `integration_test/yandere_core_journey_test.dart`, then wire only the required
  composition root in `lib/main.dart` until it passes against the fake adapter.
- [ ] T031 Run 20 fixture-backed Android integration journeys and record timing,
  interaction, Material state, and save-repeat outcomes in
  `docs/acceptance/milestone-001.md`.
- [ ] T032 Run opt-in `make acceptance-live DEVICE=<adb-serial>` and append a
  redacted live receipt proving anonymous browse/search/detail and one save.
- [ ] T033 Run final Spec Kit analysis and convergence; update
  `docs/spec-kit-evaluation.md` with traceability, seeded-inconsistency result,
  artifact effort, defects caught, and keep/simplify/remove recommendation.
- [ ] T034 Run `make check`, verify the owner checklist is resolved, and perform
  the owner launch → browse → inspect → save acceptance without instruction.

## Dependencies and execution order

- T001–T004 are blocking feasibility work.
- T005–T009 establish shared contracts and must finish before any user story.
- The historical stories remain recorded in their original order. The active
  Popular-first graph is T035 → T036/T037 → T038 → T039 → T040; the save slice
  follows its feasibility gate after the Popular foundation.
- Within a story, every test task must be observed red before implementation.
- T030–T034 depend on all selected stories; live acceptance is never part of
  deterministic `make check`.

## Requirement traceability

| Requirement | Tasks |
| --- | --- |
| FR-001–FR-005, FR-008–FR-012 | T035–T039 |
| FR-006–FR-007, FR-020–FR-021 | T001–T004, T040 |
| FR-013–FR-018 | T023–T029 |
| FR-019 | T006, T009, T033, T040 |
| FR-022–FR-024 | T007, T012, T015–T016, T019, T039–T040 |
| SC-001–SC-007, SC-009 | T016, T022–T032, T040 |
| SC-008 | T033 |

## Phase 7: Popular-first redesign and compatibility correction

**Purpose**: Make the owner's actual discovery journey the active product
center: Popular day/week/month → masonry exploration → detail/pager → download.
Authenticated 0–3 personal scoring remains a later specification.

- [ ] T035 [P] Write replacement domain, adapter, controller, and widget tests
  proving there is no Safe Mode/filter/policy-conflict path and that explicit
  ratings and opaque expressions remain unchanged. Remove the superseded
  production policy code only after the tests are observed red.
- [ ] T036 [P] Write failing `PopularPeriod`, anchor/window identity, UTC
  normalization, and serialization tests in `test/domain/popular_query_test.dart`,
  then implement the smallest immutable Popular query values in
  `lib/src/domain/`.
- [ ] T037 [P] Write failing yande.re Popular mapping tests against a reviewed
  fixture and request capture, then map day/week/month queries to the verified
  adapter-owned `order:score` and date-window expression without leaking wire
  syntax to the UI.
- [ ] T038 Write failing controller tests for Popular period/anchor switching,
  restoration after detail/back, duplicate-page append, and stale response
  rejection, then implement those transitions in the Explore controller.
- [ ] T039 Write failing Explore widget tests for Popular/Newest navigation,
  visible Day/Week/Month controls, readable anchor/window navigation, compact
  aggregate score/rating/dimensions semantics, and 48dp targets. Implement the
  Material 3 responsive aspect-preserving masonry-like surface and detail pager.
- [ ] T040 Add reviewed compact/expanded light/dark Popular goldens and run the
  focused Popular story target, `make check`, `rumdl`, `git diff --check`, and
  the Android build/install/launch receipt on device `0cadf428` when connected.

### Popular-first checkpoint

- Popular day/week/month and anchor/window are stable query identity.
- Explicit ratings remain visible; no Safe Mode UI, policy filter, or query
  rewrite exists.
- Popular and Newest are visible primary modes; the masonry feed preserves
  aspect ratio and existing content while paging.
- Detail/back preserves context and supports previous/next within the loaded
  feed. Download and authenticated scoring remain separately staged.
