# Feature Specification: 002 Account Manager Center (Mihon-Style Plugin Architecture)

**Feature Branch**: `feat/002-account-manager`

**Created**: 2026-09-20

**Status**: Planned

**Inspiration**: Mihon / Tachiyomi `Tracker` & `TrackerManager` plugin architecture (`eu.kanade.tachiyomi.data.track.*`).

---

## 1. Architectural Model: Mihon-Style Site Plugins

Latte adopts the proven plugin architecture from Mihon's tracking subsystem. Instead of hardcoding site logins into centralized controllers or UI screens, each art platform is an encapsulated **`SitePlugin`** (or `PlatformAccount`) that owns its identity, authentication strategy, secure storage, and network hooks.

```
┌──────────────────────────────────────────────────────────┐
│             AccountManagerScreen / Settings              │
│        (Mihon TrackingPreferenceWidget style)            │
└────────────────────────────┬─────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────┐
│                    SitePluginManager                     │
│  • plugins = listOf(YandePlugin, PixivPlugin, ...)       │
│  • loggedInPluginsFlow()                                 │
│  • get(siteId)                                           │
└──────────────┬────────────────────────────┬──────────────┘
               │                            │
┌──────────────▼─────────────┐ ┌────────────▼──────────────┐
│        YandePlugin         │ │        PixivPlugin        │
│ • id = "yande.re"          │ │ • id = "pixiv"            │
│ • authType = CREDENTIALS   │ │ • authType = OAUTH2_PKCE  │
│ • login(username, pass)    │ │ • login(oauthCode/token)  │
│ • SHA-1 hash client-side   │ │ • token refresh & expiry  │
│ • capabilities:            │ │ • capabilities:           │
│   [SCORING, FAVORITES]     │ │   [REFERER_INJECT, FEED]  │
│ • score(postId, 0..3)      │ │ • applyHeaders(req, url)  │
└──────────────┬─────────────┘ └────────────┬──────────────┘
               │                            │
               └──────────────┬─────────────┘
                              │
┌─────────────────────────────▼────────────────────────────┐
│                  SecureAccountStorage                    │
│        (EncryptedSharedPreferences / Keystore)           │
└──────────────────────────────────────────────────────────┘
```

### Core Interfaces

```kotlin
enum class AuthType {
    CREDENTIALS, // Username + password (e.g. Yande, Danbooru)
    OAUTH2,      // OAuth2 PKCE / Token exchange (e.g. Pixiv)
    API_KEY      // Username + API Key
}

enum class PluginCapability {
    SCORING,          // 0-3 star rating
    FAVORITES,        // Add to / browse favorites
    REFERER_INJECT,   // Custom headers on media requests
    USER_FEED,        // Subscribed / Following artist feed
}

interface SitePlugin {
    val id: String
    val name: String
    val iconRes: Int?
    val authType: AuthType
    val capabilities: Set<PluginCapability>

    val isLoggedIn: Boolean
    val isLoggedInFlow: Flow<Boolean>
    fun getDisplayUsername(): String?

    suspend fun login(credentials: Map<String, String>)
    fun logout()

    // Capability hooks
    suspend fun setScore(postId: Long, score: Int): Result<Unit> = Result.failure(UnsupportedOperationException())
    fun applyHeaders(builder: Request.Builder, url: String) {}
}
```

---

## 2. User Scenarios & Testing

### User Story 1 - Browse & Manage Accounts (Mihon Tracking Style) (Priority: P1)

As the owner, I navigate to Settings → "Accounts" (or Account Manager).
I see a clean list of supported platform plugins:
- **yande.re**: Shows logo, title, and status ("Not connected" or "azusachino" with green checkmark).
- **Pixiv**: Shows logo, title, and status ("Not connected" or "Configured").

Tapping an unauthenticated plugin opens that plugin's specific login flow:
- Yande opens a `PluginLoginDialog` (Username + Password with visibility toggle).
- Pixiv opens the OAuth2 / token configuration flow.

Tapping an authenticated plugin opens a dialog showing profile details and a "Sign Out" button.

**Why this priority**: Core modular foundation. Mirrors Mihon's intuitive `SettingsTrackingScreen` and `TrackingPreferenceWidget`.

**Acceptance Scenarios**:
1. **Given** no accounts logged in, **When** opening Account Center, **Then** all plugins display an unauthenticated state with a "Sign In" action.
2. **Given** yande.re plugin selected, **When** submitting username and password, **Then** `YandePlugin` hashes the password with `SHA1("choujin-steiner--$password--")`, verifies against yande.re, persists in `EncryptedSharedPreferences`, and updates status to logged in.
3. **Given** an invalid password, **When** logging in, **Then** an inline error is displayed in the dialog without crashing or dismissing.
4. **Given** an authenticated plugin, **When** tapping "Sign Out", **Then** credentials are encrypted and deleted, and the UI immediately reflects the unauthenticated state.

---

### User Story 2 - Yande Personal Scoring via Plugin Seam (Priority: P2)

As an authenticated yande.re user, I can rate posts from 0 to 3 stars in the post detail sheet. Giving a post 3 stars automatically favorites it. I can also quickly filter or browse my personal favorites in Explore.

**Why this priority**: Directly exercises the `SCORING` and `FAVORITES` capabilities of `YandePlugin`.

**Acceptance Scenarios**:
1. **Given** `YandePlugin.isLoggedIn == true`, **When** viewing post detail, **Then** an interactive 0–3 star rating bar and Favorite icon button are displayed.
2. **Given** rating 3 stars or tapping Favorite, **When** `YandePlugin.setScore(postId, 3)` completes, **Then** the UI reflects the score with a subtle confirmation.
3. **Given** `YandePlugin.isLoggedIn == false`, **When** tapping rating stars, **Then** the app prompts to sign in to yande.re via the plugin login dialog.
4. **Given** signed in to yande.re, **When** on Explore screen, **Then** a "My Favorites" chip is available that queries `vote:3:<username>`.

---

### User Story 3 - Pixiv Platform Foundation & Header Hook (Priority: P3)

As the owner, I can configure a Pixiv account in Account Center. `PixivPlugin` stores tokens in `EncryptedSharedPreferences` and registers its `applyHeaders` hook to inject `Referer: https://app-api.pixiv.net/` and `Authorization: Bearer <token>` for Pixiv domains (`*.pximg.net`, `app-api.pixiv.net`).

**Why this priority**: Validates the multi-platform plugin architecture with a second real-world provider ahead of milestone 0.0.3.

**Acceptance Scenarios**:
1. **Given** Account Center, **When** selecting Pixiv, **Then** Pixiv-specific authentication (OAuth2 PKCE / token entry) is available.
2. **Given** Pixiv configured, **When** any request targeting `*.pximg.net` occurs, **Then** `PixivPlugin.applyHeaders` injects `Referer: https://app-api.pixiv.net/` to prevent HTTP 403 Forbidden.

---

## 3. Functional Requirements

- **FR-001**: Latte MUST implement a `SitePlugin` interface defining identity, auth type, lifecycle (`login`, `logout`), capabilities, and header hooks.
- **FR-002**: `SitePluginManager` MUST maintain a registry of available plugins and expose reactive login states (`isLoggedInFlow`, `loggedInPluginsFlow`).
- **FR-003**: The Account Center UI MUST mirror Mihon's `TrackingPreferenceWidget` style: icon, title, display username, and green checkmark badge when authenticated.
- **FR-004**: Credentials and tokens MUST be encrypted via `EncryptedSharedPreferences` backed by Android Keystore.
- **FR-005**: `YandePlugin` MUST hash passwords client-side using `SHA1("choujin-steiner--$password--")`; raw passwords MUST NOT be stored or logged.
- **FR-006**: `YandePlugin` MUST implement `SCORING` capability calling `POST /post/vote.json`.
- **FR-007**: Post detail UI MUST dynamically query `SitePluginManager.get(siteId)?.capabilities` to render scoring controls conditionally.
- **FR-008**: `PixivPlugin` MUST implement `REFERER_INJECT` capability injecting `Referer: https://app-api.pixiv.net/` on matching URLs.
- **FR-009**: Password dialogs MUST provide visibility toggles and proper IME keyboard actions (`Next` / `Done`).

---

## 4. Device-First Verification (Principle VI)

- **Early Hardware Slice**: Deploy `AccountManagerScreen` with `YandePlugin` and `PixivPlugin` cards to the OnePlus 8 (`0cadf428`).
- Verify tactile dialog interactions, keyboard focus, and login persistence across app termination.
