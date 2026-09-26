# Feature Specification: Favorite tags, following, and local filtering

**Feature Branch**: `feat/favorite-tags-and-following`

**Created**: 2026-09-26

**Status**: Proposed

**Input**: [azusachino/latte#5](https://github.com/azusachino/latte/issues/5),
extended after a survey of PixEz, Boorusama, Yummybooru, and neighbours with two
adjacent features (search history) that reuse the same seams.

## Objective

Make the two platforms' personal-feed loops complete:

1. **Pixiv following** — follow and unfollow authors from their works view, and
   open Pixiv on the Following tab instead of Popular.
2. **Yande favorite tags** — save tags locally and start searches from them.
3. **Yande search support** — recent searches and tag autocomplete chips.

Latte is not becoming a social client: there is no follow list screen, no
comment surface, and no server-side tag favorites. Following state is shown
where author works are already shown.

## Persistence decision

All new local state lives in **Preferences DataStore** (`androidx.datastore:datastore-preferences`),
exposed through `LattePreferences` with the same `StateFlow` + mutator surface
as today. Mapping against the Jetpack storage guidance:

| Data | Store |
| --- | --- |
| Favorite tags | Preferences DataStore (`stringSetKey`) |
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

As the owner, when I switch to Pixiv, then the Following tab is first and
selected, so my default landing is followed updates.

Acceptance scenarios:

1. Pixiv tab order is Following / Popular / Favorites; tab index 0 maps to
   the followed feed (`pixivKindForTab`).
2. Given any yande tab is active, when the platform is switched to Pixiv, then
   `selectedTab` becomes 0 and `ensurePixivFeedLoaded(FOLLOWED)` runs.
3. Given Pixiv is not authenticated, when Following loads, then the existing
   sign-in prompt is shown; the tab does not silently fall back to Popular.

### User Story 3 — Favorite yande tags (P1)

As the owner, exploring and reading detail pages is the core loop; I can
favorite a tag right where I encounter it, then check updates from the
Favorite Tags view later.

Acceptance scenarios:

1. Given a detail page on either platform, when a tag chip is long-pressed,
   then the tag's favorite state toggles; favorited chips are visually distinct
   (stronger fill, brighter border, star marker).
2. Given the search bar with a query, when the star toggle is tapped, then the
   trimmed query is favorited on the active platform; tapping again removes it.
3. Given the search bar's chip rows (favorites, recents, trending, or
   suggestions), when a chip is long-pressed, then that tag's favorite state
   toggles on that platform.
4. Given the toolbar star icon (both platforms), when tapped, then the Favorite
   Tags view lists saved tags grouped by platform, sorted alphabetically.
5. Given the Favorite Tags view, when a row is tapped, then Latte switches to
   that entry's platform and opens the tag's search feed.
6. Given the Favorite Tags view, when a row's remove action is tapped, then the
   tag disappears from the list, chips, and detail styling.
7. Given the device restarts, when the Favorite Tags view is opened, then the
   saved tags persist (platform binding included).

A tag blacklist was surveyed as a candidate extension and removed from scope
after owner review; if wanted later it arrives as its own issue against the
shared filter seam.

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

## Non-goals

- A followed-authors management screen or follow list.
- Server-side yande tag favorites (Moebooru API); local only.
- A tag blacklist (surveyed, descoped after owner review).
- SQLite/Room, DataStore migration beyond settings, backup/export, tag
  collections.

## Testing strategy

Public-seam unit tests with fixtures, `make check` green per task:

- `PixivApi.followAuthor` and `userDetail` against recorded/redacted fixtures
  in `app/src/test/resources/pixiv/`, covering success, auth-required, drift,
  and transport shapes (mirroring the bookmark test seam).
- `PixivFollowRegressionTest` pins the plugin-seam follow loop end to end:
  state-read wire format, add/delete form bodies, unknown-vs-unfollowed on
  failed reads, signed-out mutation refusal, and the neutral
  `PluginFeedKind.FOLLOWED` mapping onto `v2/illust/follow`.
- `LattePreferences` round-trip for favorites and ordered history (bound and
  dedupe) through the pure `LattePreferenceCodecs` object.
- Safe-mode filtering over `Post.rating`, one shared seam for both platforms.
- Yande tag suggestions parsing from a `/tag.json` fixture.
- ViewModel-level: default-tab selection on platform switch, follow-state
  cache invalidation of the followed feed.
