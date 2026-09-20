# Latte Product & Architecture Roadmap

> **Status**: Active execution roadmap  
> **Updated**: 2026-09-20  
> **Source Documents**:
> - [001 Active Feature Spec](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/specs/001-yandere-core-journey/spec.md)
> - [Code Review & Parity Analysis](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/docs/review/2026-09-20-code-review-and-parity.md)

---

## Overview

Latte is evolving from a single-site anonymous reader (`0.0.1`) into an authenticated, multi-platform personal art workstation (`0.0.2` and `0.0.3`). This roadmap establishes the sequence of milestones, architectural requirements, UX standards, and cache policies.

```
0.0.1 (Delivered) ──> 0.0.2 (Auth, Scoring & UX Hardening) ──> 0.0.3 (Pixiv Multi-Platform)
  • Yande browse        • Yande login & password hash             • Pixiv OAuth2 PKCE
  • Masonry feed        • 0–3 scoring & Add to Favorite           • Referer header injection
  • Detail pager        • Search swipe-back & sheet drag fix      • 1:N multi-page illusts
  • WorkManager save    • Clean dialog for already-saved          • Subscribed updates feed
                        • Unified cache manager in settings       • Ranking modes
```

---

## Milestone 0.0.2: Account Manager Center (yande.re & Pixiv Support), Scoring & UX Hardening

> **Active Feature Spec**: [002 Account Manager Center](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/specs/002-account-manager/spec.md)  
> **Tasks**: [002 Tasks](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/specs/002-account-manager/tasks.md)

### 1. Account Manager Center Subsystem
- **Dedicated Account Center UI**:
  - Expose "Account Manager" under an "Accounts" section in `SettingsScreen`.
  - Platform status cards for `yande.re` and `pixiv` displaying connection state (`Unauthenticated`, `Authenticated as <username>`, `Token Expired`).
  - Sign-in dialogs with password visibility toggle and IME action management.
- **Hardware-Backed Credential Security**:
  - Use `androidx.security:security-crypto` (`EncryptedSharedPreferences`) backed by Android Keystore.
  - Zero plaintext storage: yande.re stores `SHA1("choujin-steiner--$password--")`; Pixiv stores encrypted OAuth tokens.
  - One-tap sign-out permanently purges credentials and session state.

### 2. Yande.re Authentication & Scoring
- **Credential Verification**:
  - Verify credentials against yande.re before persisting account.
- **Personal Scoring & Favorites**:
  - Interactive 0–3 star rating bar and Favorite toggle (`score == 3`) in `DetailScreen`.
  - Executes `POST /post/vote.json` with optimistic UI updates.
  - Quick "My Favorites" filter in `ExploreScreen` querying `vote:3:<username>`.

### 3. Pixiv Platform Foundation
- **OAuth2 Token Management**:
  - Token lifecycle management (access token, refresh token, expiry) in `PixivAuthAdapter`.
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
  - Add `Map<String, String> headers` to `ResolvedMedia`.
  - Forward headers to `ExtendedImage.network(..., headers: media.headers)`.
  - Forward headers via `MethodChannel` (`saveImage`) to `DownloadWorker.kt` for `HttpURLConnection.setRequestProperty`.
- **1:N Multi-Page Artworks (`IllustPage`)**:
  - Pixiv illusts can contain multiple pages (`page_count >= 1`).
  - Refactor `PostDetail` from a flat `List<MediaVariant>` to `List<IllustPage>`.
  - Support navigating pages within a post in the detail pager.
- **Subscribed / Following Updates**:
  - Add `PostQuerySource.subscribed` to `PostQuery`.
  - Fetch followed artists' newest works via Pixiv App API `/v2/illust/follow`.
  - Add a "Following" tab to Explore when the selected adapter supports it.

### 2. Pixiv Adapter & OAuth2 PKCE
- Implement `PixivAdapter` implementing `SiteAdapter`.
- Handle OAuth2 PKCE token exchange (`access_token` and `refresh_token`) and automated token refresh interceptor.
- Map Pixiv ranking modes (`day`, `week`, `month`, `rookie`, `r18`) to `PopularQuery`.

---

## Quality & Governance Gates

1. Every milestone must maintain `make check` passing with zero lints, formatting compliance, and deterministic tests.
2. New network capabilities (Yande vote, Pixiv OAuth2) must use isolated test doubles in the test suite and opt-in manual probes for live verification.
3. No credentials, tokens, or private media may ever be logged, committed, or exposed in error messages.
