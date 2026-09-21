---

description: "Implementation tasks for the Pixiv illustration experience"
---

# Tasks: Pixiv illustration experience

**Input**: [spec.md](spec.md) and [plan.md](plan.md)

**Tests**: Required by the Latte constitution. Each behavior task starts with
a failing public-seam test and leaves `make check` green.

## Phase 1: Setup

- [x] T001 Add redacted Pixiv fixture responses for ranking, multi-page detail, search, search support, followed, favorites, and bookmark outcomes in `app/src/test/resources/pixiv/`
- [x] T002 [P] Add shared fixture-loading helpers at `app/src/test/kotlin/com/azusachino/latte/data/network/PixivFixtures.kt`

## Phase 2: Foundational seams

- [x] T003 [P] Extend the source-neutral `Post` model in `app/src/main/kotlin/com/azusachino/latte/data/model/Post.kt` with optional title, canonical URL, bookmark metadata, page identity, and source-aware download identity while preserving Yande mapping
- [x] T004 [P] Define typed Pixiv feed, search-support, bookmark, and transport result states in `app/src/main/kotlin/com/azusachino/latte/data/network/PixivFeedContract.kt`
- [x] T005 Add declared authentication-flow metadata to `app/src/main/kotlin/com/azusachino/latte/plugin/SitePlugin.kt` without breaking Yande credential login

## Phase 3: User Story 1 — Browse Pixiv popular works (P1) 🎯 MVP

**Independent test**: Fixture-backed daily ranking loads into the existing grid;
opening a multi-page work pages through the work's pages; saving a page uses a
stable Pixiv identity; malformed and transport responses render explicit errors.

### Tests first

- [x] T006 [P] [US1] Add mapper and identity tests for Pixiv artwork/page normalization in `app/src/test/kotlin/com/azusachino/latte/data/model/PixivArtworkTest.kt`
- [x] T007 [P] [US1] Add MockWebServer tests for daily ranking, detail mapping, opaque `next_url`, malformed JSON, and upstream status classification in `app/src/test/kotlin/com/azusachino/latte/data/network/PixivApiTest.kt`
- [x] T008 [P] [US1] Add Pixiv.Cat resolver tests for page zero, page greater than zero, HTTPS host validation, content type, filename, non-image response, and retryable failure in `app/src/test/kotlin/com/azusachino/latte/data/network/PixivCatResolverTest.kt`
- [x] T009 [US1] Add detail/save identity coverage for a multi-page Pixiv work in `app/src/test/kotlin/com/azusachino/latte/data/download/PixivDownloadIdentityTest.kt`

### Implementation

- [x] T010 [P] [US1] Implement Pixiv DTOs, normalized artwork mapper, and page/media identity in `app/src/main/kotlin/com/azusachino/latte/data/model/PixivArtwork.kt`
- [x] T011 [P] [US1] Implement `PixivCatResolver` with canonical original URL retention and response type/filename validation in `app/src/main/kotlin/com/azusachino/latte/data/network/PixivCatResolver.kt`
- [x] T012 [US1] Implement fixture-compatible ranking/detail requests and typed response classification in `app/src/main/kotlin/com/azusachino/latte/data/network/PixivApi.kt`
- [x] T013 [US1] Connect anonymous Pixiv Popular state to `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreViewModel.kt` and the existing `FeedGrid` in `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreScreen.kt`
- [x] T014 [US1] Make `app/src/main/kotlin/com/azusachino/latte/ui/detail/DetailScreen.kt` page through a selected Pixiv work and use its canonical URL for open/share actions
- [x] T015 [US1] Make `app/src/main/kotlin/com/azusachino/latte/data/download/DownloadManager.kt` preserve Pixiv source/work/page/variant identity and source-aware filenames

## Phase 4: User Story 2 — Search Pixiv illustrations (P1)

**Independent test**: A keyword query, autocomplete suggestion, and trending
tag each produce the same Pixiv grid/detail flow with opaque continuation.

### Tests first

- [x] T016 [P] [US2] Add MockWebServer tests for illustration search, autocomplete, trending tags, empty results, and auth-required responses in `app/src/test/kotlin/com/azusachino/latte/data/network/PixivApiTest.kt`

### Implementation

- [x] T017 [US2] Add search, autocomplete, trending-tag, and opaque-cursor operations to `app/src/main/kotlin/com/azusachino/latte/data/network/PixivApi.kt`
- [x] T018 [US2] Add Pixiv query state, continuation, empty/error handling, and refresh behavior to `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreViewModel.kt`
- [x] T019 [US2] Add Pixiv autocomplete/trending-tag entry states to the existing search surface in `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreScreen.kt`

## Phase 5: User Story 3 — Connect Pixiv and use personal feeds (P2)

**Independent test**: Token import persists encrypted session fields; followed
and Favorites require auth; refresh invalidation signs out; bookmark mutation
updates only after a successful response.

### Tests first

- [x] T020 [P] [US3] Add token import, encrypted persistence, refresh success, refresh failure, logout, and auth-state tests in `app/src/test/kotlin/com/azusachino/latte/plugin/pixiv/PixivPluginTest.kt`
- [x] T021 [P] [US3] Add followed/Favorites/bookmark request and typed-error tests in `app/src/test/kotlin/com/azusachino/latte/data/network/PixivApiTest.kt`

### Implementation

- [x] T022 [US3] Implement encrypted Pixiv session storage and refresh-token lifecycle in `app/src/main/kotlin/com/azusachino/latte/plugin/pixiv/PixivPlugin.kt`
- [x] T023 [US3] Add authenticated followed, Favorites, bookmark, and unbookmark operations to `app/src/main/kotlin/com/azusachino/latte/data/network/PixivApi.kt`
- [x] T024 [US3] Replace the generic Pixiv password form with browser-first and advanced token-entry flows in `app/src/main/kotlin/com/azusachino/latte/ui/account/PluginLoginDialog.kt`
- [x] T025 [US3] Connect Pixiv session state, followed/Favorites gating, bookmark mutation, and session-expiry errors to `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreViewModel.kt` and `app/src/main/kotlin/com/azusachino/latte/ui/detail/DetailScreen.kt`

## Phase 6: User Story 4 — Switch platforms and manage accounts (P2)

**Independent test**: Tapping `Latte` switches between Yande and Pixiv, restores
bounded source-local state, cancels stale requests, and Settings exposes the
approved `Platforms & accounts` cards/actions.

### Tests first

- [ ] T026 [P] [US4] Add platform-local state and stale-request cancellation tests in `app/src/test/kotlin/com/azusachino/latte/ui/explore/ExplorePlatformStateTest.kt` (state isolation covered; cancellation remains integration-only)
- [x] T027 [P] [US4] Add plugin-manager capability/status tests for Yande and Pixiv cards in `app/src/test/kotlin/com/azusachino/latte/plugin/SitePluginManagerTest.kt`

### Implementation

- [x] T028 [US4] Register `YandePlugin` and `PixivPlugin` with the shared manager and source-aware API dependencies in `app/src/main/kotlin/com/azusachino/latte/ui/LatteApp.kt`
- [x] T029 [US4] Add the clickable platform picker, restrained platform background/tint, per-platform tabs, and reduced-motion-safe crossfade in `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreScreen.kt`
- [x] T030 [US4] Refactor `ExploreViewModel` to retain bounded Yande/Pixiv feed, query, scroll, and detail context in `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreViewModel.kt`
- [x] T031 [US4] Redesign the Settings account entry and platform cards as `Platforms & accounts` in `app/src/main/kotlin/com/azusachino/latte/ui/settings/SettingsScreen.kt` and `app/src/main/kotlin/com/azusachino/latte/ui/account/AccountManagerScreen.kt`
- [x] T032 [US4] Wire platform-scoped Manage, Sign in, and Sign out actions through `app/src/main/kotlin/com/azusachino/latte/ui/account/PluginProfileDialog.kt` and `app/src/main/kotlin/com/azusachino/latte/ui/LatteApp.kt`

## Phase 7: Polish and gates

- [x] T033 [P] Add quiet inline error, auth-required, rate-limit, upstream-drift, and transport-failure copy without toast-only recovery in `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreScreen.kt` and `app/src/main/kotlin/com/azusachino/latte/ui/detail/DetailScreen.kt`
- [x] T034 Run `make check`, inspect `git diff --check`, and record fixture/test evidence in this task file
- [ ] T035 Build and install a debug APK with `make validate` and `make install`, then record the physical-device Explore/platform-switch/detail receipt before declaring the journey complete (startup verified; phone remained locked for UI interaction)

## Dependencies and execution order

- Setup → Foundational → US1 → US2 → US3 → US4 → Polish.
- US1 is the MVP and blocks only the later feed/UI slices.
- T002, T003, T004, T006, T007, T008, T010, T011, T016, T020, T021, T026,
  and T027 are parallel only when they touch different files; all tests must
  be written and observed failing before their implementation task.
- US2 depends on the normalized Pixiv model and API seam from US1.
- US3 depends on Pixiv API result classification and the account-flow seam.
- US4 depends on both plugins exposing stable account/status flows.

## Implementation strategy

1. Complete T001–T015 and stop at the first owner-visible Popular → detail →
   save journey.
2. Complete search and account slices independently, keeping fixtures green.
3. Integrate platform switching and account management only after source-local
   feed state is observable.
4. Run the live/auth and physical-device gates; leave any unverified live route
   explicit rather than claiming success.

## Evidence

- `make check` passed on 2026-09-21: rumdl found no Markdown issues and the
  full `testDebugUnitTest` suite passed with 34 tests and no failures.
- The browser OAuth exchange, refresh/retry, failed-refresh invalidation,
  platform capability/status, and multi-page download identity tests pass with
  MockWebServer or pure unit fixtures. Production OAuth client identifiers are
  supplied through `LATTE_PIXIV_CLIENT_ID` and `LATTE_PIXIV_CLIENT_SECRET`; no
  credential is committed to the repository.
- `make validate` passed and `make install` installed the debug APK on the
  connected OnePlus 8 (`0cadf428`, Android 16).
- The first device launch exposed stale encrypted cookie preferences; the
  startup recovery path now drops only that cookie cache and recreates it.
  The current relaunch reached the Latte process with no fresh fatal exception,
  but the device remains on the dreaming lock screen, so platform-switch,
  browser callback, and detail interaction remain unverified.
