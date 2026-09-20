# Concept: Focused yande.re client

- **Slug**: `latte-yandere-client`
- **Created**: 2026-09-20
- **Recommended option**: Android-first Latte vertical slice

## Options

### Option A — Continue Dreamland

- **Sketch**: Finish the existing Tauri client and narrow its default experience
  to yande.re rather than starting another application.
- **Appetite**: medium
- **Trade-offs**: Reuses substantial working protocol, download, state, and test
  code. It retains the Rust/Tauri/React stack and a broad capability/runtime
  model that did not produce the owner's desired finished product.
- **Rabbit holes**: Retrofitting a small product boundary onto existing auth,
  cache, queue, persistence, pool, and placeholder-site surfaces; deciding which
  working features to remove.

### Option B — Android-first Latte vertical slice

- **Sketch**: Build one Flutter product around the daily journey: open to an
  image grid, search with yande.re expressions, inspect one post, and save a
  chosen variant. Treat yande.re as the only product site. Keep site-qualified
  identity, opaque queries, continuation, and media resolution behind a narrow
  adapter boundary, without building a generic plugin system.
- **Appetite**: medium
- **Trade-offs**: Matches the requested Dart rewrite and original Android
  context, offers a coherent smaller product, and can reuse lessons rather than
  code from Dreamland. It duplicates some proven domain work and depends on a
  brittle external API with no SLA.
- **Rabbit holes**: Multi-site abstraction, image caching, download background
  jobs, authentication, explicit-content policy UX, animations/design polish,
  and premature package splitting.

### Option C — Use the web and stop client work

- **Sketch**: Keep the research as a record, use yande.re's website, and invest
  no further engineering until a stronger recurring need appears.
- **Appetite**: small
- **Trade-offs**: Avoids duplication and external-API maintenance. It leaves the
  owner's stated native-client and Dart-learning goals unmet.
- **Rabbit holes**: None technically; the risk is returning later and repeating
  the same unstructured rewrite attempt.

## Recommendation

Proceed with Option B only after two short feasibility gates pass: a minimal
Dart transport test must fetch and decode the anonymous post endpoint, and the
Android Flutter toolchain must build and run a stock application. This option
best matches the owner's requested language, original product context, and need
for a finishable experience. Its appetite is capped at a four-stage journey;
anything else requires a later spec.

## Out of Scope (for the recommended option)

- authentication, favorites, voting, uploads, pools, and archive downloads;
- databases, saved-query history, download queues, background work, and cloud
  synchronization;
- a second production adapter or general plugin SDK;
- iOS, desktop, and web release claims;
- redesigning yande.re's query grammar or content taxonomy;
- parity with Dreamland or the old Moebooru application.

## Assumptions to Validate

- Android is the owner's preferred first product platform.
- Anonymous yande.re post and media requests work reliably from Dart/Android.
- The owner accepts a deliberately small milestone without accounts or local
  history.
- Saving into one user-selected library directory is sufficient for v1.
- A single Flutter package can contain the first adapter cleanly.
- Spec Kit artifacts will replace the earlier `docs/PROJECT-SPEC.md` and
  `tasks/` plan as the execution source of truth rather than coexist with them.
