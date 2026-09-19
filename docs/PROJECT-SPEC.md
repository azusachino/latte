# Spec: Latte yande.re foundation

> Historical input, superseded on 2026-09-20 by
> `specs/001-yandere-core-journey/spec.md`. Do not maintain this as a parallel
> execution specification.

Status: draft for owner review. Implementation is not approved by this document.

## Assumptions

1. "Multiple platforms" means multiple remote image/art platforms, not a
   promise to support every Flutter host platform in the first milestone.
2. Flutter is the application shell and Dart owns domain, protocol, adapter,
   and application-service code.
3. The first milestone is anonymous and read-only except for saving an image to
   a user-selected local destination.
4. yande.re is the only production adapter in the first milestone. A fake
   adapter may prove the application seam, but no placeholder site is presented
   as supported.
5. Existing Dreamland code is prior art, not code to port line by line.

These assumptions deliberately keep the first release smaller than Dreamland.
The owner should correct them before implementation begins.

## Objective

Build a calm Flutter client that lets one owner browse, search, inspect, and
download yande.re posts. Keep remote protocol details behind a narrow site
boundary so a future adapter can supply the same application use cases without
site conditionals in widgets, controllers, persistence, or downloads.

The first milestone succeeds when the owner can:

1. open the app and browse a paged yande.re post grid;
2. search with a yande.re tag expression;
3. inspect a post and choose an available media variant;
4. download that media without overwriting an existing file; and
5. observe explicit loading, empty, offline, rate-limited, and malformed-data
   states.

## Capability map

| Module id | Responsibility | Depends on |
| --- | --- | --- |
| `site-contract` | Site-neutral identities, posts, pages, queries, media variants, errors, and capability interfaces | - |
| `moebooru-protocol` | Decode and validate shared Moebooru response shapes and construct shared query parameters | `site-contract` |
| `yandere-adapter` | yande.re configuration and site-specific endpoint, policy, and mapping decisions | `site-contract`, `moebooru-protocol` |
| `explore-flow` | Browse, pagination, search, suggestions, detail, and their observable UI states | `site-contract`, `yandere-adapter` |
| `download-flow` | Resolve a post reference and media variant, then save it through a user-owned destination | `site-contract`, `yandere-adapter`, `explore-flow` |

Build order: `site-contract` -> `moebooru-protocol` -> `yandere-adapter` ->
`explore-flow` -> `download-flow`.

`moebooru-protocol` is an implementation helper, not a site or an application
service. The UI never imports it. The map intentionally has no generic
authentication, collection, or favorite module yet.

## Proposed tech stack

- Dart and Flutter, pinned to the stable versions selected during bootstrap
  through `.mise.toml`.
- `package:http` behind a narrow transport boundary unless bootstrap research
  demonstrates a concrete need for a larger client.
- Dart's standard JSON decoding plus explicit boundary validation; code
  generation is added only if hand-written immutable models become a measured
  maintenance problem.
- Flutter test tools for unit and widget tests; a local HTTP server and checked
  fixtures for adapter integration tests.
- Platform-native save/location APIs chosen only in the download slice, after
  the first host target is confirmed.

No state-management, routing, database, or dependency-injection package is
approved by this draft.

## Proposed commands

These become executable contracts during bootstrap:

```bash
make format   # dart format --output=none --set-exit-if-changed .
make lint     # flutter analyze
make test     # flutter test
make check    # format + lint + test
make dev      # flutter run -d <approved-host>
```

## Project structure

Keep one Flutter package until a second production adapter proves a package
split is useful:

```text
lib/
  src/domain/              site-neutral values and errors
  src/sites/               capability contracts and registry
  src/sites/moebooru/      shared wire parsing/query helpers
  src/sites/yandere/       the only first-milestone production adapter
  src/features/explore/    application use cases and Flutter presentation
  src/features/downloads/  download use case and platform boundary
test/                      unit, widget, and fixture-backed adapter tests
test/fixtures/yandere/     redacted, minimal yande.re responses
integration_test/          selected owner-visible journeys
docs/                      decisions and research
tasks/                     reviewed plan and task checklist
```

Widgets depend on feature controllers/use cases. Feature code depends on the
site contract. Only the composition root selects the yande.re adapter.

## Interface rules

- Identify every post by `(siteId, postId)`; a bare remote ID is insufficient.
- Preserve the user's site query as an opaque expression. Common code must not
  claim tags, ratings, sorting, or pagination mean the same thing everywhere.
- Model pagination as a continuation value owned by the adapter. Page numbers
  are a yande.re detail, not a universal UI contract.
- Represent optional behavior with small capability interfaces. Unsupported
  behavior is absent, never a fake success or an `UnimplementedError` reached
  from the UI.
- Parse all remote responses as untrusted input at the adapter boundary.
- Use one structured error taxonomy across adapters: invalid request,
  unsupported capability, malformed response, rate limited, authentication
  required, network unavailable, and remote failure. Preserve safe diagnostic
  context without retaining response bodies or credentials.
- A download request contains a post reference and selected media variant. The
  UI does not supply an arbitrary URL or destination filename as authority.

## Testing strategy

TDD is mandatory. Each task is a vertical red -> green cycle: one failing test
through a public seam, then the smallest implementation that passes it. Do not
write all contracts and tests horizontally before a usable flow exists.

The agreed seams proposed for owner approval are:

1. `SiteAdapter.queryPosts` as the remote post-query seam;
2. the explore controller's observable state as the UI/application seam; and
3. `DownloadService.save` as the local side-effect seam.

Test policy:

- Domain tests use worked literals and public value behavior.
- Adapter tests use a local HTTP server plus minimal checked fixtures. HTTP is
  the external boundary; internal mappers are not mocked.
- Controller and widget tests use a deterministic fake site adapter because the
  remote site is the system boundary.
- Download tests use a temporary directory or fake platform save boundary and
  assert observable results, not internal call order.
- Live yande.re probes are explicit manual evidence. They do not run in CI and
  never capture account cookies, credentials, or unredacted bulk responses.
- Every fixture must cover its provenance date, endpoint family, redactions,
  and the behavior it proves.

No coverage percentage is set yet. The required bar is behavioral coverage of
every accepted success criterion and failure state at its public seam.

## Boundaries

- Always: write the failing public-behavior test first; validate remote data;
  keep site and post identity paired; run `make check` before every commit.
- Always: keep yande.re policy in its adapter and common Moebooru mechanics in
  the protocol module.
- Ask first: add a dependency, persistence/database, authentication, remote
  mutation, background downloading, or another supported site.
- Ask first: select and commit the first Flutter host target.
- Never: expose cookies or credentials to widgets, fixtures, logs, or config.
- Never: add site-name switches outside the composition root.
- Never: advertise an adapter or capability that has not passed its contract
  and live-evidence gates.

## Deferred scope

- sign-in, remote favorites, voting, uploads, and other account mutations;
- tag suggestions and related-tag discovery;
- pools and archive downloads;
- saved searches and download history persistence;
- background queues and notifications;
- a second real site;
- broad desktop/mobile distribution and store packaging.

These are candidates for later capability specs, not implied requirements of
the first abstraction.

## Success criteria

- All five owner journeys under Objective pass automated tests at the agreed
  seams and a manual smoke on the approved first host.
- A fixture-backed yande.re adapter handles legacy array and object-envelope
  post responses, short/empty pages, malformed JSON, and structured remote
  errors without leaking wire models upward.
- A test-only adapter can drive the explore flow without changing feature or UI
  code, demonstrating the site boundary without pretending a second site ships.
- No production widget imports `sites/moebooru` or `sites/yandere`.
- Downloads resolve from `(siteId, postId, variant)` and refuse to overwrite an
  existing target.
- `make check` passes from a clean checkout with the pinned toolchain.

## Open questions

1. Which Flutter host is the first owner-verified target: Android, macOS, or
   another single platform?
2. Should explicit-rated content be hidden by default, configurable, or allowed
   unfiltered for this owner-only client?
3. Should the first download slice use a save dialog every time or one
   configured library directory?
4. After the anonymous milestone, is the next capability pools or authenticated
   favorites?
