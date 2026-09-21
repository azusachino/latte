# Feature Specification: Pixiv illustration experience

**Feature Branch**: `research/konachan-pixiv`

**Created**: 2026-09-21

**Status**: Implemented through fixture/auth/device-interaction gates; live
Pixiv account/feed verification and cancellation integration coverage remain
pending

**Supersedes**: Pixiv portions of
[`003-konachan-pixiv`](../003-konachan-pixiv/spec.md)

## Objective

Add Pixiv as a bounded illustration source in Latte. The first useful Pixiv
experience has three primary destinations plus query-driven illustration
search:

1. **Popular** — daily ranked illustrations.
2. **Followed updates** — new illustrations from followed artists.
3. **Favorites** — the account's bookmarked illustrations.
4. **Search** — illustration results for a user-entered keyword.

Each destination uses the existing Latte Explore grid, image-first detail
viewer, and durable local save behavior. Detail swipe moves between neighboring
feed illustrations; multi-page works use explicit page controls inside the
viewer. Latte is not becoming a complete Pixiv third-party client.

Konachan is explicitly postponed and is not part of this implementation gate.

## User stories and acceptance

### User Story 1 — Browse Pixiv popular works (P1)

As the owner, I can switch Latte to Pixiv, browse the daily ranked
illustration feed, open a work, view all of its pages, inspect metadata, and
save the visible page locally.

Acceptance scenarios:

1. Given Pixiv is selected and the account is authenticated, when Popular opens,
   then Latte requests the daily ranking operation and renders normalized
   artwork cards in the existing grid.
2. Given a ranked work has multiple pages, when it opens, then horizontal detail
   swipe moves to neighboring feed illustrations, while explicit page controls
   move through the current work's pages and show `Page n of m`.
3. Given a page is saved, when the download is queued, then the display name
   and duplicate identity use `(pixiv, workId, pageIndex, mediaVariant)`.
4. Given the ranking response is malformed or unavailable, when the feed loads,
   then Latte shows a typed error state and never renders fabricated artwork.

### User Story 2 — Search Pixiv illustrations (P1)

As the owner, I can use Latte's existing search action to search Pixiv
illustrations and start a query from autocomplete or trending tags.

Acceptance scenarios:

1. Given a non-empty query, when search is submitted, then the same grid/detail
   flow renders illustration results with opaque continuation state.
2. Given the search field has a query, when autocomplete is requested, then
   suggestions are shown without replacing the current feed with an empty
   state.
3. Given no query is entered, when trending tags load, then the user can start
   a search from a returned tag.

### User Story 3 — Connect Pixiv and use personal feeds (P2)

As the owner, I can manage a Pixiv account, browse followed updates and
bookmarks, and bookmark or unbookmark a work from detail.

Acceptance scenarios:

1. Given the account is signed out, when Followed or Favorites is selected,
   then Latte shows an explicit sign-in action rather than an empty feed.
2. Given a valid Pixiv token is available, when account-scoped feeds load, then
   Latte refreshes the token when required and maps followed/bookmarked works.
3. Given a work is not bookmarked, when bookmark is requested, then Latte sends
   the add operation and changes the UI only after success.
4. Given refresh fails, when the session is invalidated, then the account card
   becomes signed out and the feed reports authentication required.

### User Story 4 — Switch platforms and manage accounts (P2)

As the owner, I can switch between Yande and Pixiv from Explore and manage
each platform from Settings without losing the platform-local feed context.

Acceptance scenarios:

1. Given Yande and Pixiv are available, when `Latte` is tapped, then a platform
   picker exposes both static platform choices and their connection status.
2. Given the platform changes, when the Explore content crossfades, then tabs,
   tint/background, and source state change together while stale requests are
   cancelled.
3. Given each platform has a prior feed/query position, when switching back,
   then its bounded state is restored.
4. Given Settings is opened, when `Platforms & accounts` is selected, then
   platform cards expose status, capabilities, Manage, and Sign out actions.

## Product decisions

### One Explore grammar

Pixiv is a source selection, not a second navigation hierarchy. Once Pixiv is
selected, the Explore destinations become:

```text
Popular | Followed updates | Favorites
```

Search remains the existing top-bar search action. It opens a query-labelled
feed and returns to the previous destination with its scroll position intact;
it is not a permanent fourth tab.

The grid remains aspect-preserving and artwork-first. Detail is a feed pager:
horizontal swipe always means adjacent illustrations from the active feed. A
multi-page Pixiv work is a second, explicit navigation level inside the current
viewer, using previous/next page controls and a visible `Page n of m` label.
This prevents a page inside one work from being mistaken for the next feed
item, and makes Save/metadata actions target the visible page.

The `Latte` title acts as the platform switcher. Selecting Pixiv changes the
platform tint/background and tab semantics together, with a short crossfade
that respects reduced-motion settings. The background is a restrained source
identity treatment, not remote artwork. Each platform retains bounded feed,
query, scroll, and detail state independently; switching platforms cancels
stale requests and restores the selected platform's last valid state.

### Account boundary

Popular, Followed updates, and Favorites use Pixiv's authenticated App API
contract and require a valid Pixiv session. The public website's ranking route
is a separate, drift-prone HTML/web adapter and is not part of Latte's supported
feed contract. An authentication requirement is never represented as an empty
list.

Latte must not collect a Pixiv password in a normal app-owned form. The primary
Pixiv action is browser-based Pixiv sign-in, followed by refresh-token storage,
refresh, logout, and invalidation. Token import is an advanced recovery path,
not the default sign-in experience. Credentials are stored only in encrypted
platform-scoped storage; Latte does not persist a Pixiv password.

The first account slice supports one active account per platform. The main
Settings screen shows a concise account summary and opens a dedicated
`Platforms & accounts` screen. That screen presents static Yande and Pixiv
platform cards with connection status, account identity, available feed/action
capabilities, and Manage / Sign out actions. Platform transport details are
not account-management UI. `Manage account` opens the platform card; a
feed-level authentication error may offer direct sign-in for that platform.

### Image boundary

`i.pixiv.cat` and `pixiv.cat` are image transports, not the Pixiv metadata or
account API. A
Pixiv work page retains:

- the Pixiv work ID and zero-based page index;
- the original image URL when supplied by metadata;
- the canonical Pixiv web URL;
- a resolver reference for the selected image host.

When metadata supplies an `i.pximg.net` URL, the default resolver rewrites only
the host to `i.pixiv.cat`, preserving the original path and query. When no
origin URL is available, it falls back to the `pixiv.cat/<work-id>-<page>` URL
grammar. It trusts response `Content-Type` and `Content-Disposition` for
decoding and filenames. It does not store or use a Pixiv.Cat service refresh
token in Latte.

## Capability map

| Module | Responsibility | Depends on |
| --- | --- | --- |
| `pixiv-artwork` | Normalized work, artist, page, tag, restriction, and media identity | — |
| `pixiv-account` | Browser/token auth flows, session state, secure persistence, and account-required gating | — |
| `pixiv-feeds` | Popular, followed-update, favorites, search, and search-support contracts plus opaque paging | `pixiv-artwork`, `pixiv-account` |
| `pixiv-transport` | `i.pixiv.cat` host rewriting, `pixiv.cat` fallback resolution, and response filename/type handling | `pixiv-artwork` |
| `pixiv-explore` | Platform switcher, feed tabs, feed detail pager, page controls, bookmark actions, and local save handoff | `pixiv-artwork`, `pixiv-account`, `pixiv-feeds`, `pixiv-transport` |

Build order: `pixiv-artwork` → fixture-backed `pixiv-account`,
`pixiv-feeds`, and `pixiv-transport` → `pixiv-explore` → live Pixiv auth/feed
enablement.

## Domain contract

The current Yande `Post` shape is not sufficient as a long-term Pixiv model:
Pixiv has a work title, artist identity, page count, bookmark count, and
optional restriction/stat fields that must not be faked as a Yande score or
rating. The adapter boundary should normalize into a source-neutral artwork
model before Compose sees it.

The intended shape is conceptually:

```kotlin
data class Artwork(
    val sourceId: String,
    val workId: Long,
    val title: String?,
    val artist: Artist?,
    val tags: List<String>,
    val restriction: ContentRestriction?,
    val pages: List<ArtworkPage>,
    val stats: ArtworkStats?,
    val createdAt: Instant?,
    val canonicalUrl: String,
)

data class ArtworkPage(
    val pageIndex: Int,
    val width: Int,
    val height: Int,
    val originalUrl: String?,
    val previewUrl: String?,
    val mediaRef: MediaRef,
)
```

The exact Kotlin names may follow the existing model conventions, but these
properties are part of the design. In particular, source identity and page
identity must be durable:

```text
work identity = (sourceId, workId)
page identity = (sourceId, workId, pageIndex)
media identity = (sourceId, workId, pageIndex, mediaVariant)
```

## Feed contract

```kotlin
enum class PixivFeedKind {
    POPULAR,
    FOLLOWED_UPDATES,
    FAVORITES,
    SEARCH,
}

sealed interface PixivBookmarkResult {
    data object Success : PixivBookmarkResult
    data object AuthRequired : PixivBookmarkResult
    data class RateLimited(val retryAfterSeconds: Long?) : PixivBookmarkResult
    data class UpstreamDrift(val operation: String) : PixivBookmarkResult
    data class TransportFailure(val message: String) : PixivBookmarkResult
}

data class SearchSupport(
    val autocomplete: List<String>,
    val trendingTags: List<String>,
)

data class FeedRequest(
    val kind: PixivFeedKind,
    val query: String? = null,
    val cursor: String? = null,
    val refresh: Boolean = false,
)

data class FeedPage(
    val items: List<Artwork>,
    val nextCursor: String?,
)
```

The `nextCursor` is opaque. The Pixiv adapter may retain a validated `next_url`
internally, but the UI and generic feed state must not parse or manufacture
numeric offsets.

The initial protocol mapping is:

| Latte feed | Current reference operation | Auth |
| --- | --- | --- |
| Popular | Pixiv illustration ranking | required; expose `AuthRequired` honestly |
| Followed updates | Pixiv followed-illustration feed | required |
| Favorites | Pixiv bookmarked-illustrations feed | required |
| Search | Pixiv illustration search | upstream-dependent; expose `AuthRequired` honestly |

Bookmark mutations use the active Pixiv account and have the same explicit
authentication and upstream-failure boundary:

| Action | Reference operation | Auth |
| --- | --- | --- |
| Bookmark | Pixiv bookmark add/delete operations | required |

Search support is part of the first experience but remains adapter-owned:

- autocomplete returns keyword suggestions for the active query;
- trending tags provide a browseable entry point before a query is entered;
- either operation may return an empty result without turning a feed failure
  into an empty feed;
- the UI does not expose Pixiv's broader user, novel, or advanced search
  matrix.

The reference operations are visible in the read-only [PixEz API
client](../../../../refs/image-gallery-apps/pixez-flutter/lib/network/api_client.dart)
at pinned revision `7f89bc8`. They are protocol evidence, not a stable Pixiv
public API contract.

Feed results use typed states rather than exceptions or silent empty lists:

```kotlin
sealed interface FeedLoadResult {
    data class Success(val page: FeedPage) : FeedLoadResult
    data object AuthRequired : FeedLoadResult
    data class RateLimited(val retryAfterSeconds: Long?) : FeedLoadResult
    data class UpstreamDrift(val operation: String) : FeedLoadResult
    data class TransportFailure(val message: String) : FeedLoadResult
}
```

## UI contract

### Feed screen

- Source selector chooses Pixiv without changing Latte's global navigation.
- Three tabs expose the feed kinds above.
- The platform switcher is reachable by tapping `Latte`; its Pixiv identity is
  visible through the restrained Explore background/tint and selected source
  treatment.
- Pull-to-refresh resets the active cursor and requests the first page.
- End-of-list loading follows the existing staggered-grid behavior.
- Empty, loading, rate-limit, upstream-drift, and auth-required states are
  distinct.
- An auth-required state offers a clear Pixiv sign-in action; it does not show
  a fake empty feed or an endless retry loop.
- Search accepts a non-empty keyword and reuses the same feed/grid/detail
  contract. The first slice sends only the keyword and uses the verified
  server default sort.
- Autocomplete and trending tags are available from the search entry surface.
  Local query history, ranking modes, and advanced search filters are separate
  follow-ups.

### Work detail

- The viewer starts at the selected page and swipes through pages of that work.
- Metadata includes title, artist, tags, page position, restriction, and
  bookmark count when provided.
- The canonical Pixiv URL is used for open/share actions.
- The detail action can bookmark or unbookmark the work. The action uses the
  active Pixiv account, prompts for sign-in when no valid session exists, and
  reports mutation failure without changing the displayed state optimistically.
- Local download is separate from remote bookmark state.
- Comments, related works, artist profiles, novels, manga, and ugoira are not
  rendered in this feature.

### Image resolution

```text
ArtworkPage.mediaRef
  -> PixivCatResolver(workId, pageIndex)
  -> actual response type and filename
  -> Coil / WorkManager
```

The original metadata URL is retained for sharing and diagnostics. The first
slice does not expose custom mirrors or direct-origin fallback. A transport
failure offers retry and open-in-Pixiv rather than silently switching to an
unverified host.

## Non-goals

- Pixiv password collection or an unverified login workaround.
- Remote follow/unfollow mutations.
- Recommendations, user profiles, comments, and related works.
- Novels, manga, ugoira, multi-account, and notification sync.
- A second Pixiv-specific navigation architecture.
- Konachan implementation or Cloudflare challenge handling.

## Reference lessons

- [PixEz Flutter](https://github.com/Notsfsssf/pixez-flutter) separates App API,
  OAuth, image hosts, caching, and feed operations. Its image host resolver is
  a useful model for applying host policy at load time.
- [Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft) separates a generic
  feed framework from Pixiv protocol adapters and preserves original image URLs
  while rewriting only image-load URLs. Its documented scope is much larger;
  Latte adopts the boundary, not its complete feature set.
- [Pixiv.Cat backend](https://github.com/pixiv-cat/pixivcat-backend) confirms
  that Pixiv.Cat is a Pixiv image proxy with its own server-side refresh-token
  dependency. Latte must treat it as transport only.

## Verification gates

1. Fixture mappers cover one success response for each feed kind, one search
   response, one search-support response, and one multi-page detail response.
2. Fixture/error tests distinguish empty, auth-required, rate-limited,
   malformed, upstream-drift, and transport failure states.
3. Artwork/page/media identities remain stable across proxy URL changes.
4. Pixiv.Cat resolver tests cover page zero, page greater than zero, response
   content type, filename, non-image response, and retryable failure.
5. Explore/detail tests prove that switching source does not lose feed context,
   page position, or local-save identity.
6. Platform switching restores per-platform feed/search state, cancels stale
   requests, and preserves detail/page identity.
7. A bounded live metadata/authentication spike is recorded separately from
   fixture evidence before enabling account feeds on a device.
8. `make check` passes before any implementation slice is considered green.
