# Feature Specification: Pixiv illustration experience

**Feature Branch**: `research/konachan-pixiv`

**Created**: 2026-09-21

**Status**: Proposed; owner review required before implementation

**Supersedes**: Pixiv portions of
[`003-konachan-pixiv`](../003-konachan-pixiv/spec.md)

## Objective

Add Pixiv as a bounded illustration source in Latte. The first useful Pixiv
experience has three primary destinations plus query-driven illustration
search:

1. **Popular** — ranked illustrations, starting with one verified ranking mode.
2. **Followed updates** — new illustrations from followed artists.
3. **Favorites** — the account's bookmarked illustrations.
4. **Search** — illustration results for a user-entered keyword.

Each destination uses the existing Latte Explore grid, image-first detail
pager, and durable local save behavior. Latte is not becoming a complete Pixiv
third-party client.

Konachan is explicitly postponed and is not part of this implementation gate.

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

The grid remains aspect-preserving and artwork-first. Detail remains a pager,
but its pages are the illustrations inside one Pixiv work rather than adjacent
posts from the feed.

### Account boundary

Popular may be attempted without an account, but the adapter must surface the
real upstream requirement if the current App API route rejects anonymous calls.
Followed updates and Favorites are account-scoped and require a valid Pixiv
session. An authentication requirement is never represented as an empty list.

Latte must not collect a Pixiv password in a normal app-owned form. Refresh
token acquisition, encrypted storage, refresh, logout, and invalidation are a
separate authentication spike. Until that spike is accepted, live account
feeds remain gated while fixtures can exercise their UI states.

### Image boundary

`pixiv.cat` is an image transport, not the Pixiv metadata or account API. A
Pixiv work page retains:

- the Pixiv work ID and zero-based page index;
- the original image URL when supplied by metadata;
- the canonical Pixiv web URL;
- a resolver reference for the selected image host.

The default resolver creates a `pixiv.cat` candidate from work ID and page
index at load/download time. It trusts response `Content-Type` and
`Content-Disposition` for decoding and filenames. It does not store or use a
Pixiv.Cat service refresh token in Latte.

## Capability map

| Module | Responsibility | Depends on |
| --- | --- | --- |
| `pixiv-artwork` | Normalized work, artist, page, tag, restriction, and media identity | — |
| `pixiv-feeds` | Popular, followed-update, favorites, and search feed contracts plus opaque paging | `pixiv-artwork` |
| `pixiv-transport` | `pixiv.cat` URL resolution and response filename/type handling | `pixiv-artwork` |
| `pixiv-account` | Session state and account-required gating | `pixiv-feeds` |
| `pixiv-explore` | Feed tabs, detail pager integration, and local save handoff | `pixiv-artwork`, `pixiv-feeds`, `pixiv-transport` |

Build order: `pixiv-artwork` → `pixiv-feeds` and `pixiv-transport` →
`pixiv-explore` → `pixiv-account` live connection.

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
| Popular | Pixiv illustration ranking | upstream-dependent; expose `AuthRequired` honestly |
| Followed updates | Pixiv followed-illustration feed | required |
| Favorites | Pixiv bookmarked-illustrations feed | required |
| Search | Pixiv illustration search | upstream-dependent; expose `AuthRequired` honestly |

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
- Pull-to-refresh resets the active cursor and requests the first page.
- End-of-list loading follows the existing staggered-grid behavior.
- Empty, loading, rate-limit, upstream-drift, and auth-required states are
  distinct.
- An auth-required state offers a clear Pixiv sign-in action; it does not show
  a fake empty feed or an endless retry loop.
- Search accepts a non-empty keyword and reuses the same feed/grid/detail
  contract. The first slice sends only the keyword and uses the verified
  server default sort.
- Search suggestions, local query history, ranking modes, and advanced search
  filters are separate follow-ups.

### Work detail

- The viewer starts at the selected page and swipes through pages of that work.
- Metadata includes title, artist, tags, page position, restriction, and
  bookmark count when provided.
- The canonical Pixiv URL is used for open/share actions.
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
- Recommendations, user profiles, comments, related works, and trending tags.
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
   response, and one multi-page detail response.
2. Fixture/error tests distinguish empty, auth-required, rate-limited,
   malformed, upstream-drift, and transport failure states.
3. Artwork/page/media identities remain stable across proxy URL changes.
4. Pixiv.Cat resolver tests cover page zero, page greater than zero, response
   content type, filename, non-image response, and retryable failure.
5. Explore/detail tests prove that switching source does not lose feed context,
   page position, or local-save identity.
6. A bounded live metadata/authentication spike is recorded separately from
   fixture evidence before enabling account feeds on a device.
7. `make check` passes before any implementation slice is considered green.

## Open questions for owner review

1. Should `Popular` be daily ranking only for the first live slice, or should
   the first slice include a day/week/month selector?
2. Should a Pixiv account be required before showing the Popular tab, or should
   the tab remain visible and explain `AuthRequired` when the upstream demands
   a token?
3. Is read-only Favorites sufficient for the first release, with bookmark
   mutation deferred?
