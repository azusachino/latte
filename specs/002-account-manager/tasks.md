# Tasks: 002 Account Manager Center (yande.re & Pixiv)

**Feature**: 002 Account Manager Center  
**Status**: Ready for execution  
**Constitution**: v2.0.0 (Device-First Ergonomics, Downsized Spec-Kit)  

---

## Slice 1: Security & Storage Foundation

- [ ] **T001**: Add `androidx.security:security-crypto:1.1.0-alpha06` to `app/build.gradle.kts`.
- [ ] **T002**: Define domain models in `com.azusachino.latte.data.account.model`:
  - `SiteId` (`YANDE`, `PIXIV`)
  - `AuthState` (`Unauthenticated`, `Authenticating`, `Authenticated(profile)`, `Error(message)`)
  - `UserProfile(userId, username, avatarUrl)`
  - `Account(siteId, authState, credentials)`
- [ ] **T003**: Implement `SecureAccountStorage` backed by `EncryptedSharedPreferences` for storing/retrieving credentials and tokens per `SiteId`.
- [ ] **T004**: Unit tests for `SecureAccountStorage`: persistence across re-instantiation, deletion on logout, and serialization.

---

## Slice 2: Yande Authentication & API Integration

- [ ] **T005**: Implement `YandePasswordHasher`: `SHA1("choujin-steiner--$password--")` with hex encoding.
- [ ] **T006**: Add authentication endpoints to `YandeApi`:
  - Verify credentials / user profile check.
  - Return structured success (`UserProfile`) or error (`InvalidCredentials`, `NetworkError`).
- [ ] **T007**: Implement `YandeAuthAdapter` managing login, session verification, and sign-out.
- [ ] **T008**: Implement `AccountManager` coordinator exposing `StateFlow<Map<SiteId, AccountState>>`.
- [ ] **T009**: Unit tests for `YandePasswordHasher` and `YandeAuthAdapter` with MockWebServer.

---

## Slice 3: Account Manager Center UI & Device Slice (Principle VI)

- [ ] **T010**: Create `AccountManagerScreen` with platform cards for `yande.re` and `pixiv`, displaying current connection status.
- [ ] **T011**: Create `YandeLoginDialog` with username, password, password visibility toggle, IME action (`Next` → `Done`), and inline error feedback.
- [ ] **T012**: Add "Account Manager" entry row to `SettingsScreen` under an "Accounts" section.
- [ ] **T013**: Wire `AccountManagerViewModel` to `AccountManagerScreen` and `YandeLoginDialog`.
- [ ] **T014**: **Hardware Verification**: Deploy debug APK to OnePlus 8 (`0cadf428`) and verify:
  - IME keyboard open/close and focus transitions.
  - Password visibility toggle.
  - Live login with yande.re credentials and credential persistence across app kill.

---

## Slice 4: Yande Personal Scoring & Favorites

- [ ] **T015**: Add `vote(postId: Long, score: Int)` to `YandeApi` executing `POST /post/vote.json`.
- [ ] **T016**: Add interactive 0–3 star rating bar and Favorite toggle button to `DetailScreen`.
- [ ] **T017**: Handle unauthenticated rating click: prompt user to sign in via Account Manager.
- [ ] **T018**: Add "My Favorites" filter/chip to `ExploreScreen` querying `vote:3:<username>` when signed in.
- [ ] **T019**: Unit and integration tests for voting and favorites query.

---

## Slice 5: Pixiv Platform Foundation

- [ ] **T020**: Define Pixiv credential and token models (`accessToken`, `refreshToken`, `expiresAt`).
- [ ] **T021**: Create `PixivAuthAdapter` skeleton managing token lifecycle and OAuth2 PKCE parameters.
- [ ] **T022**: Implement OkHttp `PixivInterceptor`: automatically injects `Referer: https://app-api.pixiv.net/` and `Authorization: Bearer <token>` for Pixiv domains (`*.pximg.net`, `app-api.pixiv.net`).
- [ ] **T023**: Add Pixiv card UI in `AccountManagerScreen` with token configuration / sign-in options.
- [ ] **T024**: Unit tests for `PixivInterceptor` verifying header injection.

---

## Slice 6: Quality Gates & Milestone Release

- [ ] **T025**: Run `make check` (`./gradlew testDebugUnitTest`, lint, formatting).
- [ ] **T026**: Perform full hardware acceptance on OnePlus 8 (`0cadf428`): Yande sign-in, post scoring, favorites browsing, sign-out.
- [ ] **T027**: Bump version to `0.0.2` in `app/build.gradle.kts`.
