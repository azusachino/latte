# Latte Code Review, UX Parity & Multi-Platform Evolution Analysis

> **Target Project**: [Latte](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte) (`vendor/latte`)  
> **Reference Project**: [Moebooru Android](file:///Users/azusachino/Projects/project-github/harus-workstation/refs/image-gallery-apps/moebooru) (`refs/image-gallery-apps/moebooru`)  
> **Date**: September 2026  
> **Author**: Antigravity  

> **STALE (2026-09-20)**: This analysis targets the pre-rewrite Flutter/Dart
> implementation (`pubspec.yaml`, `android/app/.../DownloadWorker.kt`). Latte
> was rewritten to native Kotlin/Jetpack Compose in `52fddb5` after this doc
> was written, so its concrete file paths, dependency list, and test counts
> no longer apply. Kept for the UX-parity findings and spec-kit practice
> assessment, which may still be conceptually relevant. All concrete claims
> below are historical; verify against the current `app/src/main/kotlin/` tree
> before relying on any specific claim.

---

## 1. Executive Summary & Code Review Findings

Latte is a clean, disciplined rewrite of the native Android [Moebooru](file:///Users/azusachino/Projects/project-github/harus-workstation/refs/image-gallery-apps/moebooru) client into Dart 3 / Flutter with Material 3 styling. It concurrently evaluates GitHub **Spec Kit** (`spec-kit`) as an agentic delivery methodology.

### 1.1 Quality & Verification Status
- **Zero Lint / Zero Format Violations**: `make check` executes `dart format`, `flutter analyze`, and `flutter test` cleanly.
- **100% Test Pass Rate**: 70 unit, widget, contract, and golden tests pass in ~3 seconds, along with the isolated feasibility probe (`yandere_probe_test.dart`).
- **Minimalist Dependency Footprint**: Only three runtime dependencies in [`pubspec.yaml`](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/pubspec.yaml) (`http: ^1.5.0`, `extended_image: ^10.1.0`, `shared_preferences: ^2.5.5`).
- **Production-Grade Background Downloads**: Avoids fragile Flutter background plugins by implementing native Android Jetpack WorkManager in [`DownloadWorker.kt`](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/android/app/src/main/kotlin/com/azusachino/latte/DownloadWorker.kt) with scoped MediaStore (`Pictures/Latte`), HTTP range resumption (`Range: bytes=$offset-`), and atomic publication.

### 1.2 Spec Kit Practice Assessment
- **Pre-Code Defect Detection**: Identified 8 material architectural defects prior to writing code (e.g., distinguishing transport failure vs. offline, scoped storage Content URI modeling, save cancellation feasibility, explicit-content policy consensus).
- **Seeded Inconsistency Check**: Successfully detected an intentional contradiction between deferred search history (FR-018) and task items during `$speckit-analyze`.
- **Traceability**: 100% mapping from functional requirements (FR-001–FR-026) to execution tasks (T001–T048).

---

## 2. UX Parity: Native Moebooru vs. Latte (0.0.1)

Below is an itemized comparison between the reference native application ([`yueeng/moebooru`](file:///Users/azusachino/Projects/project-github/harus-workstation/refs/image-gallery-apps/moebooru)) and the current Latte implementation:

| UX Area | Native Moebooru ([`refs/.../moebooru`](file:///Users/azusachino/Projects/project-github/harus-workstation/refs/image-gallery-apps/moebooru)) | Latte 0.0.1 ([`vendor/latte`](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte)) | Parity Assessment & Delta |
| :--- | :--- | :--- | :--- |
| **App Architecture** | Multi-Activity (`MainActivity`, `PopularActivity`, `PreviewActivity`, `QueryActivity`, `SavedActivity`, `UserActivity`) | Single unified shell ([`LatteApp`](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/lib/src/app.dart)) with in-place animated switcher | **Latte Advantage**: Smoother transitions, zero Activity lifecycle overhead, stable in-memory feed preservation across back navigation. |
| **Feed Presentation** | StaggeredGridLayoutManager in `RecyclerView` | Greedy aspect-ratio masonry layout (`_masonryColumns`) in a single `ListView` | **Equal Parity**: Both achieve dense, aspect-preserving masonry. Latte adds user-configurable column density (auto/2/3/4) in Settings. |
| **Popular Discovery** | `PopularActivity` with horizontal `ViewPager2` of days + `MaterialDatePicker` dialog | `ExploreScreen` with `Popular` / `Newest` tabs + bottom sheet period selector (Day / Week / Month + date stepper) | **Latte Advantage**: More immediate period switching (Day, Week, Month) via bottom sheet without full-screen date picker modals. |
| **Tag Search** | Dedicated `QueryActivity` with search history and tag suggestion chips | Material 3 `SearchAnchor` / `SearchBar` with clear/back restoration | **Partial Parity**: Latte supports opaque query expressions, negative tags, and meta-tags. Moebooru includes historical search persistence and autocompletion chips (deferred in Latte 0.0.1). |
| **Detail & Pager** | `PreviewActivity` with `GestureImageView` and `ViewPager2` | `ExtendedImageGesturePageView` with 2-finger zoom and 1-finger swipe navigation | **Equal Parity**: Both support high-res zoom and horizontal post-to-post paging. Latte adds accessible previous/next arrow buttons and bounded adjacent prefetching. |
| **Post Metadata** | Bottom sheet with tag types (Artist, Copyright, Character, General), dimensions, file size, rating, source | Bottom sheet with Dimensions, Score, Source, and clickable colored tag chips | **Minor Gap**: Latte does not yet categorize tags by type (Artist, Character, etc.) in the UI chips, though wire types exist. |
| **Content Policy** | Safe Mode toggle in settings; rewrites queries to inject `rating:safe` | **No censorship or query rewrite**; all ratings returned as metadata | **Owner Choice**: Latte intentionally exposes all ratings without filtering, adhering to the owner's explicit product directive. |
| **Downloads / Save** | In-app download with quality choice (JPEG vs Original) and custom directory | Native WorkManager background download directly to `Pictures/Latte` (best quality variant) | **Equal / Modernized**: Latte simplifies save to "best quality" into public MediaStore with system notification progress and duplicate protection. |
| **Authentication & Profile** | `UserActivity`: Login, registration, password reset, user profile, uploaded posts | **Absent** in 0.0.1 | **Gap**: Latte milestone 0.0.1 is strictly anonymous. |
| **Scoring & Favorites** | `StarActivity`: 0–3 star rating bar, favorite indicator, list of voters | Read-only aggregate score display | **Gap**: Latte deferred authenticated personal scoring to milestone 0.0.2. |
| **Pools & Collections** | Dedicated Pool browsing and chapter reading | **Absent** in 0.0.1 | **Gap**: Deferred. |
| **Comments & Similar** | Post comments list; IQDB similar image search | **Absent** in 0.0.1 | **Gap**: Deferred. |

---

## 3. Next Feature: Yande.re Login & Scoring Subsystem (Add to Favorite)

### 3.1 Understanding Yande.re (Moebooru) Auth & Scoring Mechanics

In the reference Moebooru implementation:
1. **Authentication API**:
   - Web Authentication: `POST /user/authenticate` with `user[name]`, `user[password]`, and `authenticity_token` (CSRF token parsed from `/user/login`). Returns session cookies `user_id` and `pass_hash`.
   - API Authentication: Moebooru supports password hash queries: `password_hash = SHA1("choujin-steiner--$password--")`. Requests pass `login={username}&password_hash={hash}` or `api_key={apiKey}`.
2. **Scoring & Favorite API**:
   - `POST /post/vote.json` with parameters:
     - `id`: post remote ID.
     - `score`: integer from `0` to `3`:
       - `0`: Remove vote / clear favorite.
       - `1`: Good / 1 star.
       - `2`: Great / 2 stars.
       - `3`: **Favorite** / 3 stars (adds post to the user's public favorites list).
   - Response (`ItemScore`): Returns the updated post, vote breakdown (`voted_by`), and the current user's vote: `vote: Int`.
   - Favorites Query: A user's favorites can be queried using standard post search: `vote:3:{username}` or `order:vote`.

### 3.2 Architectural Readiness in Latte

Latte already prepared foundational seams in [`site_capabilities.dart`](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/lib/src/sites/site_capabilities.dart):
```dart
abstract interface class AuthenticationCapability {
  Future<void> signIn();
  Future<void> signOut();
}

abstract interface class PersonalScoreCapability {
  Future<int?> score(PostRef reference);
  Future<void> setScore(PostRef reference, int score);
}

abstract interface class RemoteFavoriteCapability {
  Future<bool> isFavorite(PostRef reference);
  Future<void> setFavorite(PostRef reference, bool favorite);
}
```

### 3.3 Implementation Blueprint for Latte

1. **Credential & Session Storage**:
   - Do **not** store plaintext passwords in `SharedPreferences`.
   - Use `flutter_secure_storage` or an app-owned encrypted keystore for `username` and `password_hash`.
   - Create an `AuthSession` domain entity:
     ```dart
     class AuthSession {
       final String username;
       final String passwordHash;
       final int userId;
     }
     ```
2. **YandeAdapter Implementation**:
   - Implement `AuthenticationCapability` and `PersonalScoreCapability` in `YandeAdapter`.
   - In `YandeAdapter._get` and new `_post`, append authentication headers or query parameters (`login` and `password_hash`) when an active session exists.
   - `setScore(reference, score)`: Call `POST /post/vote.json` with `id=${reference.remoteId}&score=$score`.
   - In `getPost(reference)`: When authenticated, extract the current user's personal vote if present in the response.
3. **UI & Presentation Integration**:
   - In `SettingsScreen`: Add an "Account" section showing sign-in status, username, and login/logout dialog.
   - In `_DetailInspectSheet`:
     - If `adapter.capabilities.personalScore != null`, render an interactive 3-star rating bar.
     - Add a dedicated **Favorite** toggle button (star or heart icon) corresponding to `score == 3`.
     - Tapping the favorite button when unauthenticated triggers the sign-in prompt.
   - In `ExploreController`: Add support for browsing the user's favorites via `PostQuery.tagSearch('vote:3:$username')`.

---

## 4. Second Platform Support: Pixiv

Adding Pixiv as a second platform is a major architectural milestone. Pixiv differs fundamentally from Moebooru. Below is the technical breakdown of the gaps and how Latte should evolve to accommodate it.

### 4.1 Pixiv Platform Specifics & Differences

| Technical Aspect | Moebooru / Yande.re | Pixiv (App-API) | Required Latte Adaptation |
| :--- | :--- | :--- | :--- |
| **Authentication** | Basic hash / cookie auth (`user[name]`, `pass_hash`) | OAuth2 with PKCE (`https://oauth.secure.pixiv.net/auth/token`) yielding short-lived `access_token` and long-lived `refresh_token` | Token refresh interceptor in `PixivAdapter`. Secure storage for `refresh_token`. |
| **Hotlink Protection** | Direct HTTPS download, standard User-Agent | Strict `Referer` validation (`Referer: https://app-api.pixiv.net/` or `https://www.pixiv.net/`) on `i.pximg.net`; returns **HTTP 403** without it | **Critical**: `ResolvedMedia` and `DownloadWorker.kt` must support custom HTTP headers. |
| **Post Structure** | **1 post = 1 image** with variants (`preview`, `sample`, `jpeg`, `original`) | **1 illust = 1..N pages** (`page_count >= 1`). Manga and multi-image sets contain multiple distinct artworks under one ID | **Critical**: Evolve `PostDetail` from a single-image model to a multi-page/multi-artwork structure. |
| **Discovery Modes** | Popular (Day, Week, Month), Newest | Ranking (Day, Week, Month, Rookie, Original, R-18), **Subscribed / Following Updates** (`/v2/illust/follow`) | Add `PostQuerySource.subscribed` to `PostQuery` and a discovery tab for following updates. |
| **Animated Art** | Static images (JPEG, PNG) | Static images + **Ugoira** (ZIP archive of frame images + frame delay metadata) | Deferred initially; static fallback or dedicated ugoira player. |

### 4.2 Critical Architectural Refactors Needed in Latte

#### A. Custom Headers for Image Loading & Native Downloads
Currently:
- `_DetailZoomArtwork` uses `ExtendedImage.network(source.toString())` without custom headers.
- `DownloadWorker.kt` sets `User-Agent: Latte/1.0`, but has no parameter for additional headers.

**Required Change**:
1. Update `ResolvedMedia` in [`site_adapter.dart`](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/lib/src/sites/site_adapter.dart):
   ```dart
   class ResolvedMedia {
     const ResolvedMedia({
       required this.reference,
       required this.variant,
       required this.source,
       this.headers = const {},
     });

     final PostRef reference;
     final MediaVariant variant;
     final Uri source;
     final Map<String, String> headers;
   }
   ```
2. Pass `headers` into `_imageProvider` / `ExtendedImage.network(..., headers: media.headers)`.
3. Pass `headers` through `MethodChannel` (`saveImage`) to `DownloadWorker.kt`, applying them to `HttpURLConnection.setRequestProperty(key, value)`.

#### B. Multi-Page Illust Support (1:N)
Currently, `PostDetail` in [`post.dart`](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/lib/src/domain/post.dart) models a flat list of `MediaVariant`:
```dart
class PostDetail {
  final PostSummary summary;
  final List<MediaVariant> media; // Assumes variants of ONE image
  ...
}
```
For Pixiv, an illust with `page_count: 5` has 5 pages, each with its own `medium`, `large`, and `original` variants.

**Recommended Evolution**:
```dart
class IllustPage {
  const IllustPage({
    required this.pageIndex,
    required this.variants,
  });

  final int pageIndex;
  final List<MediaVariant> variants;
}

class PostDetail {
  const PostDetail({
    required this.summary,
    required this.pages, // Replaces flat media list
    ...
  });

  final PostSummary summary;
  final List<IllustPage> pages;
}
```
- For Yande / Moebooru: `pages` is simply a 1-element list `[IllustPage(pageIndex: 0, variants: media)]`.
- For Pixiv: `pages` contains all pages in the set.
- Detail pager can navigate between pages within a post, or page through multi-image posts smoothly.

#### C. Subscribed / Followed Updates Query
Expand `PostQuery` in [`post.dart`](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/lib/src/domain/post.dart):
```dart
enum PostQuerySource { discovery, popular, tagSearch, subscribed }

class PostQuery {
  ...
  const PostQuery.subscribed({this.continuation})
    : source = PostQuerySource.subscribed,
      expression = null,
      popularQuery = null;
}
```
When `adapter.descriptor.id == SiteId('pixiv')`, the Explore screen displays tabs: `[Popular, Newest, Following]`.

---

## 5. Actionable Roadmap & Milestones

```
Milestone 0.0.1 (Done)
└── Yande Core Journey: Popular browse → Masonry feed → Detail pager → WorkManager download

Milestone 0.0.2 (Next: Yande Auth & Scoring)
├── Token / password_hash secure storage
├── YandeAdapter implementation of AuthenticationCapability & PersonalScoreCapability
├── DetailInspectSheet: 3-star rating bar & Add-to-Favorite toggle
└── Explore favorites query ('vote:3:username')

Milestone 0.0.3 (Pixiv Foundation & Core Journey)
├── Architectural Seam Refactor:
│   ├── Multi-page PostDetail (IllustPage)
│   ├── ResolvedMedia headers (Referer injection)
│   └── DownloadWorker header forwarding
├── Pixiv OAuth2 PKCE login & token refresh
├── PixivAdapter: Ranking (Day/Week/Month), Illust detail, Media resolution
└── Subscribed updates feed (PostQuerySource.subscribed)
```

---

## 6. Project Local File Notice
This review document is committed locally to the project at:  
[`vendor/latte/docs/review/2026-09-20-code-review-and-parity.md`](file:///Users/azusachino/Projects/project-github/harus-workstation/vendor/latte/docs/review/2026-09-20-code-review-and-parity.md).
