# Konachan and Pixiv research

Date: 2026-09-21
Status: research complete; implementation remains gated on this note

## Question and conclusion

Latte needs a Konachan source before a Pixiv source. The immediate questions
are whether the Konachan Cloudflare page can be handled by the native client,
and whether `pixiv.cat` is enough to make Pixiv a useful experience.

The conclusions are:

1. **Do not build a Cloudflare solver.** `konachan.com` is currently returning
   a Cloudflare Managed Challenge to this workstation's non-browser HTTP
   client, including its JSON endpoint. Cloudflare documents that Challenge
   Pages return HTML for a browser to execute and are not supported by command
   line clients or automated browsers. Latte should detect this response and
   offer a browser fallback, not attempt to capture, forge, or transplant
   `cf_clearance`.
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
   first Pixiv slice should therefore be read-only and fixture-backed, with an
   explicit authentication spike before bookmarks, following, comments, or
   account-scoped feeds.

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
with `Referer: https://www.pixiv.net/`. `pixiv.cat` is therefore a valuable
image-host abstraction, but it is not a substitute for a metadata source.

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

## 3. Proposed Pixiv experience for Latte

Latte should begin with an illustration-first experience rather than a full
Pixiv clone. Novels, comments, social graphs, and multi-account can follow only
after the App API/auth contract earns a verified adapter.

### Explore

Use one Pixiv source entry with these initial destinations:

```text
Explore
  ├─ Recommended (auth or contract-dependent; show its unavailable state)
  ├─ Rankings (day first; date selection after live verification)
  ├─ Search illustrations / manga / users
  └─ Bookmarks (authenticated only)
```

The card should be artwork-first and aspect-preserving. Keep the metadata
quiet but available: title, creator, bookmark count, restriction/content
marker, page count, and an ugoira marker when applicable. Do not copy the
Moebooru tag-first interaction wholesale; Pixiv's useful identity is the work
and artist relationship.

Search and ranking are separate query identities. A search result must not
silently become a popularity-ranked result when the server rejects or limits a
sort mode. The UI should show the active mode and a retryable unavailable state.

### Detail

Use an image-first pager with these roles:

- page position and swipe between pages of one work;
- title, creator, tags, restriction, bookmark count, and creation metadata;
- bookmark/follow actions only when the adapter confirms authentication;
- related works as a secondary continuation, not as an automatic feed mutation;
- ugoira play and save as a separate media variant with progress and failure;
- local download as a device action, separate from remote bookmark state;
- “Open on Pixiv” for the canonical web experience and account settings.

Resolve each page's image through the image-host policy:

```text
metadata image URL
  -> pixiv.cat/<id>[-<page>].<extension> (default proxy)
  -> pixiv.re or pixiv.nl (user-selected fallback)
  -> direct origin URL with required Referer (opt-in fallback)
```

The resolver should preserve the server's actual `Content-Type` and filename,
not trust the nominal extension in the proxy URL. Local duplicate identity
should include `(pixiv, work ID, page, media variant/origin)`.

### Authentication and policy

Do not collect a Pixiv password in a Latte-owned form as part of this research
slice. The private App API and refresh-token flow need a dedicated auth spike.
For a web login or account-settings action, prefer the system browser/Custom
Tab; Android documents that third-party authentication should use Custom Tabs
to keep credentials in the browser context. A token handoff must be explicit
and testable before it is wired to the native repository.

R18/restriction state is a remote account/content-policy fact. It must not be
inferred from whether an image proxy returned bytes. Keep content markers,
account visibility, and local download state separate, as Latte already does
for the yande.re experience.

## 4. Implementation gate and order

No production implementation should start until these decisions are accepted:

### Konachan first

1. Keep the source identity as `konachan.com`; do not substitute or expose
   `konachan.net` in the Konachan.com flow.
2. Add a protocol fixture and `BrowserRequired` transport result before adding
   UI or account behavior.
3. Implement anonymous list/detail/download for the chosen origin. Keep the
   Cloudflare browser action as a fallback and verify it on the OnePlus 8.
4. Only after that slice is green, consider pools, score, favorites, and
   authenticated mutations.

### Pixiv second

1. Freeze a small normalized model for an illustration, page, artist,
   restriction, and media variant.
2. Build a read-only metadata adapter spike against the currently selected
   App API route, with explicit token-required and upstream-drift errors.
3. Build and test the `pixiv.cat` image resolver independently of metadata;
   preserve the original Pixiv URL for canonical sharing and use the proxy
   only at image-load/download time.
4. Ship the illustration feed/detail/download experience with proxy fallback.
5. Treat login, bookmarks, following, comments, rankings beyond the verified
   mode, ugoira, and novels as separately gated capabilities.

The first acceptance story should be:

```text
choose source -> load a bounded feed -> open work -> page through media
-> inspect creator/tags -> download one page -> return without losing context
```

For both sites, a transport failure must remain distinguishable from an empty
feed, a blocked content item, an authentication requirement, and a local
download duplicate.
