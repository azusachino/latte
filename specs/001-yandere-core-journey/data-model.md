# Data model: yande.re core journey

All values below are immutable product concepts. Remote wire DTOs and Android
platform payloads are private boundary types and are not domain entities.

## Site identity

- `SiteId`: non-empty stable string; first production value is `yandere`.
- `SiteDescriptor`: ID and display name only. It does not advertise unused
  capabilities in this milestone.

## Post reference

- `siteId`: required `SiteId`.
- `remoteId`: required non-empty string; numeric formatting is site-owned.
- Equality and hashing use both fields.

## Post summary

- `reference`: required `PostRef`.
- `rating`: `safe | questionable | explicit | unknown`.
- `tags`: ordered normalized strings preserving site spelling.
- `preview`: optional usable `MediaVariant`.
- `score`, `width`, `height`, `source`, `createdAt`: optional.
- Every rating, including explicit, is valid presentation data. The first
  milestone does not filter or rewrite posts based on rating.
- A summary without usable preview media may be omitted from the grid but remains
  a structured adapter result for diagnostics.

## Post detail

- `summary`: required `PostSummary`.
- `media`: one or more usable `MediaVariant` values, ordered preview → sample →
  JPEG → original when present.
- `parentId`, `hasChildren`, `fileChecksum`, `fileExtension`: optional.
- Missing optional fields remain absent; no placeholder values are synthesized.

## Media variant

- `id`: stable within one post (`preview | sample | jpeg | original`).
- `kind`: same closed set as ID for this milestone.
- `width`, `height`, `byteSize`, `extension`: optional.
- Remote location is adapter-private. Domain and UI carry the variant ID and
  descriptive metadata, not download authority.

## Discovery query

- `source`: `newest | tagSearch` for the existing paged feed and search paths.
- `expression`: required for tag search; trimmed but otherwise opaque.
- `continuation`: optional adapter-owned value.
- Control characters and empty submitted searches are invalid.

## Popular query

- `period`: normalized `day | week | month`.
- `anchor`: a UTC calendar date selected by the owner.
- `window`: the period-derived inclusive UTC start/end dates. A day window has
  one date; week and month windows are calendar windows containing `anchor`.
- `page`: the adapter-owned page number or continuation position.
- Period and anchor/window together form query identity and survive detail/back
  navigation. They are not encoded into a widget-local label.
- The yande.re adapter maps the normalized value to its verified opaque query;
  common code does not construct site tags.

## Post page

- `posts`: ordered unique `PostSummary` values.
- `next`: optional continuation; absent ends pagination.
- Duplicate references in one or adjacent pages are ignored by the feature
  controller while preserving first-seen order.

## Explore state

States are mutually exclusive:

```text
initial -> initialLoading -> content | empty | failure
content -> nextPageLoading -> content | endReached | nextPageFailure
  content <-> detail/pager
content -> replacingQuery -> content | noResults | failure
search content -> discovery content (clear query)
```

`content` retains current query, ordered posts, continuation, feed position,
and whether the last next-page attempt failed. Superseded request results do not
transition current state.

## Site failure

- `invalidRequest`: owner input cannot be submitted safely.
- `transportUnavailable`: the request has no usable transport; owner copy does
  not infer device-offline versus DNS, TLS, captive-portal, or remote failure.
- `throttled`: remote `421`/`429`, with optional retry-after.
- `remoteUnavailable`: retryable `5xx` or equivalent.
- `notFound`: post/media disappeared.
- `malformedResponse`: invalid JSON or invalid required fields.
- `rejectedMedia`: scheme/host/content metadata violates site policy.
- `remoteFailure`: other terminal remote response.

Each failure carries safe owner-facing context and retryability, never raw
response bodies, cookies, or credentials.

## Save request and result

- `SaveRequest`: `PostRef` plus `MediaVariant.id`.
- `ResolvedMedia`: adapter-private validated HTTPS source, expected metadata,
  and deterministic filename inputs.
- `SaveProgress`: received bytes and optional total; one foreground save exists.
- `SaveResult`: `completed(contentUri, album, displayName) |
  existing(contentUri, album, displayName) | retryableFailure |
  terminalFailure`.

Save transition:

```text
idle -> resolving -> transferring -> publishing -> completed | existing | failed
```

Only `publishing` may expose the completed object to Android MediaStore.
Failure or interruption before completion removes app-owned temporary bytes.
