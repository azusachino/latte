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
- [x] T008 [P] [US1] Add Pixiv media resolver tests for official URL retention, `i.pixiv.re` path-preserving fallback, `pixiv.cat` page zero/page greater than zero, HTTPS host validation, content type, filename, non-image response, and retryable failure in `app/src/test/kotlin/com/azusachino/latte/data/network/PixivCatResolverTest.kt`
- [x] T009 [US1] Add detail/save identity coverage for a multi-page Pixiv work in `app/src/test/kotlin/com/azusachino/latte/data/download/PixivDownloadIdentityTest.kt`

### Implementation

- [x] T010 [P] [US1] Implement Pixiv DTOs, normalized artwork mapper, and page/media identity in `app/src/main/kotlin/com/azusachino/latte/data/model/PixivArtwork.kt`
- [x] T011 [P] [US1] Implement `PixivCatResolver` with canonical original URL retention and response type/filename validation in `app/src/main/kotlin/com/azusachino/latte/data/network/PixivCatResolver.kt`
- [x] T012 [US1] Implement fixture-compatible ranking/detail requests and typed response classification in `app/src/main/kotlin/com/azusachino/latte/data/network/PixivApi.kt`
- [x] T013 [US1] Connect authenticated Pixiv Popular state to `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreViewModel.kt` and the existing `FeedGrid` in `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreScreen.kt`
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

**Independent test**: Browser OAuth and advanced token import persist encrypted
session fields; followed and Favorites require auth; refresh invalidation signs
out; bookmark mutation updates only after a successful response.

### Tests first

- [x] T020 [P] [US3] Add browser OAuth/token import, encrypted persistence, refresh success, refresh failure, logout, and auth-state tests in `app/src/test/kotlin/com/azusachino/latte/plugin/pixiv/PixivPluginTest.kt`
- [x] T021 [P] [US3] Add followed/Favorites/bookmark request and typed-error tests in `app/src/test/kotlin/com/azusachino/latte/data/network/PixivApiTest.kt`

### Implementation

- [x] T022 [US3] Implement encrypted Pixiv session storage and refresh-token lifecycle in `app/src/main/kotlin/com/azusachino/latte/plugin/pixiv/PixivPlugin.kt`
- [x] T023 [US3] Add authenticated followed, Favorites, bookmark, and unbookmark operations to `app/src/main/kotlin/com/azusachino/latte/data/network/PixivApi.kt`
- [x] T024 [US3] Replace the generic Pixiv password form with browser-first and advanced token-entry flows in `app/src/main/kotlin/com/azusachino/latte/ui/account/PluginLoginDialog.kt`
- [x] T025 [US3] Connect Pixiv session state, followed/Favorites gating, bookmark mutation, and session-expiry errors to `app/src/main/kotlin/com/azusachino/latte/ui/explore/ExploreViewModel.kt` and `app/src/main/kotlin/com/azusachino/latte/ui/detail/DetailScreen.kt`

## Phase 5.5: Author works and content safety

**Independent test**: Selecting a Pixiv author from detail opens that author's
illustration works, while Safe Mode filters Pixiv results and reloads the
active feed without confusing a filtered page with an upstream empty result.

- [x] T036 [P] Add Pixiv author ID mapping, user-works endpoint coverage, and
  author-action state coverage in `PixivArtworkTest.kt`, `PixivApiTest.kt`, and
  `ExplorePlatformStateTest.kt`.
- [x] T037 [US1] Connect the detail author action to the numeric Pixiv user-works
  feed and preserve the Yande `user:<name>` search behavior.
- [x] T038 [US1] Apply the global Safe Mode preference to normalized Pixiv feed,
  search, and author-work results, continue through opaque cursors after a
  fully filtered page, and cover the filtering seam with a unit test.

## Phase 6: User Story 4 — Switch platforms and manage accounts (P2)

**Independent test**: Tapping `Latte` switches between Yande and Pixiv, restores
bounded source-local state, cancels stale requests, and Settings exposes the
approved `Platforms & accounts` cards/actions.

### Tests first

- [x] T026 [P] [US4] Add platform-local state and stale-request cancellation tests in `app/src/test/kotlin/com/azusachino/latte/ui/explore/ExplorePlatformStateTest.kt` (the production `applyIfActive` seam rejects a late response after its request job is canceled)
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
- [x] T035 Build and install a debug APK with `make validate` and `make install`, then record the physical-device Explore/platform-switch/detail receipt before declaring the journey complete (Explore, Yande detail, Pixiv browser OAuth exchange, authenticated Popular/Following/Favorites rendering, platform picker, and Platforms & accounts verified on the connected OnePlus 8; author works and Pixiv Safe Mode are also verified; bookmark toggle, completed download, duplicate-save warning, Following detail-back position restoration, and loading-image fallback states are verified live)

## Phase 8: Post-acceptance image transport hardening

- [x] T039 [US1] Prefer official Pixiv medium/full image URLs with the required Referer for previews and detail, retain `i.pixiv.re` and `pixiv.cat` fallbacks, and cover the candidate order in `app/src/main/kotlin/com/azusachino/latte/data/model/PixivArtwork.kt`, `app/src/main/kotlin/com/azusachino/latte/data/model/Post.kt`, `app/src/main/kotlin/com/azusachino/latte/data/network/OkHttpProvider.kt`, and `app/src/test/kotlin/com/azusachino/latte/data/model/PixivArtworkTest.kt`
- [x] T040 [US1] Preserve the originating detail destination when opening Pixiv author works, then return through the author feed to the same work/page on Back in `app/src/main/kotlin/com/azusachino/latte/ui/LatteApp.kt` and `app/src/test/kotlin/com/azusachino/latte/ui/LatteAppNavigationTest.kt`

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
  full `testDebugUnitTest` suite passed with no failures.
- The browser OAuth exchange, verifier persistence across plugin recreation,
  refresh/retry, failed-refresh invalidation, platform capability/status, and
  multi-page download identity tests pass with MockWebServer or pure unit
  fixtures. The app uses Pixiv's public native-client configuration, with
  `LATTE_PIXIV_CLIENT_ID` and `LATTE_PIXIV_CLIENT_SECRET` retained as optional
  build-time overrides.
- `make validate` passed and `make install` installed the debug APK on the
  connected OnePlus 8 (`0cadf428`, Android 16).
- The connected-device receipt covered Yande Explore artwork, the Latte
  platform picker, Pixiv's `Popular | Following | Favorites` surface and
  explicit auth-required state, the Pixiv browser-login dialog, the
  `Platforms & accounts` screen, and a Yande detail screen with Save/Open/Share
  actions. The refreshed picker now shows teal Yande `y` and blue Pixiv `p`
  favicon-like badges with a selected check; switching platforms also changes
  the root Material 3 palette used across the app.
- The same device receipt opened a Pixiv detail Information sheet, followed its
  Author row into a populated author-works feed, toggled Safe Mode, and returned
  to a populated filtered Popular feed.
- The final authenticated-feed receipt rendered real Following and Favorites
  grids, toggled a Pixiv bookmark off and back on, completed a WorkManager save
  into `Pictures/Latte`, and showed the visible duplicate-save warning on the
  second save request.
- The cancellation regression test drives the production Pixiv result-apply
  seam with a deliberately late response and proves a canceled request cannot
  update feed state.
- The navigation regression test drives `Following -> detail -> author works`
  and proves Back returns to the original detail destination before the feed.
- The post-fix device smoke repeated `Following -> detail -> author works` on
  the connected OnePlus 8: the first Back restored the detail screen and the
  second Back restored the Pixiv feed tabs.
- The connected-device regression pass scrolled deep into Pixiv Following,
  opened a work, returned with Android Back, and observed the same visible
  viewport. It also opened a card while its thumbnail was still loading and
  observed a visible detail loading/preview state rather than a blank viewer.
- The anonymous App API ranking probe returned HTTP 400 with Pixiv's
  `invalid_request` OAuth message. PixEz's login-first interceptor and the
  device probe agree that native-client Popular is authenticated; the public
  website's separate ranking route is research-only because the workstation
  returned JSON while Android received HTML. Official `i.pximg.net` medium
  previews and original detail URLs are now tried first with
  `Referer: https://app-api.pixiv.net/`; `i.pixiv.re` is the path-preserving
  fallback and the `pixiv.cat` ID/page form is the final fallback. A same-day
  probe returned HTTP 500 from the documented `i.pixiv.cat` path but HTTP 200
  from the ID route and `i.pixiv.re`, so the implementation does not depend on
  `i.pixiv.cat` availability.
- After the direct-source transport change, `make check` and `lintDebug` passed;
  the debug APK was installed on the connected OnePlus 8. A device smoke
  capture showed a populated Pixiv grid shortly after switching platforms and
  the same populated state after the loading window, without an image-error
  flash.
