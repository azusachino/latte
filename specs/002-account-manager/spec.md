# Feature Specification: 002 Account Manager Center (Mihon-Style Plugin Architecture)

**Feature Branch**: `feat/002-account-manager`

**Created**: 2026-09-20

**Status**: Released as `v0.0.2` with Slices 1-4 implemented and the review
blockers around encrypted storage, credential evidence, plugin lookup, query
ownership, Safe Mode defaults, and inline action errors repaired. Secure-storage
tests, T014 hardware verification, pool behavior coverage, and Slice 5 (Pixiv)
remain open as follow-up work -- see `tasks.md`.

**Inspiration**: Mihon / Tachiyomi `Tracker` & `TrackerManager` plugin architecture (`eu.kanade.tachiyomi.data.track.*`).

---

## 1. Architectural Model: Mihon-Style Site Plugins

Latte adopts the proven plugin architecture from Mihon's tracking subsystem. Instead of hardcoding site logins into centralized controllers or UI screens, each art platform is an encapsulated **`SitePlugin`** (or `PlatformAccount`) that owns its identity, authentication strategy, secure storage, and network hooks.

```text
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
4. **Given** signed in to yande.re, **When** on Explore screen, **Then** a dedicated "Favorites" tab (alongside Popular/Newest) is available, backed by its own feed querying `vote:3:<username>`.
5. **Given** a post was favorited in a prior session or on the web, **When** opening its detail, **Then** the heart/star reflects that existing favorite (recovered via `GET /favorite/list_users.json`, since yande.re exposes no other "my existing vote" lookup; 1/2-star ratings cannot be recovered this way).

---

### User Story 4 - Pool Browsing (Priority: P2, added post-hoc)

As the owner, I can browse and search yande.re pools (curated ordered post
collections) from a dedicated "Pools" tab, and open one to view its posts in
pool order. Not in the original spec for this milestone -- added directly
from live-device feedback, per this project's device-first-thin-slices
practice. The post-hoc addition is recorded in `tasks.md`.

**Acceptance Scenarios**:

1. **Given** the Pools tab, **When** it loads, **Then** it lists pools via `GET /pool.json`, each row showing a cover thumbnail (lazily fetched from the pool's first post), its id, post count, and a lock badge if private.
2. **Given** the Pools tab, **When** using the app bar's search action, **Then** it searches pools by name (`GET /pool.json?query=`) instead of post tags -- the same search entry point is contextual per tab, not a second search UI.
3. **Given** a pool row is tapped, **When** its posts load, **Then** they render via the existing post-search feed using `pool:<id>` as the tag (verified to return the same order as `pool/show.json`), with the app bar title showing `#<id> · <name>` (ellipsized on overflow) and a back arrow returning to the Pools tab.

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
- **FR-010**: Explore MUST expose Favorites as a dedicated tab with its own feed state, not a search-bar substitution.
- **FR-011**: Explore MUST expose a Pools tab (`GET /pool.json` list/search); opening a pool MUST reuse the existing post-search feed via `pool:<id>` rather than a parallel detail screen.

---

## 4. Device-First Verification (Principle VI)

- **Early Hardware Slice**: Deploy `AccountManagerScreen` with `YandePlugin` and `PixivPlugin` cards to the OnePlus 8 (`0cadf428`).
- Verify tactile dialog interactions, keyboard focus, and login persistence across app termination.
