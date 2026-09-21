# Feature Specification: 003 Konachan.com and Pixiv sources

**Feature Branch**: `research/konachan-pixiv`

**Created**: 2026-09-21

**Status**: Proposed; owner review required before production implementation

**Input**: [`Konachan and Pixiv research`](../../docs/research/2026-09-21-konachan-pixiv.md)

## Product boundary

Latte remains a Yande-compatible image-board client with a site adapter
boundary. This feature adds source-specific capabilities behind that boundary;
it does not replace the existing Explore grammar with a new Pixiv social app.

The source identities are explicit:

```text
konachan.com  = a Moebooru-shaped source that may require a browser fallback
pixiv         = Pixiv illustration metadata plus an independently resolved image transport
konachan.net  = out of scope; do not treat it as a .com mirror or fallback
```

## Research-derived capability map

The two read-only references are pinned locally at the revisions available on
2026-09-21:

| Reference | Useful lesson for Latte | Boundary to keep |
| --- | --- | --- |
| [PixEz Flutter](../../../../refs/image-gallery-apps/pixez-flutter/lib/network/api_client.dart) `7f89bc8` | Illustration search, ranking, related works, bookmarks, user works, ugoira metadata, next-page URLs, cached reads, and a distinct image-source resolver | Its private App API headers, DNS/TLS workarounds, and broad novel/social surface are not Latte requirements |
| [Pixiv-Shaft feed module](../../../../refs/image-gallery-apps/pixiv-shaft/docs/feeds-module.md) `2c8a30b` | Keep generic feed state separate from Pixiv protocol knowledge; make paging, empty/error states, and local-first first-page caching explicit | Do not copy its Android modules or assume its private API/network route is stable |
| [Pixiv-Shaft image-host note](../../../../refs/image-gallery-apps/pixiv-shaft/docs/image-host.md) `2c8a30b` | Rewrite image hosts at load time, preserve original URLs for sharing, and disable a transport bypass when a proxy/custom host needs ordinary TLS verification | `pixiv.cat` is a transport policy, not metadata/authentication |

The required Latte capability map is deliberately smaller:

| Capability | First usable slice | Later gate |
| --- | --- | --- |
| `konachan.com` anonymous browse | Post list, tag search, post lookup, normalized media variants | Pools, score, favorites, login |
| `konachan.com` challenge handling | Classify challenge HTML/headers as `BrowserRequired`; open the system browser and offer retry | Native API after a future opt-in live contract confirms JSON access |
| Pixiv illustration browse | Fixture-backed feed/detail contract with explicit token-required and upstream-drift errors | Live metadata adapter after an authentication/contract spike |
| Pixiv image transport | `pixiv.cat` ID/page resolver, actual content type and filename handling, original URL retention | User-selected mirrors, direct origin fallback, ugoira transport |
| Shared local save | Site/work/page/variant identity, durable duplicate check, background save | Batch queue and download history |

## User stories and acceptance

### User Story 1 - Browse Konachan.com when native access is available (P1)

As the owner, I select Konachan.com, search its site-owned tag syntax, scan an
aspect-preserving feed, open a post, page through available media, and save one
variant without losing the feed context.

Acceptance scenarios:

1. A valid Moebooru post array maps to Latte's normalized post model without
   dropping optional preview, sample, original, dimensions, rating, tags, or
   checksum fields.
2. A post list, tag search, and post lookup preserve the source identity
   `konachan.com`; numeric IDs alone are not cache or download identity.
3. An HTML challenge response is not decoded as an empty list or malformed
   JSON. It becomes `BrowserRequired(origin = konachan.com)`.
4. The browser action opens the canonical `.com` URL. Returning to Latte
   exposes an owner-triggered retry and does not claim that browser cookies
   authorize a separate native request.
5. Detail and save restore the current feed query, page, position, and source
   identity.

### User Story 2 - Use Pixiv for illustration discovery (P2)

As the owner, I search or browse Pixiv illustrations, open a work, move across
its pages, inspect artist/tags/restriction metadata, and keep one image locally.

Acceptance scenarios:

1. The first metadata adapter is fixture-backed and models success, empty,
   token-required, rate-limited, malformed, and upstream-drift states
   separately.
2. A work has stable identity `(pixiv, workId)`, and a page has identity
   `(pixiv, workId, pageIndex)`.
3. The detail pager exposes page count, title, artist, tags, restriction
   marker, bookmark count when supplied, and the canonical Pixiv URL without
   inventing absent values.
4. A page's image URL is resolved through the selected image-host policy. The
   default proxy path is `pixiv.cat`; the original metadata URL remains
   available for sharing and diagnostics.
5. The resolver trusts the response `Content-Type` and filename when saving;
   it does not assume that a `.jpg` proxy suffix proves JPEG bytes.
6. Bookmark, follow, comment, novel, multi-account, and password collection
   are unavailable until their own auth and contract gates pass.

### User Story 3 - Save consistently across sources (P2)

As the owner, I can save a Konachan or Pixiv page and get the same durable,
restart-safe duplicate behavior already required by Latte's Yande journey.

Acceptance scenarios:

1. Save authority comes from a normalized site/work/page/variant reference,
   not an arbitrary URL supplied by a presentation component.
2. A proxy-resolved Pixiv URL and a direct-origin URL for the same page do not
   create accidental duplicate files when the canonical media identity is the
   same.
3. A failed or interrupted transfer remains unpublished and retryable.
4. An existing published item is reported as already saved without overwrite,
   including after process restart.

## Required architecture

The implementation must introduce the smallest source seam that the existing
Yande flow can consume without importing site-specific wire models into Compose:

```text
source selection
  -> source adapter (query / lookup / media resolution)
  -> normalized feed + post + media identity
  -> existing Explore / Detail / Download workflows
```

The source adapter owns endpoint paths, query syntax, response classification,
remote status mapping, site policy, and media URL resolution. Presentation owns
neither `post.json` nor Pixiv App API routes. The existing `SitePlugin` remains
for authentication and optional remote mutations; it must not become the
metadata feed abstraction merely because Pixiv has an account.

### Konachan.com contract

- Anonymous operations: post list, tag search, post lookup, media variants.
- Response classifier: JSON success, empty JSON, ordinary HTTP failure,
  challenge HTML/headers, malformed JSON, and transport failure.
- Challenge result: a typed browser-required state with canonical origin and
  retry action.
- No challenge solver, `cf_clearance` import, CAPTCHA automation, stealth
  header layer, rotating proxy pool, or challenge-parameter replay.
- No `.net` route or fallback in this feature.

### Pixiv contract

- Initial metadata operations: illustration list/search/detail, with pagination
  represented as an opaque server cursor or next URL rather than an assumed
  numeric offset.
- Initial media operation: resolve a work ID and page index to `pixiv.cat`,
  while retaining the original Pixiv URL and canonical web URL.
- Media policy must make the proxy host explicit and must not weaken TLS
  verification merely because a proxy is selected.
- Authenticated operations are separate capabilities: token acquisition,
  refresh, bookmark mutation, follow, comments, and account-scoped feeds.
- Ugoira is a separate media variant and is deferred until a ZIP/frame contract
  and local playback/save policy are verified.

## Non-goals

- Building or shipping a Cloudflare challenge solver.
- Treating `konachan.net` as a mirror, compatibility mode, or source alias.
- Reproducing Pixiv's novels, social graph, messaging, comments, or full
  account center in the first source slice.
- Copying code from PixEz or Pixiv-Shaft into Latte.
- Storing Pixiv or Pixiv.cat service refresh tokens in logs, fixtures, or
  unencrypted preferences.
- Adding a second source-specific UI information architecture.

## Verification gates before implementation

1. Owner accepts the `.com`-only Konachan boundary and this capability map.
2. Fixture tests cover challenge classification, Moebooru normalization,
   Pixiv page identity, proxy URL resolution, and content-type/filename use.
3. A bounded opt-in Konachan.com probe is recorded with the current response
   class; it is evidence, not a release health check.
4. A Pixiv metadata fixture contract is agreed before any private App API call
   is added.
5. `make check` passes for every implementation slice; live/device claims are
   recorded separately from fixture and unit-test evidence.

This specification is a review artifact. Production implementation remains
blocked until the owner approves the source boundary and the first thin slice.
