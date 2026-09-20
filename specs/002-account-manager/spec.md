# Feature Specification: 002 Account Manager Center (yande.re & Pixiv)

**Feature Branch**: `feat/002-account-manager`

**Created**: 2026-09-20

**Status**: Planned

**Input**: User request: "lets plan for the 0.0.2; account manager center (first with yande.re, and possible pixiv support)"

---

## 1. Vision & Architectural Seams

Latte evolves from an anonymous single-site viewer into a multi-platform art workstation. Milestone 0.0.2 introduces the **Account Manager Center**, a unified subsystem for managing platform credentials, sessions, and authenticated capabilities.

Rather than tightly coupling login logic to individual screens, authentication is structured behind clean platform-neutral seams:
- **`Account`**: Data model capturing site identity (`SiteId`), credentials/tokens, user profile, and active `AuthState`.
- **`AccountStorage`**: Secure, hardware-backed credential persistence using Android `EncryptedSharedPreferences` (Jetpack Security). No credentials or password hashes in plaintext.
- **`AccountManager`**: Reactive coordinator exposing `StateFlow<Map<SiteId, AccountState>>` to the UI, orchestrating login, token refresh, credential verification, and sign-out.
- **`YandeAuthAdapter`**: Implements authentication and scoring for yande.re via SHA-1 password hashing (`choujin-steiner--$password--`) and `POST /post/vote.json`.
- **`PixivAuthAdapter`**: Implements the Pixiv authentication seam (OAuth2 PKCE / session token) and prepares HTTP interceptors (`Authorization: Bearer`, `Referer: https://app-api.pixiv.net/`) for subsequent browsing.

---

## 2. User Scenarios & Testing

### User Story 1 - Manage Accounts in Account Center (Priority: P1)

As the owner, I navigate to the Account Manager Center from Settings. I see cards for supported platforms (yande.re, Pixiv). For yande.re, I can sign in with my username and password, test my credentials, view my connection state, and sign out at any time.

**Why this priority**: Core foundation. All authenticated capabilities (scoring, favorites, user feeds, OAuth2 token refresh) depend on reliable credential management and storage.

**Independent Test**:
1. Open Settings → Account Manager.
2. Sign in to yande.re with valid credentials → State updates to `Connected as <username>`.
3. Restart the app → Account state persists securely across process restarts.
4. Sign out → Credentials and tokens are permanently purged from secure storage.

**Acceptance Scenarios**:
1. **Given** no yande.re account is configured, **When** the owner opens Account Center, **Then** yande.re displays an "Unauthenticated" status with a "Sign In" action.
2. **Given** the owner submits their yande.re username and password, **When** credentials are valid, **Then** the app computes `SHA1("choujin-steiner--$password--")`, verifies the session with yande.re, stores credentials encrypted, and displays "Signed in as `<username>`".
3. **Given** invalid credentials or network failure during sign-in, **When** verification fails, **Then** the dialog displays an inline error message without dismissing or crashing, and passwords are not retained.
4. **Given** an authenticated yande.re account, **When** the owner taps "Sign Out", **Then** the account status resets to "Unauthenticated" and secure storage entries for that account are deleted.

---

### User Story 2 - Yande Personal Scoring & Favorites (Priority: P2)

As an authenticated yande.re user, I can rate posts from 0 to 3 stars in the post detail sheet. Giving a post 3 stars automatically favorites it. I can also quickly filter or browse my personal favorites in Explore.

**Why this priority**: Completes the core Yande workflow promised on the roadmap, turning passive inspection into personal curation.

**Independent Test**:
1. Open a post detail sheet while authenticated.
2. Select 3 stars → `POST /post/vote.json?id=<id>&score=3` succeeds, and favorite status reflects immediately in UI.
3. Open Explore and select "My Favorites" (or search `vote:3:<username>`) → Feed shows user's favorited posts.

**Acceptance Scenarios**:
1. **Given** an authenticated yande.re session, **When** the owner opens a post detail sheet, **Then** an interactive 0–3 star rating bar and Favorite icon button are displayed.
2. **Given** the owner taps 3 stars (or the Favorite button), **When** the vote request completes, **Then** the local UI reflects the updated score and shows a subtle confirmation.
3. **Given** an unauthenticated session, **When** the owner taps a rating star, **Then** Latte displays a prompt to sign in via the Account Manager.
4. **Given** the owner navigates to Explore, **When** signed in to yande.re, **Then** a "My Favorites" chip/shortcut is available that loads `vote:3:<username>`.

---

### User Story 3 - Pixiv Platform Foundation & Token Management (Priority: P3)

As the owner, I can configure a Pixiv account in the Account Center via OAuth2 PKCE or session token. Latte securely stores access/refresh tokens and automatically attaches required headers (`Authorization`, `Referer: https://app-api.pixiv.net/`) to outbound requests.

**Why this priority**: Establishes the multi-platform seam for milestone 0.0.3, ensuring Pixiv authentication and network prerequisites are verified before building Pixiv browse/detail feeds.

**Independent Test**:
1. Open Account Center → Pixiv card.
2. Provide Pixiv credentials / OAuth tokens.
3. Verify tokens are stored in `EncryptedSharedPreferences`.
4. Trigger an authenticated network probe → Confirms `Authorization: Bearer <token>` and `Referer` headers are correctly injected without HTTP 403.

**Acceptance Scenarios**:
1. **Given** the Account Center, **When** the owner selects the Pixiv card, **Then** Pixiv-specific authentication options (OAuth2 PKCE / token input) are available.
2. **Given** valid Pixiv tokens, **When** saved, **Then** `PixivAuthAdapter` manages token expiry and transparent token refresh via OkHttp interceptor.
3. **Given** an image or API request targeting Pixiv domains (`app-api.pixiv.net`, `*.pximg.net`), **When** executed by OkHttp or Coil, **Then** the network interceptor automatically injects `Referer: https://app-api.pixiv.net/`.

---

## 3. Functional Requirements

- **FR-001**: Latte MUST provide a dedicated "Account Manager" screen accessible from `SettingsScreen`.
- **FR-002**: The Account Manager MUST display platform status cards for `yande.re` and `pixiv`.
- **FR-003**: Credentials, passwords, and tokens MUST be stored in Android `EncryptedSharedPreferences` backed by Android Keystore. Plaintext storage is prohibited.
- **FR-004**: Yande password inputs MUST be hashed client-side using `SHA1("choujin-steiner--$password--")`. The raw password MUST NEVER be persisted or logged.
- **FR-005**: Yande sign-in MUST verify credentials against the remote API before saving the account.
- **FR-006**: The Account Manager MUST allow signing out of any platform, purging all corresponding credentials and cached session tokens.
- **FR-007**: When authenticated with yande.re, `DetailScreen` MUST render an interactive 0–3 star rating bar and Favorite toggle.
- **FR-008**: Rating actions MUST execute `POST /post/vote.json` with post ID and score, returning structured success/failure without blocking the detail pager.
- **FR-009**: When unauthenticated, tapping rating controls MUST prompt the user to sign in via the Account Manager.
- **FR-010**: Explore feed MUST provide a quick filter for personal favorites (`vote:3:<username>`) when a yande.re account is connected.
- **FR-011**: `PixivAuthAdapter` MUST manage OAuth2 access tokens, refresh tokens, and expiration timestamps.
- **FR-012**: An OkHttp network interceptor MUST inject `Referer: https://app-api.pixiv.net/` for all requests targeting `*.pximg.net` or `app-api.pixiv.net`.
- **FR-013**: Password entry fields MUST support visibility toggling (show/hide password) and proper IME keyboard actions (`Next` / `Done`).
- **FR-014**: All authentication network operations MUST handle timeout, transport offline, and invalid credentials gracefully with inline UI feedback.
- **FR-015**: Every red-green slice MUST pass unit/contract tests and maintain `make check` green.

---

## 4. Device-First Ergonomics & Verification (Principle VI)

In accordance with Constitution Principle VI:
1. **Early Hardware Slice**: Implement the `AccountManagerScreen` and Yande sign-in dialog as an early thin slice, deploy a debug APK to the OnePlus 8 (`0cadf428`), and verify:
   - Soft keyboard (IME) behavior and focus movement between username and password.
   - Password visibility toggle responsiveness.
   - Clear visual feedback during credential verification (loading spinner vs inline error text).
2. **Tactile UX Verification**:
   - Star rating bar touch target sizes (minimum 48x48 dp per star or thumb-friendly slider).
   - Favorite button toggle feedback (smooth icon animation or subtle haptic/color transition).
