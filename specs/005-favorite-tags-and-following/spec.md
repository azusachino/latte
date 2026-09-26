# Feature Specification: Favorite tags, following, and local filtering

**Feature Branch**: `feat/favorite-tags-and-following`

**Created**: 2026-09-26

**Status**: Proposed

**Input**: [azusachino/latte#5](https://github.com/azusachino/latte/issues/5),
extended after a survey of PixEz, Boorusama, Yummybooru, and neighbours with two
adjacent features (search history, tag blacklist) that reuse the same seams.

## Objective

Make the two platforms' personal-feed loops complete:

1. **Pixiv following** — follow and unfollow authors from their works view, and
   open Pixiv on the Following tab instead of Popular.
2. **Yande favorite tags** — save tags locally and start searches from them.
3. **Yande search support** — recent searches and tag autocomplete chips.
4. **Tag blacklist** — a local hide-list applied across both platforms,
   generalizing Safe Mode's existing filter seam.

Latte is not becoming a social client: there is no follow list screen, no
comment surface, and no server-side tag favorites. Following state is shown
where author works are already shown.

## Persistence decision

All new local state lives in **Preferences DataStore** (`androidx.datastore:datastore-preferences`),
exposed through `LattePreferences` with the same `StateFlow` + mutator surface
as today. Mapping against the Jetpack storage guidance:

| Data | Store |
| --- | --- |
| Favorite tags, blacklist tags | Preferences DataStore (`stringSetKey`) |
| Recent searches (ordered) | Preferences DataStore, one JSON-encoded `stringKey` (DataStore has no ordered set; kotlinx-serialization is already a dependency), bound to 20, LRU-deduped |
| Existing keys (`columnCount`, `themeMode`, `safeMode`) | Migrated to the same DataStore in a one-time read of `latte_prefs`; the SharedPreferences file is retired |

Proto DataStore is rejected: three flat keys do not justify a protobuf schema
toolchain. Room/SQLite is rejected: nothing is relational or queryable here.
Revisit triggers: a local tag-autocomplete cache (Yummybooru-scale) or
queryable download history — that is the `Room` row of the guidance table.

Device acceptance runs on the connected OnePlus 8 (`IN2010`): manual verify
of chip rows, star toggle, follow toggle against the live Pixiv account, and
blacklist hiding on both platforms, in addition to the fixture-backed unit
tests.

## User stories and acceptance

### User Story 1 — Follow a Pixiv author (P1)

As the owner, I can follow or unfollow an author from their works view and see
the current follow state.

Acceptance scenarios:

1. Given an author's works view is open, when it loads, then Latte requests
   `/v1/user/detail` (`filter=for_android`, `user_id`) and renders a
   Follow/Following toggle reflecting `user.is_followed`.
2. Given the toggle is tapped, then Latte POSTs `v1/user/follow/add` (public
   restrict) or `v1/user/follow/delete` with `user_id`; the toggle updates only
   on success and a toast reports failure.
3. Given the access token is expired, when follow or state-read fails with
   auth, then the existing token refresh runs once, then the
   existing `authRequired` presentation applies.
4. Given follow succeeds, then the Following feed's cache is invalidated so the
   next visit reflects the change.

### User Story 2 — Pixiv opens on Following (P1)

As the owner, when I switch to Pixiv, then the Following tab is selected and
loads, not Popular.

Acceptance scenarios:

1. Given any yande tab is active, when the platform is switched to Pixiv, then
   `selectedTab` becomes 1 and `ensurePixivFeedLoaded(FOLLOWED_UPDATES)` runs.
2. Given Pixiv is not authenticated, when Following loads, then the existing
   sign-in prompt is shown; the tab does not silently fall back to Popular.

### User Story 3 — Favorite yande tags (P1)

As the owner, I can save tags I search often and start a search from them.

Acceptance scenarios:

1. Given the yande search bar is expanded and the query is blank, then saved
   favorite tags render as chips; tapping one fills the query and searches.
2. Given a query (typed or from a tag chip), when the star toggle in the search
   bar is enabled, then the trimmed query is added to favorites; toggling again
   removes it.
3. Given favorites exist, when the search bar is expanded with a query, then
   only the star's selected state reflects membership.
4. Given the device restarts, when the search bar is expanded, then favorites
   persist.

### User Story 4 — Yande search history and autocomplete (P2)

As the owner, the yande search bar offers recent searches and tag suggestions.

Acceptance scenarios:

1. Given a search completes successfully, when the search bar next expands,
   then the trimmed query appears first in a recent-search chip row (bounded,
   deduped, most-recent-first).
2. Given a partially typed query of at least 2 characters, then Latte requests
   `/tag.json?name=<query>*&order=count&limit=8` and renders suggestion chips;
   results are cached per prefix in the ViewModel for the session.
3. Given the autocomplete request fails, when results do not arrive, then the
   chip row is simply absent; no error surface is shown.

### User Story 5 — Tag blacklist (P2)

As the owner, I can hide posts carrying certain tags on both platforms.

Acceptance scenarios:

1. Given blacklist entries in Settings, when any feed page (yande or Pixiv)
   applies, then posts whose `tags` intersect the blacklist are dropped before
   rendering, sharing the Safe Mode filter path.
2. Given a blacklisted post is the only content on a feed page, when pagination
   continues, then the existing skip-empty-page continuation logic applies.
3. Given Safe Mode is also on, when both filters run, then posts are dropped by
   either rule (composition, not precedence).
4. Given detail is open for an already-rendered post, when the blacklist
   changes, then already-open content is not retroactively closed; the filter
   applies to subsequent loads.

## Non-goals

- A followed-authors management screen or follow list.
- Server-side yande tag favorites (Moebooru API); local only.
- Blacklist of authors or works (tags only; authors can follow later).
- SQLite/Room, DataStore migration, backup/export, tag collections.

## Testing strategy

Public-seam unit tests with fixtures, `make check` green per task:

- `PixivApi.followAuthor` and `userDetail` against recorded/redacted fixtures
  in `app/src/test/resources/pixiv/`, covering success, auth-required, drift,
  and transport shapes (mirroring the bookmark test seam).
- `LattePreferences` round-trip for favorites, ordered history (bound and
  dedupe), and blacklist.
- Filter composition: Safe Mode × blacklist matrix over `Post.tags` and
  `Post.rating`, one shared seam for both platforms.
- Yande tag suggestions parsing from a `/tag.json` fixture.
- ViewModel-level: default-tab selection on platform switch, follow-state
  cache invalidation of the followed feed.
