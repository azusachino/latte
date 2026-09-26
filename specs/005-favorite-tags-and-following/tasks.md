---

description: "Implementation tasks for favorite tags, following, and local filtering"
---

# Tasks: Favorite tags, following, and local filtering

**Input**: [spec.md](spec.md) and [plan.md](plan.md)

**Tests**: Required by the Latte constitution. Each behavior task starts with
a failing public-seam test and leaves `make check` green.

## Phase 1: Fixtures and seams

- [ ] T001 Add redacted Pixiv fixtures for user detail, follow add, and follow
  delete outcomes in `app/src/test/resources/pixiv/`
- [ ] T002 Add a yande `/tag.json` suggestion fixture in
  `app/src/test/resources/`
- [x] T003 Define `PixivUserDetailResult` in `PixivFeedContract.kt`

## Phase 2: Track A — Pixiv following (Story 1, 2)

- [x] T004 [P] Add `PixivApi.followAuthor(userId, follow)` posting
  `v1/user/follow/add` / `v1/user/follow/delete`, mirroring `bookmark()`
- [x] T005 [P] Add `PixivApi.userDetail(userId)` decoding `user.is_followed`
- [x] T006 Add `FOLLOW_AUTHORS` capability to the Pixiv platform entry
- [x] T007 ViewModel: follow-state load/toggle with followed-feed cache
  invalidation; tests for both
- [x] T008 UI: Follow/Following toggle in the user-works header with failure
  toast
- [x] T009 Default to the Following tab on Pixiv selection and initial
  restore; test for both

## Phase 3: Track B — local state (Stories 3, 4)

- [x] T010 [P] Migrate `LattePreferences` to Preferences DataStore with one-time
  `latte_prefs` key migration; public surface unchanged; existing prefs tests
  still pass
- [x] T011 [P] Add favorite tags, ordered recent searches (bound 20), and
  blacklist tags keys with round-trip tests
- [x] T012 Record successful yande searches into recent searches; test
- [x] T013 [P] `YandeApi.getTagSuggestions(prefix)` from `/tag.json`; parsing
  test against fixture
- [x] T014 ViewModel suggestion cache per prefix; blank-prefix favorites/recents
  exposure
- [x] T015 Search bar UI: chips rows (favorites, recents, suggestions) and the
  star favorite toggle

## Phase 4: Story 5 — blacklist

- [x] T016 [P] Extract shared `filterPosts(posts, safeMode, blacklist)` seam;
  Safe Mode × blacklist composition tests
- [x] T017 Apply the seam in yande and Pixiv feed paths including pagination
  continuation
- [x] T018 Settings: blacklist tag editor beside Safe Mode; feed reload wiring
  through the existing preference collector

## Phase 5: Device acceptance (OnePlus 8, `IN2010`)

- [ ] T019 Install debug build; verify chips, star toggle, history persistence
  across app restart, blacklist hiding on yande and Pixiv
- [ ] T020 Live Pixiv: follow/unfollow an author from the works view; confirm
  the Following feed updates and Pixiv opens on Following

## Phase 6: Closeout

- [ ] T021 `make check`, `make lint`, `make validate` green; CHANGELOG entry;
  PR linked to issue #5
- [ ] T022 File deferred follow-up issues (bulk download, backup/export, tag
  collections)
