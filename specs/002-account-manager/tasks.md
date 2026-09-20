# Tasks: 002 Account Manager Center (Mihon-Style Plugin Architecture)

**Feature**: 002 Account Manager Center  
**Status**: Ready for execution  
**Inspiration**: Mihon / Tachiyomi `Tracker` & `TrackerManager`  
**Constitution**: v2.0.0 (Device-First Ergonomics, Downsized Spec-Kit)  

---

## Slice 1: Plugin Core & Secure Storage

- [x] **T001**: Add `androidx.security:security-crypto:1.1.0-alpha06` to `app/build.gradle.kts`.
- [x] **T002**: Define core plugin contracts in `com.azusachino.latte.plugin`:
  - `SitePlugin`: interface with `id`, `name`, `iconRes`, `authType`, `capabilities`, `isLoggedIn`, `isLoggedInFlow`, `login()`, `logout()`, `applyHeaders()`
  - `AuthType` (`CREDENTIALS`, `OAUTH2`, `API_KEY`)
  - `PluginCapability` (`SCORING`, `FAVORITES`, `REFERER_INJECT`, `USER_FEED`)
- [x] **T003**: Implement `SecurePluginStorage` backed by `EncryptedSharedPreferences` for plugin-scoped credential storage.
- [x] **T004**: Implement `SitePluginManager` managing `plugins = listOf(yandePlugin, pixivPlugin)` and exposing `loggedInPluginsFlow()`.
- [x] **T005**: Unit tests for `SecurePluginStorage` and `SitePluginManager`.

---

## Slice 2: YandePlugin Implementation

- [x] **T006**: Implement `YandePasswordHasher`: `SHA1("choujin-steiner--$password--")` with hex string output.
- [x] **T007**: Add credential verification to `YandeApi`:
  - Verify session via `/user/check` or authenticated probe.
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
  - Test Mihon-style preference widget rendering.
  - Test keyboard IME, password toggle, and live Yande login.
  - Test app kill and restart: credentials remain securely restored.

---

## Slice 4: Yande Scoring & Favorites Integration

- [x] **T015**: Implement `setScore(postId: Long, score: Int)` in `YandePlugin` calling `POST /post/vote.json`.
- [x] **T016**: Wire `DetailScreen` to check `YandePlugin.isLoggedIn` and `PluginCapability.SCORING`:
  - If logged in: show interactive 0–3 star rating bar and Favorite toggle button (`score == 3`).
  - If not logged in: tapping rating stars launches `PluginLoginDialog` for Yande.
- [x] **T017**: Add "My Favorites" filter chip in `ExploreScreen` querying `vote:3:<username>` when `YandePlugin` is logged in.
- [x] **T018**: Unit tests for scoring capability and favorites query.

---

## Slice 5: PixivPlugin Foundation & Header Interceptor

- [ ] **T019**: Implement `PixivPlugin` skeleton:
  - `id = "pixiv"`
  - `authType = AuthType.OAUTH2`
  - `capabilities = setOf(PluginCapability.REFERER_INJECT)`
- [ ] **T020**: Implement `applyHeaders` in `PixivPlugin`: injects `Referer: https://app-api.pixiv.net/` and `Authorization: Bearer <token>` for `*.pximg.net` and `app-api.pixiv.net`.
- [ ] **T021**: Add `PluginHeaderInterceptor` into `OkHttpProvider` routing outbound requests through `SitePluginManager.plugins.forEach { it.applyHeaders(...) }`.
- [ ] **T022**: Add Pixiv card in `AccountManagerScreen` with token configuration / sign-in options.
- [ ] **T023**: Unit tests for `PluginHeaderInterceptor` verifying Referer header injection.

---

## Slice 6: Release & Hardware Receipt

- [ ] **T024**: Run `make check` (`./gradlew testDebugUnitTest`, lint, formatting).
- [ ] **T025**: Final hardware acceptance on OnePlus 8 (`0cadf428`).
- [ ] **T026**: Bump version to `0.0.2` in `app/build.gradle.kts`.
