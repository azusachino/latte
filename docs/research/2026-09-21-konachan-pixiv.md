# Konachan and Pixiv research

Date: 2026-09-21
Status: research complete; Pixiv scope revised; implementation remains gated

## Question and conclusion

Latte is pausing Konachan and defining a bounded Pixiv illustration experience.
The immediate questions are whether `pixiv.cat` can provide the image
transport, which Pixiv feed capabilities are worth exposing, and how those
capabilities fit the existing Yande-compatible Explore flow.

The conclusions are:

1. **Park Konachan.** `konachan.com` is currently returning
   a Cloudflare Managed Challenge to this workstation's non-browser HTTP
   client, including its JSON endpoint. Cloudflare documents that Challenge
   Pages return HTML for a browser to execute and are not supported by command
   line clients or automated browsers. Latte should not make Konachan part of
   the current implementation milestone.
2. **Do not substitute `konachan.net` for `konachan.com`.** The owner has
   confirmed that `.net` is a different, safe-mode-shaped site rather than the
   `.com` collection. Its successful response is therefore not evidence for a
   `.com` adapter and it is out of scope for this slice.
3. **Use `pixiv.cat` as an image transport only.** Its documented URL grammar
   resolves a Pixiv work ID and optional page number to an image, and its live
   response correctly returned the origin filename and content type. It does
   not provide Pixiv search, user, bookmark, ranking, or detail metadata.
4. **Pixiv metadata/authentication is a separate, unstable boundary.** The
   available open clients use Pixiv's private App API and refresh-token flow;
   Pixiv does not publish the App API contract as a stable developer API. The
   Pixiv target is therefore read-only illustration browsing across three
   feeds: ranked popular works, followed-artist updates, and bookmarked
   illustrations. The latter two require an authenticated account; account
   acquisition remains a separate spike.

5. **Search is part of the first experience.** Illustration search is another
   feed-shaped operation and can reuse the same grid, cursor, and detail flow.
   Autocomplete is a small follow-up if the live route is reliable.

6. **Do not build a complete Pixiv client.** Comments, social graphs, novels,
   manga, ugoira, user profiles, and multi-account behavior are outside this
   first experience. Remote bookmark/follow mutations are useful but remain a
   post-authentication gate.

This keeps the work aligned with Latte's existing site-plugin boundary while
making transport failure, site identity, and local download behavior explicit.

## Evidence boundary

This is a dated research note. Live site behavior can change without notice.
The official Konachan API help describes the common Moebooru contract; the
live probes below establish only what these hosts returned on 2026-09-21 from
this workstation. Pixiv App API behavior is inferred from live responses and
open-source client implementations because no stable public App API reference
was found.

The current Latte code already has persistent cookies and an OkHttp client in
[`OkHttpProvider.kt`](../../app/src/main/kotlin/com/azusachino/latte/data/network/OkHttpProvider.kt),
but the native Explore flow is still directly coupled to `YandeApi`. The
existing [`SitePlugin`](../../app/src/main/kotlin/com/azusachino/latte/plugin/SitePlugin.kt)
primarily owns authentication, capabilities, and request headers; it is not
yet a complete browse/detail source abstraction. New adapters should not hide
that gap behind a fake Pixiv or Konachan login state.

## 1. Konachan

### 1.1 The current Cloudflare behavior

At 2026-09-21 11:07 JST, the following read-only probes were made without
cookies or browser automation:

| Probe | Result |
| --- | --- |
| `https://konachan.com/` | HTTP 403, `server: cloudflare`, `cf-mitigated: challenge`, HTML titled `Just a moment...` |
| `https://konachan.com/post.json?limit=1` | HTTP 403 with the same challenge HTML, not a JSON post array |
| `https://konachan.net/` | HTTP 200 HTML |
| `https://konachan.net/post.json?limit=1` | HTTP 200 JSON post array |

This is a transport result, not proof that every user or every network sees
the same challenge. It is enough to reject an implementation that assumes an
OkHttp request can always decode `.com` JSON.

The reproducible experiment is [`konachan-com-probe.sh`](../../scripts/experiments/konachan-com-probe.sh)
and runs with:

```text
make experiment-konachan
```

It makes six bounded requests: HTML and JSON routes with the default client,
an Android-shaped user-agent, and a desktop browser-shaped user-agent. It does
not retain cookies, follow redirects, solve a challenge, rotate a proxy, or
retry. On 2026-09-21 at 11:20 JST, all six requests returned HTTP 403 with
`content-type: text/html`, `server: cloudflare`, and
`cf-mitigated: challenge`. Changing only `Accept` or `User-Agent` did not
change the classification.

The [Konachan API help](https://konachan.com/help/api) describes a Moebooru
API compatible with Danbooru 1.13.0. It documents JSON responses via the
`.json` suffix, `GET /post.json` with `limit`, `page`, and `tags`, and a hard
limit of 100 posts per request. The same documentation is available from
[konachan.net](https://konachan.net/help/api). The live `.net` sample contained
the expected Moebooru fields, including `id`, `tags`, `rating`, `score`,
dimensions, MD5, and preview/sample/original media URLs.

Cloudflare's own documentation gives the important constraints:

- An [Interstitial Challenge Page](https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/)
  interrupts the request and returns an HTML page for the browser to render;
  this is incompatible with a caller expecting JSON or another non-HTML body.
- [Supported browsers](https://developers.cloudflare.com/cloudflare-challenges/reference/supported-browsers/)
  explicitly exclude command-line clients and automated browsers. Cloudflare
  also notes that custom or heavily modified engines and some WebViews have
  limited support.
- A successful browser challenge sets a [challenge-passage
  cookie](https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/challenge-passage/)
  called `cf_clearance`. Cloudflare additionally documents that a solve issued
  from a different IP than the original challenge can fail or loop
  ([challenge limitations](https://developers.cloudflare.com/cloudflare-challenges/concepts/how-challenges-work/)).

Therefore the following are not acceptable Latte strategies:

- copying a `cf_clearance` value out of a browser into OkHttp;
- replaying the challenge endpoint or calculating challenge parameters;
- shipping a CAPTCHA solver, `cloudscraper`-style stealth layer, or rotating
  proxy pool;
- pretending that a browser-like User-Agent makes an HTTP client a supported
  browser; or
- using headless automation to solve a production challenge.

Those approaches are both brittle and contrary to the documented boundary.

### 1.2 Supported product behavior for `.com`

The native adapter should classify the response before decoding it:

```text
HTTP 403 + cf-mitigated: challenge
or challenge HTML containing “Just a moment...”
  -> BrowserRequired(origin = konachan.com)
```

The user-facing state should say that Konachan requires a browser check and
offer **Open Konachan in browser**. On Android, a Custom Tab is the right
fallback surface: Android documents that it uses the user's preferred browser,
shares browser state and cookies with that browser, and is better suited to
external sites than a WebView ([Custom Tabs overview](https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs)).

That browser action is a viewing fallback, not a promise that the native API
client becomes cleared. Latte must not claim that solving the challenge in the
Custom Tab authorizes a separate OkHttp connection.

The `.com` native path can be reconsidered only after an opt-in live contract
probe confirms that the endpoint is returning JSON to the target device/network.
The implementation should keep the failure state useful when that condition
drifts again.

### 1.3 Why `.net` is excluded

The earlier `.net` response must not be used as a fallback or fixture for this
work. The owner has clarified that it is not the same website as `.com` and is
effectively a safe-mode façade. Latte therefore has no `.net` product route,
adapter, or download identity in this specification. If that site is studied
later, it needs its own source decision and evidence rather than a Konachan.com
alias.

### 1.4 Konachan first slice

The smallest useful Konachan slice is:

1. Add fixture-backed parsing for the documented Moebooru post list and the
   optional fields Latte already normalizes for yande.re.
2. Add a response classifier that distinguishes JSON, an ordinary HTTP error,
   and `BrowserRequired`; never surface the challenge HTML as a parser error.
3. Add the `.com` browser fallback state and an actionable retry after the
   user returns to Latte.
4. Keep scoring, favorites, pools, and account login out of this slice until
   the anonymous browse/detail/download path is green.

Suggested verification gates:

- fixture: a 403 challenge body maps to `BrowserRequired`;
- fixture: a valid post array maps to normalized posts without dropping
  optional media URLs;
- opt-in live check: `.com` returns JSON to the target device/network;
- device check: `.com` challenge state has a readable browser action and does
  not offer a misleading native retry loop.

## 2. Pixiv and `pixiv.cat`

### 2.1 What `pixiv.cat` actually provides

The [Pixiv.cat home page](https://pixiv.cat/) documents these image URLs:

- single-page work: `https://pixiv.cat/<work-id>.<jpg|png|gif>`;
- multi-page work: `https://pixiv.cat/<work-id>-<page-number>.<jpg|png|gif>`;
- the extension is only a URL convenience; the real type is sent in
  `Content-Type`;
- `pixiv.re` and `pixiv.nl` are listed as mirror domains.

Its [reverse-proxy page](https://pixiv.cat/reverseproxy.html) documents a
second, path-preserving form: replace only `i.pximg.net` with
`i.pixiv.cat`. This is the preferred form when metadata already supplies the
original Pixiv image URL, because it preserves the exact media path and page.

The site explicitly says it is not affiliated with Pixiv and recommends its
reverse proxy when a client already has the original Pixiv image URL. The
[verified Pixiv.Cat GitHub organization](https://github.com/pixiv-cat) exposes
the backend and a Cloudflare Workers implementation. The backend README says
it is an image proxy, uses response caching, and requires server-side Pixiv
refresh tokens; the worker source resolves a work ID through Pixiv's App API
before fetching the original image.

A live probe of `https://pixiv.cat/75034219.jpg` returned:

```text
HTTP 200
Content-Type: image/png
Content-Disposition: filename="75034219_p0.png"
X-Origin-URL: https://i.pximg.net/.../75034219_p0.png
Cache-Control: public, max-age=31536000
```

The same image URL on `i.pximg.net` returned 403 without a Referer and 200
with `Referer: https://www.pixiv.net/`. The reverse-proxy form preserves the
path while avoiding that hotlink restriction; the ID route remains a useful
fallback when no original URL is available. `pixiv.cat` is therefore a
valuable image-host abstraction, but it is not a substitute for a metadata
source.

Availability is not uniform across the proxy hostnames: a 2026-09-21 probe
from the development network returned HTTP 500 from the documented
`i.pixiv.cat` path, while the equivalent `pixiv.cat/<id>.jpg` route and
`i.pixiv.re` path returned HTTP 200. Latte therefore keeps the ID route as a
runtime-compatible fallback and does not treat the reverse proxy as a health
checked guarantee.

Do not put the Pixiv.cat service's refresh token in Latte. If Latte later uses
the private App API directly for a user's own account, that is a different
user-token boundary and must be encrypted, never logged, and tested separately.

### 2.2 Metadata and authentication risk

The live unauthenticated request to
[`app-api.pixiv.net/v1/illust/detail`](https://app-api.pixiv.net/v1/illust/detail?illust_id=75034219)
returned HTTP 400 with an OAuth/access-token error. This confirms that the
App API is not a public, anonymous detail endpoint from this client context.

The maintained [PixivPy3 source](https://github.com/upbit/pixivpy) is useful
protocol evidence, not an authority from Pixiv. Its README says that password
login was removed from its client and that refresh-token authentication is
used. It lists the App API operations Latte would eventually need, including
recommendations, rankings, search, detail, related works, bookmarks, user
works, ugoira metadata, and novels. It also marks the old public API as
deprecated.

The working assumption for Latte is consequently:

```text
Pixiv metadata/auth API = private and drift-prone
pixiv.cat               = image transport/proxy only
direct i.pximg.net      = possible fallback, needs Pixiv Referer and live check
```

The first implementation must not pretend that a detail fixture proves login,
bookmark writes, or long-lived refresh-token behavior.

The browser login spike now has a narrower verified contract:

- Pixiv's native-client login page is opened at
  `app-api.pixiv.net/web/v1/login` with an S256 PKCE challenge and
  `client=pixiv-android`.
- Successful browser navigation returns through
  `pixiv://account/login?code=...`; Latte accepts only that callback shape and
  clears the delivered intent because the authorization code is single-use.
- Latte's primary entry is an app-owned WebView that intercepts this callback;
  the manifest callback remains a narrow fallback for an external browser, so
  another installed Pixiv client cannot win the login handoff.
- The PKCE verifier is stored in Latte's encrypted plugin storage before the
  browser opens, so activity/process recreation does not discard the exchange
  context. A mutex prevents duplicate callback exchanges.
- PixEz and Pixiv-Shaft both keep the native client configuration and verifier
  handling inside their OAuth boundary. Latte follows that boundary; the user
  password remains in Pixiv's browser surface and is never collected by Latte.

The connected OnePlus 8 completed the browser exchange and retained the session
across an app restart. The first authenticated Popular request exposed a
separate clock-sensitive bug: sending the device-derived `date` produced HTTP
200 with an empty `illusts` list. Current Pixiv-Shaft API declarations treat
`date` as optional and omit it for the latest ranking, so Latte now sends only
`mode=day` for Popular. The device then returned a populated ranking and
rendered the grid. Fixture coverage still remains the contract for response
mapping; live account behavior can change independently.

### 2.3 Existing clients worth studying

These projects are references for behavior and failure handling, not code to
copy into Latte:

- [PixEz Flutter](https://github.com/Notsfsssf/pixez-flutter) is a large Flutter
  client with illustration browsing, ugoira playback, localization, and
  configurable network/image-host behavior. Its [`Hoster`](https://github.com/Notsfsssf/pixez-flutter/blob/master/lib/er/hoster.dart)
  code keeps API, OAuth, and image hosts distinct and caches resolved host
  choices. It is a useful comparison for transport settings, but its network
  workarounds are not a Konachan or Cloudflare contract for Latte.
- [Shaft / Pixiv-Shaft](https://github.com/CeuiLiSA/Pixiv-Shaft) is a current
  Android client whose documented experience includes recommendations,
  discovery, daily/weekly/monthly rankings, search, related works, multi-page
  illustrations, ugoira, comments, bookmarks, download history, multi-account,
  and two-pane tablet behavior. Its current README also exposes a network
  self-check and lets users choose Pixiv image mirrors such as `pixiv.cat`,
  `pixiv.re`, and `pixiv.nl`.
- [PixivPy3's App API list](https://raw.githubusercontent.com/upbit/pixivpy/master/README.md#api-functions)
  is a compact map of the private-client surface and its pagination shape.

The lesson is not to reproduce the whole Pixiv product. It is to separate
metadata, image transport, account mutation, and local download state so one
unstable boundary does not poison the others.

## 3. Revised Pixiv experience for Latte

Latte should present Pixiv as one source with three primary feed destinations
and a query-driven search feed. This
preserves the existing Explore grammar while making account requirements clear.

```text
Pixiv Explore
  ├─ Popular          ranked illustrations; default daily ranking
  ├─ Followed updates works from followed artists; account required
  └─ Favorites        bookmarked illustrations; account required
  + Search            illustration results; query-driven feed
```

`Popular` maps to the App API ranking operation, not the broader recommended
surface. The native App API requires OAuth in the current client context, so
Latte exposes Popular as auth-gated. The latest daily ranking request sends
`mode=day` without a client-generated `date`; dated historical ranking is not
part of this first UX. The public website's `ranking.php` route
is a separate web response: it returned JSON from the workstation but HTML on
the Android device, so it remains research-only rather than a second feed
adapter. Popular should start with one verified ranking mode rather than exposing
Pixiv's complete ranking/date/filter matrix. `Followed updates` maps to the
followed-illustration feed. `Favorites` maps to the user's bookmarked
illustrations. PixEz's source shows these as distinct operations rather than a
single client-side filter: ranking, followed illustrations, and bookmarked
illustrations have separate routes and pagination behavior in its
[`api_client.dart`](https://github.com/Notsfsssf/pixez-flutter/blob/master/lib/network/api_client.dart).

All three feeds use the same artwork-first staggered grid. The card may show
title, artist, page count, bookmark count, and a restriction marker when the
metadata supplies them. It must not invent a score or rating to satisfy the
Yande-shaped card.

Search is an action in the Explore top bar rather than a fourth permanent tab.
Submitting a non-empty query replaces the active feed with a query-labelled
result feed; back returns to the previous feed and its scroll position. The
first search contract is keyword plus the server's default illustration sort.
Advanced sort, date, bookmark-count, AI-type, user, and novel search filters
are later options, not reasons to make the first search screen a form.

The reference clients expose a few useful, relatively small additions:

| Feature | Evidence | Value | Latte decision |
| --- | --- | --- | --- |
| Illustration search | PixEz `/v1/search/illust` | High; useful without a fixed feed | Include in first Pixiv experience |
| Search suggestions | PixEz `/v2/search/autocomplete` | Medium; reduces query friction | Include in the first search experience; local recent-query history remains deferred |
| Ranking period/mode | PixEz ranking `mode` and `date` parameters | Medium; familiar Popular refinement | Daily first; add week/month after live contract verification |
| Bookmark/unbookmark | PixEz `/v2/illust/bookmark/add` and `/v1/illust/bookmark/delete` | High; makes Favorites actionable | Include after the auth and mutation gates pass |
| Related works | PixEz `/v2/illust/related` | Medium; natural detail continuation | Easy follow-up, not required for first feed slice |
| Trending tags | PixEz `/v1/trending-tags/illust` | Medium; useful search entry point | Include as search-entry support; keep it out of the permanent tab row |
| User profile/follow actions | PixEz user/follow routes | Medium but opens a social graph | Defer with the full-client surface |

This keeps the initial implementation small without making the source feel
like three disconnected demo tabs: search and, later, one bookmark action give
the feed set a useful discovery-to-collection loop.

The detail screen keeps Latte's existing image-first pager, but the pager is
for pages inside one Pixiv work. It exposes title, artist, tags, page position,
restriction, bookmark count when present, the canonical Pixiv URL, sharing,
and local download. It does not add comments, related works, user profiles,
novels, manga, or ugoira in this slice.

### Feed state and authentication

The destinations are one feed state machine with a feed-kind parameter:

```text
FeedKind.POPULAR
FeedKind.FOLLOWED_UPDATES
FeedKind.FAVORITES
FeedKind.SEARCH(query)
```

The source owns protocol-specific paging. The UI receives an opaque cursor and
must not parse Pixiv's `next_url` into an assumed numeric offset. A token
requirement is a visible state, not an empty feed:

```text
Loading -> Content(items, nextCursor)
        -> Empty
        -> AuthRequired(action = Sign in to Pixiv)
        -> RateLimited(retryAfter?)
        -> Unavailable(reason)
```

Pixiv login is not a password form owned by Latte. The account design needs a
separate authentication spike for refresh-token acquisition, encrypted
storage, refresh, logout, and account invalidation. Until that gate passes,
fixture data can exercise the followed and favorites UI, while live requests
must surface `AuthRequired` or `Unavailable` honestly.

### Image transport

Pixiv metadata and image transport stay separate:

```text
Pixiv work/page metadata
  -> Pixiv media reference (work ID, page index, original URL)
  -> i.pixiv.cat host-rewritten URL at image-load/download time
     (pixiv.cat ID/page fallback when the origin path is unavailable)
```

The Pixiv.Cat backend describes itself as an image proxy and requires its own
server-side Pixiv refresh token; that token must never enter Latte. See the
[Pixiv.Cat backend](https://github.com/pixiv-cat/pixivcat-backend). Latte keeps
the original Pixiv URL for canonical sharing and diagnostics. When an origin
image URL is available, the default image candidate replaces only
`i.pximg.net` with `i.pixiv.cat`; the work-ID/page resolver remains a fallback.
The resolver trusts response `Content-Type` and `Content-Disposition` rather
than assuming the URL suffix describes the bytes.

Pixiv-Shaft's image-host design is a useful constraint here: host rewriting is
applied at image load time, original URLs remain available for sharing, and a
proxy must not inherit an unsafe direct-connect TLS bypass. See its
[`image-host.md`](https://github.com/CeuiLiSA/Pixiv-Shaft/blob/classic/docs/image-host.md).

### Scope boundary

In scope for this Pixiv experience:

- three primary illustration feeds: popular, followed updates, favorites;
- keyword illustration search as a query-driven feed;
- autocomplete and trending tags as search-entry support;
- opaque cursor pagination and refresh/error/empty/auth states;
- normalized illustration detail with multi-page image viewing;
- `pixiv.cat` image resolution and original-URL retention;
- shared Latte local save and restart-safe duplicate behavior;
- open-in-Pixiv and share actions using the canonical web URL;
- bookmark/unbookmark from detail after the account mutation gate passes.

Out of scope:

- remote follow/unfollow mutations;
- recommendations, user profiles, comments, related works, and local query
  history;
- novels, manga reader, ugoira playback, and multi-account;
- custom image mirrors and direct-origin fallback;
- Pixiv password collection or a complete Pixiv account center.

## 4. Implementation gate and order

Konachan remains parked. The Pixiv work is gated in this order:

1. Freeze the normalized illustration/page/media model and typed feed states.
2. Add fixture-backed mappers for ranking, followed-update, favorites, search,
   search support, detail, bookmark mutation, empty, auth-required, rate-limit,
   malformed, and upstream-drift responses.
3. Build the `pixiv.cat` resolver independently of metadata and verify actual
   content type/filename handling.
4. Put the three feed kinds and query-driven search behind the existing Explore
   grid/detail/download flow without introducing a Pixiv-specific navigation
   hierarchy.
5. Run a bounded authentication spike. Only then connect live account-scoped
   feeds and decide whether the selected App API contract is stable enough for
   a device slice.

The first useful acceptance story is:

```text
choose Pixiv -> open Popular -> open a work -> page through its illustrations
-> inspect artist/tags -> download one page -> return without losing context
```

The account acceptance story is separate:

```text
sign in -> open Followed updates or Favorites -> refresh -> paginate ->
open a work -> download -> sign out -> account feeds become AuthRequired
```

For both sites, a transport failure must remain distinguishable from an empty
feed, a blocked content item, an authentication requirement, and a local
download duplicate.
