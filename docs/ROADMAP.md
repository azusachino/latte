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

## Milestone 0.0.2: Yande Authentication, Scoring Subsystem & UX Hardening

### 1. UX Hardening & Interaction Fixes
- **Search Result Swipe-to-Back**:
  - Enable horizontal swipe-right in `_ExploreTabScaffold` to trigger `controller.clearSearch()`, returning to the prior discovery/popular position.
  - Add explicit `leading: BackButton` to the search app bar.
- **Detail Inspect Sheet Dragging Fix**:
  - Add an explicit Material 3 drag handle pill (`showDragHandle` / top grab area) to `_DetailInspectSheet`.
  - Increase collapsed peek height from 66px to 88px so FAB buttons do not consume all draggable surface area.
- **Download Feedback & Confirmation Overhaul**:
  - Replace the floating SnackBar (`bottom + 176`) with a clean Material 3 confirmation `AlertDialog` (`showDialog`) for "Already saved. Download again?".
  - Remove redundant native Android Toasts from `MainActivity.kt`; let native system notifications own progress.

### 2. Cache Manager Subsystem
- **Unified Disk Cache**:
  - Standardize feed thumbnail rendering in `_RemoteArtwork` to use `ExtendedImage.network(..., cache: true)`.
  - Enables offline thumbnail persistence across app restarts.
- **Cache Management in Settings**:
  - Expose a "Storage & Cache" section in `SettingsScreen`.
  - Display real-time cached image size via `getCachedSizeBytes()`.
  - Provide a "Clear image cache" action invoking `clearDiskCachedImages()` and `imageCache.clear()`.

### 3. Yande.re Authentication & Scoring
- **Credential Storage**:
  - Implement secure storage for `username` and `password_hash = SHA1("choujin-steiner--$password--")`.
  - Never store credentials in plaintext `SharedPreferences`.
- **Capability Implementation**:
  - Implement `AuthenticationCapability` (`signIn`, `signOut`) in `YandeAdapter`.
  - Implement `PersonalScoreCapability` (`score(PostRef)`, `setScore(PostRef, int)`) calling `POST /post/vote.json`.
  - Add interactive 0–3 star rating bar and Favorite toggle button (`score == 3`) in `_DetailInspectSheet`.
  - Add `PostQuery.tagSearch('vote:3:$username')` to browse personal favorites.

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
