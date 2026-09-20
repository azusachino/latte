# Latte Product & Architecture Roadmap

> **Status**: Active execution roadmap  
> **Updated**: 2026-09-21
> **Source Documents**:
>
> - [002 Active Feature Spec](../specs/002-account-manager/spec.md)
> - [Spec Kit evaluation](spec-kit-evaluation.md)
> **Historical context**: [code review & parity analysis](review/2026-09-20-code-review-and-parity.md)

---

## Overview

Latte is evolving from a single-site anonymous reader (`0.0.1`) into an authenticated, multi-platform personal art workstation (`0.0.2` and `0.0.3`). This roadmap establishes the sequence of milestones, architectural requirements, UX standards, and cache policies.

```text
0.0.1 (Delivered) ──> 0.0.2 (Auth, Scoring & UX Hardening) ──> 0.0.3 (Pixiv Multi-Platform)
  • Yande browse        • Yande login & password hash             • Pixiv OAuth2 PKCE
  • Masonry feed        • 0–3 scoring & Add to Favorite           • Referer header injection
  • Detail pager        • Search swipe-back & sheet drag fix      • 1:N multi-page illusts
  • WorkManager save    • Clean dialog for already-saved          • Subscribed updates feed
                        • Unified cache manager in settings       • Ranking modes
```

---

## Milestone 0.0.2: Account Manager Center (Mihon-Style Plugin Architecture), Scoring & UX Hardening

> **Status**: In progress. Plugin, Yande authentication/scoring, Favorites, and
> Pools have source implementations locally; review blockers for storage,
> credential evidence, plugin lookup, query ownership, Safe Mode defaults, and
> inline action errors are repaired. Secure-storage/pool test coverage and T014
> hardware verification are partial. Pixiv, final hardware acceptance, and the
> 0.0.2 version bump remain open.
> **Active Feature Spec**: [002 Account Manager Center](../specs/002-account-manager/spec.md)
> **Tasks**: [002 Tasks](../specs/002-account-manager/tasks.md)
> **Design Inspiration**: Mihon / Tachiyomi `Tracker` & `TrackerManager` plugin architecture

### 1. Mihon-Style Plugin Subsystem (`SitePlugin` & `SitePluginManager`)

- **Plugin Architecture**:
  - Each platform is an encapsulated `SitePlugin` implementing identity, `AuthType` (`CREDENTIALS`, `OAUTH2`), capabilities (`SCORING`, `FAVORITES`, `REFERER_INJECT`), login/logout lifecycle, and header hooks.
  - `SitePluginManager` maintains the plugin registry and exposes `loggedInPluginsFlow()`.
- **Mihon-Style UI (`AccountPreferenceWidget`)**:
  - In `SettingsScreen` (under "Accounts") or dedicated `AccountManagerScreen`.
  - Displays each platform with its logo, title, and display username with a green checkmark when logged in.
  - Tapping an unauthenticated plugin opens its specific login flow (`PluginLoginDialog` for Yande, OAuth2 for Pixiv).
  - Tapping an authenticated plugin opens a management dialog showing active capabilities and a "Sign Out" action.
- **Hardware-Backed Credential Security**:
  - `androidx.security:security-crypto` (`EncryptedSharedPreferences`) backed by Android Keystore.
  - Zero plaintext storage: yande.re stores `SHA1("choujin-steiner--$password--")`; Pixiv stores encrypted OAuth tokens.
  - One-tap sign-out permanently purges credentials and session state.

### 2. Yande.re Authentication & Scoring

- **Credential Verification**:
  - Verify credentials against yande.re before persisting account.
- **Personal Scoring & Favorites**:
  - Interactive 0–3 star rating bar and Favorite toggle (`score == 3`) in `DetailScreen`.
  - Executes `POST /post/vote.json` with optimistic UI updates.
  - A dedicated "Favorites" tab in `ExploreScreen` (alongside Popular/Newest)
    backed by its own feed, querying `vote:3:<username>`.
  - Favorite state set outside this app (a prior session, or the web) is
    recovered via `GET /favorite/list_users.json?id=<post>` -- yande.re has
    no "my existing vote" lookup, so only the favorite (score 3) tier is
    recoverable; 1/2-star ratings don't survive a restart.
- **Pool Browsing (delivered ahead of schedule)**:
  - A "Pools" tab lists/searches yande.re pools (`GET /pool.json`).
  - Opening a pool reuses the existing post-search feed via `pool:<id>` as a
    search tag (verified to match `pool/show.json`'s post order), rather
    than a parallel detail screen.
  - Not in the original 002 spec -- added directly from live-device
    feedback per this project's device-first-thin-slices practice; see
    `specs/002-account-manager/spec.md` User Story 4.

### 3. Pixiv Platform Foundation

- **OAuth2 Token Management**:
  - Token lifecycle management (access token, refresh token, expiry) in `PixivPlugin`.
- **Network Interceptor**:
  - Automatic injection of `Referer: https://app-api.pixiv.net/` and `Authorization: Bearer <token>` for `*.pximg.net` and `app-api.pixiv.net` domains.
  - Prepares the network layer for milestone 0.0.3 multi-platform browsing.

### 4. UX Hardening (Principle VI)

- Early thin-slice hardware verification on OnePlus 8 (`0cadf428`).
- Smooth dialog/IME interactions and quiet inline error states.

---

## Milestone 0.0.3: Pixiv Platform Support

### 1. Architectural Adjustments

- **Custom HTTP Headers for Media (`Referer`)**:
  - Pixiv images on `i.pximg.net` return HTTP 403 Forbidden without `Referer: https://app-api.pixiv.net/`.
  - Implement `PixivPlugin.applyHeaders` and route matching requests through
    `SitePluginManager` and the OkHttp provider.
- **1:N Multi-Page Artworks (`IllustPage`)**:
  - Pixiv illusts can contain multiple pages (`page_count >= 1`).
  - Extend the Kotlin post/media model with a site-owned multi-page mapping
    before exposing it to the shared detail pager.
  - Support navigating pages within a post in the detail pager.
- **Subscribed / Following Updates**:
  - Add a Pixiv-owned following query and expose a "Following" tab only when
    the selected plugin advertises the capability.

### 2. Pixiv Adapter & OAuth2 PKCE

- Implement `PixivPlugin` implementing `SitePlugin` and register it only after
  its deterministic and device acceptance receipts pass.
- Handle OAuth2 PKCE token exchange (`access_token` and `refresh_token`) and automated token refresh interceptor.
- Map Pixiv ranking modes (`day`, `week`, `month`, `rookie`, `r18`) to the
  shared post-query seam without adding Pixiv conditions to Compose screens.

---

## Quality & Governance Gates

1. Every milestone must maintain the project gates: `make check`, `make lint`, and `make validate`; deterministic tests and lint must stay green.
2. New network capabilities (Yande vote, Pixiv OAuth2) must use isolated test doubles in the test suite and opt-in manual probes for live verification.
3. No credentials, tokens, or private media may ever be logged, committed, or exposed in error messages.
