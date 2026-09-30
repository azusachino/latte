# Latte Product & Architecture Roadmap

> **Status**: Active execution roadmap  
> **Updated**: 2026-09-27
> **Source Documents**:
>
> - [002 Active Feature Spec](../specs/002-account-manager/spec.md)
> - [004 Pixiv Feature Spec](../specs/004-pixiv-illustration-experience/spec.md)
> - [Spec Kit evaluation](spec-kit-evaluation.md)
> **Historical context**: [code review & parity analysis](review/2026-09-20-code-review-and-parity.md)

---

## Overview

Latte is evolving from a single-site anonymous reader (`0.0.1`) into an authenticated, multi-platform personal art workstation (`0.0.2`, `0.0.3`, `0.1.0`, `0.1.1`, `0.1.2`, and `0.1.3`). This roadmap establishes the sequence of milestones, architectural requirements, UX standards, and cache policies.

```text
0.0.1 ──> 0.0.2 ──> 0.0.3 ──> 0.1.0 ──> 0.1.1 ──> 0.1.2 ──> 0.1.3 (Delivered) ──> 0.2.0 (Active)
• Browse  • Auth    • Pixiv   • Fav tags • Proxy    • Batch   • Signing & Updates    • APK Diet (<12MB)
• Feed    • Scores  • OAuth2  • Following• Referer  • Insets  • In-App Updates       • DoH & Security
• Save    • Cache   • Feeds   • Konachan • Clearance• Reset   • MinSdk & ForceUpdate • Web SSO Auth
```

---

## Konachan

`konachan.net` (SFW mirror) ships in 0.1.0 as a third Moebooru platform: the
Yande plugin and Moebooru API were parameterized by site identity with no new
transport code. `konachan.com` stays on the roadmap behind its Cloudflare
wall: the plain OkHttp client receives a JS challenge there, and bypassing it
(webview challenge solving, as Boorusama does) is deliberate future work, not
a defect.

---

## Milestone 0.0.2: Account Manager Center (Mihon-Style Plugin Architecture), Scoring & UX Hardening

> **Status**: Released as `v0.0.2` on 2026-09-21. Plugin, Yande
> authentication/scoring, Favorites, and Pools are included; review blockers for
> storage, credential evidence, plugin lookup, query ownership, Safe Mode
> defaults, and inline action errors are repaired. Secure-storage/pool test
> coverage and T014 hardware verification remain partial. Pixiv and its final
> hardware acceptance are tracked in the 0.0.3 milestone below.
> **Active Feature Spec**: [002 Account Manager Center](../specs/002-account-manager/spec.md)
> **Tasks**: [002 Tasks](../specs/002-account-manager/tasks.md)
> **Design Inspiration**: Mihon / Tachiyomi `Tracker` & `TrackerManager` plugin architecture

### 1. Mihon-Style Plugin Subsystem (`SitePlugin` & `SitePluginManager`)

- **Plugin Architecture**:
  - Each platform is an encapsulated `SitePlugin` identified by `PlatformId`; the enum owns the external key, display/API URLs, and declared `PlatformCapability` set, while the plugin owns authentication, lifecycle, and header hooks.
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

> **Status**: Released as `v0.0.3` on 2026-09-21. Browser OAuth,
> Popular/Following/Favorites, search, platform switching, author works, image
> fallback, cache re-entry, Pixiv Safe Mode, bookmark toggle, download
> completion/duplicate prevention, multi-page back stack restoration, and
> largeHeap support are verified. Konachan remains postponed.
> **Active Feature Spec**: [004 Pixiv Illustration Experience](../specs/004-pixiv-illustration-experience/spec.md)
> **Tasks**: [004 Tasks](../specs/004-pixiv-illustration-experience/tasks.md)

### 1. Architectural Adjustments

- **Pixiv image proxy transport**:
  - Pixiv images on `i.pximg.net` return HTTP 403 Forbidden without a Pixiv
    Referer; use the supplied official medium/original URL first with
    `Referer: https://app-api.pixiv.net/`, then `i.pixiv.re` as a
    path-preserving fallback, and `pixiv.cat` ID/page resolution last.
  - Keep this policy in the shared OkHttp provider and media resolver rather
    than in Compose or account-management UI.
- **1:N Multi-Page Artworks (`IllustPage`)**:
  - Pixiv illusts can contain multiple pages (`page_count >= 1`).
  - Extend the Kotlin post/media model with a site-owned multi-page mapping
    before exposing it to the shared detail pager.
  - Keep horizontal detail swipe on neighboring feed works; expose explicit
    previous/next controls for pages within a multi-page Pixiv work.
- **Subscribed / Following Updates**:
  - Add a Pixiv-owned following query and expose a "Following" tab only when
    the selected plugin advertises the capability.

### 2. Pixiv Adapter & OAuth2 PKCE

- Implemented `PixivPlugin` as a `SitePlugin` and registered it after its
  deterministic and device acceptance receipts passed.
- Handle OAuth2 PKCE token exchange (`access_token` and `refresh_token`) and automated token refresh interceptor.
- The verified Popular surface uses the daily ranking operation. Additional
  Pixiv ranking modes remain a follow-up and are not advertised by 0.0.3.

---

## Milestone 0.1.0: Favorite Tags, Pixiv Following, and Konachan.net

> **Status**: Released as `v0.1.0` on 2026-09-26. Platform-aware favorite
> tags, detail-page long-press favoriting, Pixiv Following-first feeds with
> author follow/unfollow, and Konachan.net as a third Moebooru platform are
> verified. Common plugin feed abstractions (`PluginFeedSource`) and
> capability-based behavior replace platform identity branching.
> **Active Feature Spec**: [005 Favorite Tags & Following](../specs/005-favorite-tags-and-following/spec.md)
> **Tasks**: [005 Tasks](../specs/005-favorite-tags-and-following/tasks.md)

### 1. Favorite Tags & Search Experience

- **Favorite Tags**:
  - Saved tags bound to each platform (`yande.re`, `konachan.net`).
  - Add/remove tags from the image detail page, search bar star toggle, or long-pressing tag chips.
  - Dedicated `FavoriteTagsScreen` accessible from toolbar star, allowing direct feed navigation.
- **Search Enhancements**:
  - Autocomplete tag suggestions with post count badges from Moebooru API.
  - Recent searches persistence in Preferences DataStore.

### 2. Pixiv Following-First Experience

- **Following Feed & Author Management**:
  - Direct follow/unfollow toggle from the author works view, reflecting live follow status.
  - Pixiv platform defaults to opening on the Following tab.
  - Automatic cache invalidation and feed refresh upon follow/unfollow.

### 3. Konachan.net Moebooru Integration

- Parameterized Moebooru API and feed source to support `konachan.net` alongside `yande.re`.
- Preserved independent per-platform state, cookies, and search/suggestion caches.
- `konachan.com` remains deferred due to Cloudflare challenge.

---

## Milestone 0.1.1: Pixiv Multi-Image Download & Toast Offset

> **Status**: Released as `v0.1.1` on 2026-09-27. Multi-image proxy index alignment, toast clearance above the page swiper, and Pixiv CDN Referer headers for WorkManager saves.

### 1. Multi-Image Download Alignment

- Corrected `PixivCatResolver` URL construction for multi-page works (`pixiv.cat/<id>-<page-number>.<ext>` using 1-based page indices), resolving the 1-off mismatch where saving page 2 downloaded page 1.
- Ensured single-page illustrations continue without suffix (`pixiv.cat/<id>.<ext>`).
- Forwarded `Referer: https://app-api.pixiv.net/` through `DownloadManager` for official `pximg.net` media URLs.

### 2. UI Overlay Clearance

- Elevated `ToastHost` default bottom padding from 96dp to 144dp, ensuring download start and duplicate notices stay clear of the `DetailScreen` multi-image pagination bar and action pills.

---

## Milestone 0.1.2: Explore Multi-Select Batch Download & Feed Resilience

> **Status**: Released as `v0.1.2` on 2026-09-29. Multi-select batch download at the Explore screen across all platforms (Yande, Konachan, Pixiv) with a bottom-right floating action button, multi-page Pixiv first-page (`p0`) download default, 10-item selection cap, and resolution of stale Pixiv authentication feed blocking (#9).

### 1. Explore Multi-Select Batch Download

- Multi-select interaction across all platforms on the Explore screen: long-press to select, tap to toggle, with a 10-item cap.
- Visual selection state with 2.dp primary card border and top-right check badge.
- Floating bottom-right `ExtendedFloatingActionButton` with navigation bar padding for thumb reach and navigation bar clearance.
- Downloads first page (`p0`) only for multi-page Pixiv illustrations by default.

### 2. Pixiv Feed Authentication Resilience (#9)

- Clear stale authentication-required errors across all Pixiv feeds upon login so background feeds (such as Following) reload cleanly.
- Reset user-specific Pixiv feeds upon logout to avoid displaying stale session state.

---

## Milestone 0.1.3: Release APK Signing & In-App Update Engine

> **Status**: Released as `v0.1.3` on 2026-09-30. Dedicated release keystore signing pipeline, GitHub Releases in-app self-updater with download progress dialog, `FileProvider` package installer integration, minimum Android version (`minSdk`) compatibility protection, and mandatory update support (#11, #12).

### 1. Release Keystore & CI/CD Pipeline

- Configured `signingConfigs.release` in Gradle powered by environment variables with graceful fallback for local development.
- GitHub Actions release workflow decodes `LATTE_KEYSTORE_BASE64`, signs APK with dedicated release key (`CN=haru`), and generates SHA-256 checksums (`checksums.txt`).

### 2. In-App Self-Update Engine & Compatibility

- `UpdateService` querying GitHub Releases API with semantic version comparison.
- Streamed APK download with progress tracking and `FileProvider` installer integration.
- `minSdk` compatibility gate prevents downloading incompatible APKs on older Android OS levels.
- `minSupportedVersion` / mandatory update support prevents running broken clients on deprecated versions.

---

## Milestone 0.2.0: APK Diet, Security Hardening & Web-Based Seamless Auth

> **Status**: In planning / active roadmap milestone.

### 1. APK Size Optimization (DEX Diet & Tree-Shaking)

- **Target**: Reduce APK size from **47MB to <12MB** (>70% reduction).
- Enable R8 minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`).
- Author production `proguard-rules.pro` (keep rules for `kotlinx.serialization`, Coil, and OkHttp).
- Prune `material-icons-extended` dependency by migrating frequently used icons to local vector drawables.

### 2. Comprehensive Security Hardening

- Replace hardcoded fallback IP (`198.251.89.183`) in `customDns` with dynamic DNS-over-HTTPS (DoH) via `okhttp-dnsoverhttps` (Cloudflare / Google DoH) to prevent stale IP failures and bypass ISP DNS hijacking.
- Add `android:networkSecurityConfig` enforcing strict HTTPS and blocking cleartext traffic.
- Harden `EncryptedSharedPreferences` against Android Keystore hardware desynchronization and corruption crashes on OEM ROMs.
- Validate incoming deep-link OAuth intents in `MainActivity` against spoofed origins.

### 3. Web-Based Seamless Auth (Moebooru / Yande.re & Konachan)

- Replace manual password typing with an in-app WebView login flow supporting Android system Autofill (Google Password Manager, 1Password, Bitwarden) and biometrics.
- Intercept authenticated session cookies (`user_id`, `pass_hash`, `_moebooru_session`) upon successful web login and inject them into `PersistentCookieJar`.
- Provide an optional "Import Session Cookie / Pass-Hash" fallback for power users.
- Gracefully handle Cloudflare verification.

---

## Quality & Governance Gates

1. Every milestone must maintain the project gates: `make check`, `make lint`, and `make validate`; deterministic tests and lint must stay green.
2. New network capabilities (Yande vote, Pixiv OAuth2) must use isolated test doubles in the test suite and opt-in manual probes for live verification.
3. No credentials, tokens, or private media may ever be logged, committed, or exposed in error messages.
