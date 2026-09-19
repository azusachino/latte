# Dart/Flutter Moebooru client research

Date: 2026-09-20

## Question and conclusion

How should Latte rewrite the old Moebooru client in Dart/Flutter, ship yande.re
first, preserve a credible path to other image-board platforms, and practice
test-driven development?

Build the first vertical slice around a small site-neutral `ImageBoard` port
and one `YandereImageBoard` adapter. Keep Moebooru wire types private to a
protocol module and expose only normalized posts, page-number pagination, and
explicit optional capabilities. Do not reproduce Dreamland's full capability
catalog, authentication bridge, SQLite queues, or download runtime before the
first browse/search/detail slice proves they are needed.

The initial acceptance path should be anonymous and read-only:

```text
enter tag expression -> fetch page -> render thumbnails -> open post -> save original
```

Every protocol behavior should begin as a failing fixture test. A small number
of opt-in live contract checks should verify assumptions that fixtures cannot,
but they must not be the ordinary test suite.

## Source scope

This note uses the [yande.re API help](https://yande.re/help/api), the live
read-only yande.re JSON endpoints, the upstream
[Moebooru source repository](https://github.com/moebooru/moebooru), official
[Dart](https://dart.dev/libraries/serialization/json) and
[Flutter](https://docs.flutter.dev/) documentation, and owned Dreamland code
and decisions. Live observations below are dated; they are evidence to encode
as fixtures, not permanent guarantees.

## yande.re protocol baseline

### Posts and queries

The first-party API page identifies itself as `API 1.13.0+update.3`, describes
the API as *mostly* compatible with Danbooru 1.13.0, and supports JSON by
replacing `.xml` with `.json`. It documents `GET /post.json` with:

- `limit`, with a documented hard maximum of 100;
- one-based `page` pagination;
- `tags`, accepting the same tag combinations and meta-tags as the website.

This means Latte must preserve a user's tag expression as site syntax. It
should not parse it into a universal search AST in v1. Content policy can be an
application value, but the yande.re adapter is responsible for translating it
to a rating term without changing the rest of the expression.

A read-only probe on 2026-09-20 confirmed that
[`/post.json?limit=1`](https://yande.re/post.json?limit=1) returns a top-level
JSON array. The observed post contained identity and timestamps; width/height;
space-delimited tags; `s`, `q`, or `e` rating; score/source/status; MD5 and file
extension/size; parent/child flags; and preview, sample, JPEG, and original file
URLs with their dimensions and sizes. Decoder fixtures should include absent
or null optional URLs and fields, because the API page does not promise every
observed field.

There is no documented direct post-by-ID JSON route. Upstream Moebooru still
has an open request for one; the portable lookup is
`/post.json?tags=id:<id>` ([upstream routes](https://github.com/moebooru/moebooru/blob/321fd52a037ada9bb23a7f30ef544f76e57cec93/config/routes.rb#L137-L165),
[upstream issue #144](https://github.com/moebooru/moebooru/issues/144)). Treat
lookup as a query returning zero or one post, not as `/post/<id>.json`.

### Tags and adjacent resources

The API page documents `GET /tag.json` with `limit`, `page`, `order`, `id`,
`after_id`, exact `name`, and `name_pattern`. It documents
`GET /tag/related.json` with `tags` and an optional type restriction. These can
support suggestions later, but they are a separate capability from post query.

The same page documents JSON-capable controllers for artists, comments, wiki,
notes, and users. Upstream routes also expose `/pool.json` and
`/pool/show.json?id=...`. They are evidence of the wider Moebooru protocol, not
Latte v1 scope. Pool ZIP behavior used by Dreamland is not described on that
API page; it must remain yande.re-specific until verified from live routes or
current first-party source.

### Authentication and mutations

The API page says authenticated actions may pass `login` and `password_hash`
([source permalink](https://github.com/moebooru/moebooru/blob/321fd52a037ada9bb23a7f30ef544f76e57cec93/app/views/help/api.en.html.erb#L90-L98)).
The hash is a salted SHA-1 of a site-specific string, not a plain password
hash. It also warns that possession of the hash may allow account
impersonation. State-changing calls generally use POST.

That legacy scheme is unsuitable as the first milestone: it would require
handling a reusable credential and the documentation does not establish how
modern yande.re sessions, CSRF protection, or mobile login should work. Keep
authentication out of the first vertical slice. When it is scheduled, prefer
a browser/session-cookie experiment behind an opaque credential store, and
never put cookies or password material in logs, ordinary preferences, fixture
files, or domain models.

Dreamland currently infers a yande.re session from `user_info`/`user_id`
cookies and implements favorites via site-specific routes
([adapter](../../../dreamland/crates/dreamland-site-yandere/src/lib.rs)). That
is useful prior-art evidence, not a confirmed public contract; Latte must
re-verify it with an authorized test account before adopting it.

### Access and rate caveats

The official API page documents the 100-post page cap but publishes no request
rate, retry-after policy, client identification rule, CORS guarantee, or
availability SLA. Anonymous probes on 2026-09-20 returned `200` for the API help
and post list and did not expose a rate-limit header. Absence of a header is
not evidence of unlimited access.

The first-party help source documents its response taxonomy, including `420`
invalid record, `421` throttled, `422` locked, `423` already exists, `424`
invalid parameters, and `503` unavailable
([source permalink](https://github.com/moebooru/moebooru/blob/321fd52a037ada9bb23a7f30ef544f76e57cec93/app/views/help/api.en.html.erb#L38-L85)).
The client should therefore:

- use a truthful product/version `User-Agent` where the platform permits it;
- have bounded timeouts and cancellation;
- map authentication/authorization, not-found, invalid parameters, throttling,
  5xx, transport, and decode failures separately;
- recognize Moebooru's documented custom `421` throttled response as well as
  standard `429`; honor `Retry-After` if present and otherwise use bounded
  backoff only for explicitly retryable reads such as `421`/`429`/`503`;
- avoid speculative prefetch and cap concurrent media downloads;
- never attempt to bypass bot protection.

Browser/Web is not an assumed target. Flutter's networking docs note that
platform clients differ, and yande.re does not document cross-origin browser
access. Desktop/mobile HTTP is the safe planning baseline until web CORS is
tested.

## What is common and what is site-owned

"Moebooru compatible" is a protocol family, not a promise that two deployments
have identical features or policy.

Safe common layer:

- request construction for `/post.json`, `/tag.json`, and related-tag routes;
- page/limit/tags parameter encoding;
- tolerant decoding of the common post fields;
- rating and media-variant mapping;
- a typed protocol error carrying HTTP status and decode context.

Site adapter layer:

- base URLs and allowed media hosts;
- content-policy defaults and display name;
- browser routes;
- authentication/session interpretation;
- favorites, pools, ZIP archives, and any undocumented routes;
- capability declaration and live-acceptance evidence.

Application/domain layer:

- `(siteId, remoteId)` post identity;
- browse/search/detail intent and page state;
- download requests expressed as post plus variant, never an arbitrary URL;
- user-owned saved queries and local download history.

Avoid a `MoebooruSite` superclass full of nullable methods. Use one small
required browse/query port plus optional interfaces such as `TagSuggestions`
or `Authentication` only when a shipped flow consumes them. A second adapter
should reuse the Moebooru protocol implementation by composition, while
retaining its own identity and policies.

## Dreamland lessons

Dreamland's mature direction is worth retaining:

- its [site/runtime ADR](../../../dreamland/docs/adr/0004-site-runtime-boundary.md)
  keeps HTTP, decoding, validation, credentials, and filesystem authority out
  of UI code;
- its [API contract ADR](../../../dreamland/docs/adr/0007-api-v1-site-contract.md)
  models post query as required and features such as suggestions, lookup,
  favorites, and collections as optional capabilities;
- its [SOLID ADR](../../../dreamland/docs/adr/0010-solid-layer-boundaries.md)
  uses a registry/composition root and requires descriptor flags to agree with
  real ports;
- its implementation normalizes identity as `(site, post ID)`, keeps remote
  favorites distinct from local state, and resolves media variants inside the
  adapter ([core contracts](../../../dreamland/crates/dreamland-core/src/lib.rs),
  [yande.re adapter](../../../dreamland/crates/dreamland-site-yandere/src/lib.rs)).

Dreamland also shows where Latte should be smaller. Its current core exposes a
large capability matrix, runtime session services, cache/download queues, and
several platform skeletons before multiple production adapters validate every
seam. The architecture document explicitly says larger typed-service changes
should wait for a second consumer
([ADR 0009](../../../dreamland/docs/adr/0009-runtime-operation-context-and-common-services.md)).
Latte should apply that lesson earlier: one adapter, one slice, minimal ports,
and extraction only when a second implementation demonstrates shared behavior.

## Dart/Flutter implementation seams

### Networking

Flutter's official networking recipes use `package:http` and recommend passing
an `http.Client` into network code instead of calling top-level `http.get`; the
injected client can be replaced in tests
([fetching](https://docs.flutter.dev/cookbook/networking/fetch-data),
[mocking](https://docs.flutter.dev/cookbook/testing/unit/mocking)). Define a
very small transport boundary around the operations Latte actually needs, or
inject `http.Client` directly into the adapter. Do not introduce a generic
interceptor framework in milestone one.

Close owned clients, encode query parameters with `Uri`, and keep base/media
host allowlists in the yande.re adapter. Cancellation support must be chosen
and tested before promising cancelable requests; `package:http` injection alone
does not define that behavior.

### Serialization

Dart's `dart:convert` provides `jsonDecode`, while Flutter's JSON guide
distinguishes manual decoding from generated model code
([Dart JSON](https://dart.dev/libraries/serialization/json),
[Flutter JSON](https://docs.flutter.dev/data-and-backend/serialization/json)).
For the first small response model, a hand-written private wire decoder is the
cheapest choice and makes tolerance rules visible in tests. Reconsider
`json_serializable` only when model count or repetitive mapping earns the
build-runner cost.

Never deserialize remote JSON directly into the domain `Post`. Decode a
`YanderePostDto`, validate required identity and usable media, then normalize.
Large result parsing can later move to an isolate; Flutter documents `compute`
for work that risks skipping frames, while noting that web runs the callback on
the current event loop
([`compute`](https://api.flutter.dev/flutter/foundation/compute.html)). Measure
before adding this complexity.

### Storage

Do not choose a database in the research phase. The first slice can keep query
and navigation state in memory and save media through a narrow `FileStore`
port. If durable saved queries or download history enter scope, Flutter's
official SQLite recipe uses `sqflite` and a repository-style database boundary
([SQLite](https://docs.flutter.dev/cookbook/persistence/sqlite)). Credentials
need platform secure storage, not SQLite or shared preferences; the exact
package and supported platforms remain a later decision.

Downloads should accept a normalized post reference and desired variant. The
adapter resolves the known media URL; the storage service chooses and validates
the destination. This preserves the Dreamland security boundary without
recreating its queue system prematurely.

### TDD seam and test pyramid

Use fakes before generated mocks. The official Flutter testing recipe's central
point is dependency injection: deterministic substitutes prevent slow, flaky
live-service tests and expose success and failure cases. A handwritten fake
`http.Client`/transport is enough until interaction-heavy tests justify Mockito.

For each slice, follow red -> green -> refactor:

1. Add a captured, redacted yande.re fixture and a failing decoder/normalizer
   test.
2. Add failing request-construction tests for tags, page, and limit.
3. Implement the minimum adapter behavior.
4. Add repository/controller tests for loading, empty, next-page, HTTP error,
   malformed JSON, and stale-response behavior.
5. Add widget tests for the user-visible state transition.
6. Run an opt-in live contract test only for assumptions explicitly labelled
   live, record the date, and refresh the fixture deliberately.

Golden tests are appropriate after a visual language exists, not as a
substitute for behavior tests.

## Recommended first plan boundary

1. Bootstrap Flutter with pinned Dart/Flutter through mise and canonical
   `make` targets (`make test`, `make check`).
2. Write domain tests for `SiteId`, `PostRef`, rating, media variants, and
   `PostPage`.
3. Write yande.re fixture tests, then implement the private wire DTO and mapper.
4. Write request tests, then implement anonymous latest/tag query with page
   pagination and a maximum of 100.
5. Write controller and widget tests, then build the gallery loading/empty/
   error/content states.
6. Write detail and file-store tests, then implement original/sample selection
   and one explicit save action.
7. Run a read-only live acceptance check on supported target platforms.
8. Only then decide whether tag suggestions, saved queries/history, pools, or
   authentication is the next vertical slice.

Definition of done for the first milestone: fixture tests and Flutter tests are
green; malformed/null fields do not crash; paging and tag expressions are
covered; no secret or arbitrary URL crosses into UI/storage authority; and a
dated live check demonstrates browse, search, detail, and save on each claimed
platform.

## Unknowns requiring live verification

- Which platforms Latte will claim first (macOS, Windows, Linux, Android, iOS,
  or web), including filesystem permissions and media-host TLS behavior.
- Current yande.re rate limits, `Retry-After` behavior, acceptable concurrency,
  and whether a product `User-Agent` is required or merely courteous.
- CORS behavior if Flutter Web is considered.
- Nullability and alternate JSON envelope shapes across deleted, held,
  pending, parent/child, animated, and very old posts.
- Redirects, referer requirements, and allowed hosts for preview/sample/JPEG/
  original media downloads.
- Whether tag suggestion and related-tag responses match upstream Moebooru on
  current yande.re.
- Pools, ordered pool posts, and ZIP authorization semantics; these are not in
  the published API page.
- Current authenticated login/session cookie behavior, CSRF requirements,
  favorites mutation/list semantics, logout, expiry, and secure persistence.
- Whether another intended platform is genuinely Moebooru-compatible. The
  abstraction must be tested against a concrete second adapter before being
  generalized further.
