---

description: "Implementation tasks for favorite tags, following, and feed defaults"
---

# Tasks: Favorite tags, following, and feed defaults

**Input**: [spec.md](spec.md) and [plan.md](plan.md)

**Tests**: Required by the Latte constitution. Each behavior task starts with
a failing public-seam test and leaves `make check` green.

## Phase 1: Fixtures and seams

- [x] T001 Follow-state fixtures inlined in `PixivApiTest` /
  `PixivFollowRegressionTest` (redacted JSON bodies for user detail and follow
  add/delete)
- [x] T002 Yande `/tag.json` suggestion bodies inlined in
  `YandeTagSuggestionTest`
- [x] T003 Define `PixivUserDetailResult` in `PixivFeedContract.kt` and the
  neutral `PluginFeed*` contract in `plugin/PluginFeed.kt`

## Phase 2: Track A — Pixiv following (Stories 1, 2)

- [x] T004 `PixivApi.followAuthor(userId, follow)` posting
  `v1/user/follow/add` / `v1/user/follow/delete`, mirroring `bookmark()`
- [x] T005 `PixivApi.userDetail(userId)` decoding `user.is_followed`
- [x] T006 `FOLLOW_AUTHORS` capability on the Pixiv platform entry;
  `setAuthorFollowed`/`isAuthorFollowed` on the `SitePlugin` seam
- [x] T007 ViewModel: follow-state load/toggle with followed-feed cache
  invalidation
- [x] T008 UI: Follow/Following toggle in the user-works header with failure
  toast
- [x] T009 Default to the Following tab on Pixiv selection and initial
  configure

## Phase 3: Track B — local state (Stories 3, 4)

- [x] T010 Migrate `LattePreferences` to Preferences DataStore with one-time
  `latte_prefs` key migration; public surface unchanged
- [x] T011 Favorite tags and ordered recent searches (bound 20) with codec
  round-trip tests
- [x] T012 Record successful yande searches into recent searches
- [x] T013 `YandeApi.getTagSuggestions(prefix)` from `/tag.json`; parsing test
- [x] T014 ViewModel suggestion cache per prefix; blank-prefix favorites/recents
  exposure
- [x] T015 Search bar UI: chips rows (favorites, recents, suggestions), the
  star favorite toggle, and long-press chip favoriting
- [x] T016 Favorite Tags view (`Screen.FavoriteTags`) reached from the yande
  toolbar star; row tap opens the tag's feed, row remove unfavorites

## Phase 4: Regression protection

- [x] T017 `PixivFollowRegressionTest`: plugin-seam follow loop (wire format,
  form bodies, unknown-vs-unfollowed, signed-out refusal, neutral feed
  mapping)
- [x] T018 `LattePreferenceCodecsTest` and the shared safe-mode filter seam
  stay covered

## Descoped after owner review

The tag blacklist (originally T015–T018) was removed from this feature. The
shared `filterPosts` seam remains in place for a future issue.

## Phase 5: Device acceptance (OnePlus 8, `IN2010`)

- [x] T019 Install debug build; verify chips, star toggle, Favorite Tags view,
  and detail-page long-press favoriting (owner device pass 2026-09-26;
  restart persistence re-verify pending after the dev-build storage reset)
- [x] T020 Live Pixiv: follow/unfollow an author from the works view; confirm
  the Following feed updates and Pixiv opens on Following (owner-confirmed
  after the pager-snap race fix, commit 274381e)

## Phase 6: Closeout

- [ ] T021 `make check`, `make lint`, `make validate` green; CHANGELOG entry;
  PR linked to issue #5
- [ ] T022 File deferred follow-up issues (bulk download, backup/export, tag
  collections, optional tag blacklist)
