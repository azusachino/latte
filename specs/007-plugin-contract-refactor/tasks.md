# Tasks: 007 Plugin Contract Refactor

Branch: `refactor/plugin-contract` (cut from `main` after v0.2.3)

## Slice 1 -- feed-state store

- [x] T001 Add `feeds: Map<PlatformId, Map<PluginFeedKind, FeedState>>` to `ExploreUiState` with `feed()` / `withFeed()` helpers; delete the 9 named feed fields.
- [x] T002 Port every `updatePixivFeed` / moebooru feed mutation to `withFeed(platform, kind) { ... }`.
- [x] T003 Port every Screen read (`uiState.popularFeed` etc.) to `uiState.feed(uiState.platform, kind)`.
- [x] T004 Port `feedState(kind)` / `pixivFeed(state, kind)` helpers to the map form; delete both.
- [x] T005 `pixivLoadJobs` -> `feedJobs: Map<PlatformId, Map<PluginFeedKind, Job>>`; `cancelAllPixivJobs` -> `cancelFeedJobs(platform)`; platform switch must not cancel other platforms' jobs.
- [x] T006 Compile + `make check` green.

## Slice 2 -- generic search/author/suggestion state

- [x] T007 `searchTags: Map<PlatformId, String>`; delete `pixivSearchTags` usages via `searchTags[platform]`.
- [x] T008 `searchSuggestions` / `trendingTags` maps; delete `yandeSearchSuggestions` / `pixivSearchSuggestions` / `pixivTrendingTags`.
- [x] T009 Rename pixiv author fields to generic (`authorId`, `authorName`, `authorFollowed`, `isTogglingAuthorFollow`) keyed by platform.
- [x] T010 Move `pixivSearchCache` / `pixivUserWorksCache` / `yandeSearchCache` behind `Map<PlatformId, ...>`; clear path via `onPluginSignedOut(platform)`.
- [x] T011 Compile + `make check` green.

## Slice 3 -- tab-driven pager

- [x] T012 Add `supportsPeriodSelection: Boolean = false` to `PluginFeedTab`; set true on Moebooru POPULAR.
- [x] T013 Build pager pages from `activePlugin.feedTabs`; delete `pixivKindForTab` and hardcoded page indices.
- [x] T014 Popular period/date chips render only when the active tab declares `supportsPeriodSelection`.
- [x] T015 Emulator sweep: every tab on Yande / Konachan / Pixiv (logged in + out): loads, pagination, pull-refresh, safe-mode notice.

## Slice 4 -- auth capability flags + accent

- [x] T016 Add `supportsBrowserLogin` / `supportsCookieImport` to `SitePlugin`; rewire `PluginLoginDialog` without platform casts.
- [x] T017 Add `accent: Long` (color) to `PlatformId`; replace `PIXIV_ACCENT` usages.
- [x] T018 Emulator login/out sweep per platform.

## Slice 5 -- acceptance + release

- [x] T019 Acceptance greps (see spec) return zero matches; add a `DanbooruPlugin` fixture test proving shared layers need no edits.
- [x] T020 `make check` / `make lint` / `make validate` green.
- [x] T021 PR closes #19; changelog 0.2.4; tag release.
