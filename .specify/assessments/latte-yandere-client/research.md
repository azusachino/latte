# Idea Research: Latte yande.re client

- **Slug**: `latte-yandere-client`
- **Created**: 2026-09-20
- **Evidence confidence (overall)**: medium

## Users & Demand

- The repository owner is the identified first user and explicitly wants a
  maintained Dart client for yande.re plus a credible later site boundary. This
  is direct stated demand, but no broader user research exists. — [source: user
  request in this session] (confidence: high, cited)
- The old Android Moebooru repository has 91 stars and 6 forks, which shows some
  public interest but does not establish active unmet demand. Its GitHub
  metadata reports a most recent push on 2025-11-24. — [source:
  <https://github.com/yueeng/moebooru>] (confidence: medium, cited)

## Prior Art

- The old Moebooru Android app supports yande.re and Konachan and demonstrates
  the core product loop: ranked/tagged discovery, a post grid, detail viewing,
  and saving. Its site choice is largely build-flavor configuration rather than
  a runtime multi-site product. — [source:
  `refs/image-gallery-apps/moebooru` at
  `5bcf76644de594e136865eb70d486b916152de4b`] (confidence: high, cited)
- Dreamland already implements much of the same loop with explicit site
  capabilities, yande.re and Konachan adapters, downloads, local state, and a
  Tauri UI. It proves the domain can be modeled, but it also carries a large
  surface—auth, favorites, pools, queues, caches, persistence, and placeholder
  sites—before the owner accepted the product as finished. — [source:
  `vendor/dreamland/README.md`, `docs/API-V1.md`, and
  `docs/RETROSPECTIVE-0.1.2.md`] (confidence: high, cited)
- Latte's first planning pass found a smaller reusable boundary: site-qualified
  post identity, opaque site query syntax, adapter-owned continuation, private
  wire DTOs, explicit capabilities, and download authority based on a post
  reference rather than an arbitrary URL. — [source:
  `docs/research/2026-09-20-dart-moebooru-yandere.md`] (confidence: high, cited)

## Market & Context

- The primary alternative is to continue Dreamland rather than rewrite. That
  avoids another greenfield application but keeps a Rust/Tauri/React stack and
  the larger runtime shape that the owner associates with unfinished product
  work. — [source: `vendor/dreamland/README.md` and user request] (confidence:
  high, cited)
- A smaller Android-first Flutter client better matches the original product
  context and the owner's Dart rewrite intent. This is a concept hypothesis,
  not yet observed product behavior. — [ASSUMPTION] (confidence: medium)
- Doing nothing leaves two unsatisfactory choices: an aging reference app or a
  broader Dreamland implementation that did not produce the desired finished
  experience. — [source: user request and owned repositories] (confidence:
  high, cited)

## Data & Constraints

- yande.re's first-party API documents one-based post pages, tag expressions,
  and a maximum page size of 100. Its documented error taxonomy includes custom
  `421` throttling, while it does not publish a request-rate SLA. — [source:
  <https://yande.re/help/api>] (confidence: high, cited)
- A read-only `curl` probe on 2026-09-20 returned `200` JSON from
  `/post.json?limit=1` and `/tag.json?...`; an `xh` probe with a custom
  `User-Agent` unexpectedly returned `404` for post/help routes while tags still
  worked. The cause is unresolved, so client headers and transport behavior must
  be verified with a Dart spike before product implementation. — [source: live
  local probes, 2026-09-20] (confidence: high for observation, low for cause)
- Flutter stable `3.47.5` and Dart `3.13.4` are available through mise but not
  installed for this repository. The machine has ADB 37 and Java 21; Android is
  therefore plausible, but a Flutter doctor/build has not yet proved the full
  host toolchain. — [source: local `mise latest`, `adb version`, and
  `java -version`, 2026-09-20] (confidence: high, cited)
- Flutter officially supports Android and desktop hosts, but a product should
  claim only platforms exercised by its own build and device acceptance gates.
  — [source: https://docs.flutter.dev/reference/supported-platforms]
  (confidence: high, cited)
- Spec Kit's assessment and SDD workflows are explicitly independent: an idea
  assessment can stop with `kill` or `needs-clarification`; a `go` handoff then
  enters constitution → specify → clarify → plan → checklist → tasks → analyze.
  — [source: https://github.com/github/spec-kit/blob/d4229c071c7ea3885b43e8a7739847300f618f13/docs/guides/assessment.md]
  (confidence: high, cited)

## Evidence Against the Idea

- Dreamland already exists and has recent owner activity. A rewrite risks
  duplicating working protocol and download logic while producing a third
  incomplete client. — [source: <https://github.com/azusachino/dreamland> and
  `vendor/dreamland`] (confidence: high, cited)
- One intended user is enough for a personal tool but insufficient evidence for
  a broader product or ecosystem investment. — [source: current user evidence]
  (confidence: high, cited)
- yande.re provides an old, partially documented API with no published rate or
  availability contract; header-sensitive probe behavior could make a client
  brittle. — [source: https://yande.re/help/api and 2026-09-20 probes]
  (confidence: high, cited)
- "Future multi-platform support" can invite speculative abstraction. No
  second target site has supplied acceptance fixtures or live evidence yet. —
  [source: Dreamland retrospective and current Latte scope] (confidence: high,
  cited)
- Full Spec Kit artifacts overlap with the existing `docs/PROJECT-SPEC.md` and
  `tasks/` files. If both remain authoritative, workflow overhead and drift will
  increase instead of decreasing ambiguity. — [source: repository structure
  and Spec Kit artifact model] (confidence: high, cited)

## Gaps & Open Questions

- [NEEDS CLARIFICATION: Does a minimal Dart HTTP probe reproduce curl's
  successful post request and identify the xh discrepancy?]
- [NEEDS CLARIFICATION: Does `flutter doctor` plus a generated hello-world build
  pass for the chosen first host?]
- [NEEDS CLARIFICATION: Is Android the intended first product platform?]
- [NEEDS CLARIFICATION: Which existing planning files should be superseded by
  Spec Kit artifacts to avoid two sources of truth?]
- [NEEDS CLARIFICATION: What concrete second site, if any, should validate the
  abstraction after the yande.re milestone?]

## Sources

- <https://yande.re/help/api> (host: yande.re, policy: confirmed by user task)
- <https://github.com/yueeng/moebooru> (host: github.com, policy: allowlisted)
- <https://github.com/azusachino/dreamland> (host: github.com, policy: allowlisted)
- [Flutter supported platforms](https://docs.flutter.dev/reference/supported-platforms)
  (host: docs.flutter.dev, policy: confirmed by user task)
- [Spec Kit assessment guide](https://github.com/github/spec-kit/blob/d4229c071c7ea3885b43e8a7739847300f618f13/docs/guides/assessment.md)
  (host: github.com, policy: allowlisted)
- `docs/research/2026-09-20-dart-moebooru-yandere.md`
- `vendor/dreamland/`
- live local probes and toolchain inspection on 2026-09-20
