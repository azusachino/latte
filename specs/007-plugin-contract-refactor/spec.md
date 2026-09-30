# Feature Specification: 007 Plugin Contract Refactor

**Feature Branch**: `refactor/plugin-contract`  
**Created**: 2026-10-01  
**Status**: Staged for execution (fresh session recommended -- see plan note)  
**Input**: [azusachino/latte#19](https://github.com/azusachino/latte/issues/19), prior art in `docs/research/2026-09-26-konachan-extension-review.md`

## Objective

Make `PlatformId` + `SitePlugin` the *only* platform knowledge in the codebase.
Adding a 4th platform (danbooru, gelbooru, ...) must require **zero** edits to
`ExploreViewModel`, `ExploreScreen`, or `ExploreUiState`: register a plugin,
get feeds, tabs, auth UI, search support, and cache policy for free.

## Target Design

### 1. Feed-state store (replaces split-brain fields)

```kotlin
// ExploreUiState
val feeds: Map<PlatformId, Map<PluginFeedKind, FeedState>> = emptyMap()

fun ExploreUiState.feed(platform: PlatformId, kind: PluginFeedKind): FeedState =
    feeds[platform]?.get(kind) ?: FeedState()

fun ExploreUiState.withFeed(
    platform: PlatformId,
    kind: PluginFeedKind,
    transform: (FeedState) -> FeedState,
): ExploreUiState
```

Deletes: `popularFeed`, `newestFeed`, `favoritesFeed`, `searchFeed`,
`pixivPopularFeed`, `pixivFollowedFeed`, `pixivFavoritesFeed`,
`pixivSearchFeed`, `pixivUserWorksFeed`.
`poolsFeed: PoolListState` stays (pools are a Moebooru-family surface rendered
outside the feed grid; revisit if a second pool source appears).

### 2. Generic search/author/suggestion state

```kotlin
val searchTags: Map<PlatformId, String> = emptyMap()      // replaces searchTags + pixivSearchTags
val searchSuggestions: Map<PlatformId, List<String>> = emptyMap()  // replaces yande/pixiv pair
val trendingTags: Map<PlatformId, List<String>> = emptyMap()
val authorId: Map<PlatformId, Long?> / authorName / authorFollowed / isTogglingAuthorFollow
```

### 3. Tab-driven pager

`ExploreScreen` builds pages from `activePlugin.feedTabs` (title, kind,
requiresAuthentication). Deletes `pixivKindForTab`, `selectedPixivKind`
index math, `supportsUserFeeds` branching, and hardcoded page indices.
Popular-period/date chips render when the tab's kind is POPULAR on a
plugin that declares a `POPULAR` capability variant (move the flag onto
`PluginFeedTab`, e.g. `supportsPeriodSelection: Boolean`).

### 4. Job + cache keys

`pixivLoadJobs: Map<PluginFeedKind, Job>` becomes
`feedJobs: Map<PlatformId, Map<PluginFeedKind, Job>>` (a platform switch must
not cancel another platform's in-flight load).
`pixivSearchCache` / `pixivUserWorksCache` / `yandeSearchCache` move onto the
feed-source contract (`PluginFeedSource` gains optional
`cachedState(query/authorId)`), or become
`Map<PlatformId, *>` stores in the ViewModel as an interim step.

### 5. Auth-UI capability flags

`SitePlugin` gains:

```kotlin
val supportsBrowserLogin: Boolean get() = AuthFlow.BROWSER in supportedAuthFlows
val supportsCookieImport: Boolean get() = authType == AuthType.CREDENTIALS
```

`PluginLoginDialog` drops `plugin as? PixivPlugin` / `as? MoebooruPlugin`
casts; browser login and cookie import become capability-driven.
`PlatformId` gains `accent: Color` (replaces `PIXIV_ACCENT` in the Screen).

## Execution Order (slices)

| Slice | Content | Verify |
| --- | --- | --- |
| 1 | Feed-state store + ViewModel/Screen port | compile + full test suite green |
| 2 | Generic search/author state + caches | compile + tests |
| 3 | Tab-driven pager | manual emulator sweep of every tab on all 3 platforms |
| 4 | Auth capability flags + accent | login/out per platform on emulator |
| 5 | Acceptance audit | see below |

## Acceptance Criteria

- `rg -i "pixiv|yande|konachan|moebooru" app/src/main/kotlin/com/azusachino/latte/ui/explore/` returns **zero** matches.
- `rg "as? PixivPlugin|as? MoebooruPlugin" app/src/main/kotlin/com/azusachino/latte/ui/` returns **zero** matches.
- New-platform test: adding a fixture `DanbooruPlugin` to `SitePluginManager` in a test compiles and renders tabs without shared-layer edits.
- No user-visible behavior change: feeds, tabs, auth, cache policy identical.
- `make check`, `make lint`, `make validate` green.

## Session Note

This refactor is deliberately staged for a **fresh session**: the transform
touches ~3000 lines across the two explore files, and the execution quality
depends on full context budget. The plan above is mechanical once slice 1
lands; do not attempt slices out of order.
