# Implementation Plan: Favorite tags, following, and local filtering

**Spec**: [spec.md](spec.md)

Two independent tracks (Pixiv social, local tags/filtering) plus one shared
persistence task. Each task lands with its tests and leaves `make check` green.

## Track A — Pixiv following

1. `PixivFeedContract.kt`: add `PixivUserDetailResult` (Success with
   `isFollowed: Boolean`, AuthRequired, RateLimited, UpstreamDrift,
   TransportFailure).
2. `PixivApi.kt`:
   - `followAuthor(userId, follow)`: `PixivBookmarkResult`, POST form
     `user_id` (+ `restrict=public` on add) to `v1/user/follow/add` or
     `v1/user/follow/delete`; guard `userId <= 0` and blank token exactly like
     `bookmark()`.
   - `userDetail(userId)`: GET `v1/user/detail` decoding `user.is_followed`.
   - Private DTO `PixivUserDetailResponse(user: PixivUserDetailDto(is_followed))`.
3. `Platform.kt`: add `FOLLOW_AUTHORS` capability to PIXIV only.
4. `ExploreViewModel`: `pixivFollowedByAuthor: Map<Long, Boolean>` state;
   `loadPixivFollowState(userId)` on entering user works; `togglePixivFollow()`
   calls the API, updates state on success, clears the followed-feed cache and
   reloads the Following feed if it has content.
5. `ExploreScreen`: Follow/Following button in
   the user-works header beside `pixivAuthorName`; failure toast through
   `ToastHost`.
6. Default tab: `selectPlatform(PIXIV)` sets `selectedTab = 0` (and initial
   restore when the platform is Pixiv) and ensures the followed feed loads.

## Track B — Local tags and history

1. Migrate `LattePreferences` from SharedPreferences to Preferences DataStore
   (`datastore-preferences` dependency): add the `dataStore` delegate on
   `Context`, read once with a one-time migration of the existing
   `latte_prefs` keys (`columnCount`, `themeMode`, `safeMode`), keep the public
   `StateFlow` + setter surface so callers do not change. DataStore writes are
   suspend/`edit {}` — collect the flow into a `MutableStateFlow` internally as
   today.
2. Add platform-aware `favoriteTags` and `recentSearches` as JSON-encoded string
   keys (history bound to 20), with mutators and codec round-trip tests.
3. History recording: `ExploreViewModel.search()` (yande branch) records the
   trimmed query on success.
4. Moebooru suggestions: `MoebooruApi.getTagSuggestions(prefix)` via
   `/tag.json?name=<prefix>*&order=count&limit=8`, mapped to tag names;
   `ExploreViewModel` caches per platform and prefix; blank prefix shows favorites +
   recents instead.
5. Search bar UI (`ExploreScreen`): when yande and expanded — blank query shows
   favorites then recents as chips; non-blank query shows suggestions and a
   star toggle for favoriting the active query.
6. Keep one `filterPosts(posts, safeMode)` seam. Send `rating:safe` with
   Moebooru feed requests and filter Pixiv results locally.

The tag blacklist was descoped after owner review.

## Deferred follow-ups (file as issues after merge)

- Bulk download for pools and user works.
- Backup/export of local prefs (favorites, history, blacklist).
- Tag collections (grouped queries).
