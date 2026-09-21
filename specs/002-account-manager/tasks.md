# Tasks: 002 Account Manager Center (Mihon-Style Plugin Architecture)

**Feature**: 002 Account Manager Center  
**Status**: Released as `v0.0.2` with Slices 1–4 implemented; the
review-blocking storage, authentication, adapter-boundary, Safe Mode, and
inline-error issues are repaired. Secure-storage tests, T014 hardware
verification, and Pixiv remain open; pool browsing was delivered post-hoc.
Pixiv work is now specified by
[`004-pixiv-illustration-experience`](../004-pixiv-illustration-experience/spec.md);
the Slice 5 entries below are retained as historical planning notes until the
new contract is decomposed into implementation tasks.
**Inspiration**: Mihon / Tachiyomi `Tracker` & `TrackerManager`  
**Constitution**: v2.0.0 (Device-First Ergonomics, Downsized Spec-Kit)  

---

## Slice 1: Plugin Core & Secure Storage

- [x] **T001**: Add `androidx.security:security-crypto:1.1.0-alpha06` to `app/build.gradle.kts`.
- [x] **T002**: Define core plugin contracts in `com.azusachino.latte.plugin`:
  - `SitePlugin`: interface with `id`, `name`, `iconRes`, `authType`, `capabilities`, `isLoggedIn`, `isLoggedInFlow`, `login()`, `logout()`, `applyHeaders()`
  - `AuthType` (`CREDENTIALS`, `OAUTH2`, `API_KEY`)
  - `PluginCapability` (`SCORING`, `FAVORITES`, `REFERER_INJECT`, `USER_FEED`)
- [x] **T003**: Implement `SecurePluginStorage` backed by `EncryptedSharedPreferences` for plugin-scoped credential storage, and make `PersistentCookieJar` use encrypted preferences with no ordinary-preferences fallback after Keystore failure.
- [ ] **T004**: Implement `SitePluginManager` managing `plugins = listOf(yandePlugin, pixivPlugin)` and exposing `loggedInPluginsFlow()`. The manager exists, but the runtime registry currently contains Yande only until the Pixiv slice lands.
- [ ] **T005**: Unit tests for `SecurePluginStorage` and `SitePluginManager`. Manager tests exist; secure-storage tests are still missing.

---

## Slice 2: YandePlugin Implementation

- [x] **T006**: Implement `YandePasswordHasher`: `SHA1("choujin-steiner--$password--")` with hex string output.
- [x] **T007**: Add credential verification at the Yande login boundary:
  - Accept a login only when the response establishes a positive `user_id` session cookie.
  - Do not use a public user endpoint as credential evidence.
- [x] **T008**: Implement `YandePlugin` implementing `SitePlugin`:
  - `id = "yande.re"`
  - `capabilities = setOf(SCORING, FAVORITES)`
  - `login(mapOf("username" to u, "password" to p))`
  - `logout()`
- [x] **T009**: Unit tests for `YandePlugin` with MockWebServer.

---

## Slice 3: Mihon-Style Account Center UI & Device Slice (Principle VI)

- [x] **T010**: Create `AccountPreferenceWidget` (Mihon-style):
  - Platform icon/logo, title, display username (when logged in), and green checkmark badge.
  - Tapping opens login or logout/profile dialog.
- [x] **T011**: Create `PluginLoginDialog`:
  - Supports `AuthType.CREDENTIALS` (username, password, password toggle, IME action `Next` → `Done`, inline error message).
- [x] **T012**: Create `PluginProfileDialog` for logged-in state (shows username, active capabilities, and "Sign Out" button).
- [x] **T013**: Create `AccountManagerScreen` and link it under "Accounts" in `SettingsScreen`.
- [ ] **T014**: **Hardware Verification**: Deploy debug APK to OnePlus 8 (`0cadf428`):
  - [x] Test Mihon-style preference widget rendering.
  - [x] Test keyboard IME (fixed a real focus-advance bug, see `9d8ee22`) and password toggle.
  - [ ] Test live Yande login and app kill/restart credential persistence — needs a real yande.re test account, not yet run.

---

## Slice 4: Yande Scoring & Favorites Integration

- [x] **T015**: Implement `setScore(postId: Long, score: Int)` in `YandePlugin` calling `POST /post/vote.json`.
- [x] **T016**: Wire `DetailScreen` through `SitePluginManager.get(post.siteId)` and check the resolved plugin's login state and `PluginCapability.SCORING`:
  - If logged in: show interactive 0–3 star rating bar and Favorite toggle button (`score == 3`).
  - If not logged in: tapping rating stars launches `PluginLoginDialog` for Yande.
- [x] **T017**: Add "My Favorites" filter chip in `ExploreScreen` querying `vote:3:<username>` when `YandePlugin` is logged in. (Superseded 2026-09-20: promoted to a dedicated Favorites tab with its own feed, see User Story 2 in `spec.md`.)
- [x] **T018**: Unit tests for scoring capability, the exact Yande password hash vector, authenticated-session login evidence, and adapter-owned favorite/pool query construction.

---

## Post-hoc delivered scope: Pool Browsing

This scope was added from live-device feedback after the original Slice 4
breakdown. It is tracked here so the implementation record covers the shipped
behavior without rewriting the original acceptance history.

- [x] **T019**: Add a Pools tab that lists and searches pools through `GET /pool.json`.
- [x] **T020**: Show pool id, name, post count, private-state badge, and lazy cover thumbnails.
- [x] **T021**: Open a pool through the existing `YandeApi.poolTags(<id>)` post feed and preserve a titled back path to Pools.

---

## Slice 5: PixivPlugin Foundation & Header Interceptor

- [ ] **T022**: Implement `PixivPlugin` skeleton:
  - `id = "pixiv"`
  - `authType = AuthType.OAUTH2`
  - `capabilities = setOf(PluginCapability.REFERER_INJECT)`
- [ ] **T023**: Implement `applyHeaders` in `PixivPlugin`: injects `Referer: https://app-api.pixiv.net/` and `Authorization: Bearer <token>` for `*.pximg.net` and `app-api.pixiv.net`.
- [ ] **T024**: Add `PluginHeaderInterceptor` into `OkHttpProvider` routing outbound requests through `SitePluginManager.plugins.forEach { it.applyHeaders(...) }`.
- [ ] **T025**: Add Pixiv card in `AccountManagerScreen` with token configuration / sign-in options.
- [ ] **T026**: Unit tests for `PluginHeaderInterceptor` verifying Referer header injection.

---

## Slice 6: Release & Hardware Receipt

- [x] **T027**: Run the project gates: `make check`, `make lint`, and `make validate` (all passed locally on 2026-09-21; `make check` runs Markdown checks and unit tests).
- [ ] **T028**: Final hardware acceptance on OnePlus 8 (`0cadf428`).
- [x] **T029**: Bump version to `0.0.2` in `app/build.gradle.kts`.
