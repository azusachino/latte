# Latte yande.re foundation tasks

> Historical input, superseded on 2026-09-20 by
> `specs/001-yandere-core-journey/tasks.md`. Do not execute this checklist.

These tasks are proposals, not authorization to implement. Each implementation
task is a vertical red-green slice and should touch no more than five files.

## Phase 0: contract approval

- [ ] Task 0: approve milestone boundaries and public seams
  - Acceptance: `docs/PROJECT-SPEC.md` records the chosen first host, content
    policy, download destination behavior, and approved test seams.
  - Verify: owner explicitly approves the updated spec.
  - Files: `docs/PROJECT-SPEC.md`, `tasks/plan.md`
  - Dependencies: none

## Phase 1: foundation

- [ ] Task 1: bootstrap the smallest checked Flutter application
  - Acceptance: stable Dart/Flutter are pinned; `make format`, `make lint`,
    `make test`, `make check`, and `make dev` are executable; the stock smoke
    test passes.
  - Verify: `make check`
  - Files: `.mise.toml`, `Makefile`, `pubspec.yaml`, `lib/main.dart`,
    `test/app_smoke_test.dart`
  - Dependencies: Task 0

- [ ] Task 2: render one normalized yande.re fixture page
  - Acceptance: a failing public adapter/controller test precedes code; a
    legacy-array fixture becomes typed post summaries; the Flutter grid renders
    stable post identity and preview media without importing wire models.
  - Verify: focused adapter and widget tests, then `make check`
  - Files: site contract, yande.re adapter, one fixture, explore controller,
    explore widget
  - Dependencies: Task 1

- [ ] Task 3: expose empty and failure states through the same flow
  - Acceptance: empty page, malformed response, offline/network failure,
    Moebooru `421`, standard `429`, and server-unavailable results produce
    distinct observable states; no raw response body reaches UI or logs.
  - Verify: focused adapter/controller/widget tests, then `make check`
  - Files: structured error model, yande.re adapter tests, explore controller,
    explore widget
  - Dependencies: Task 2

### Checkpoint: tracer bullet

- [ ] Clean checkout passes `make check`.
- [ ] Approved host renders the fixture-backed grid and every error state.
- [ ] Owner reviews the first vertical slice before live networking expands.

## Phase 2: anonymous exploration

- [ ] Task 4: page through the yande.re feed
  - Acceptance: adapter-owned continuation loads the next page; a short or
    empty page terminates; duplicate post references do not duplicate cards.
  - Verify: local-server adapter test and widget pagination test, then
    `make check`
  - Files: continuation model, yande.re adapter, fixtures, explore controller,
    explore widget test
  - Dependencies: Task 3

- [ ] Task 5: search an opaque yande.re tag expression
  - Acceptance: spaces and negative tags round-trip through the yande.re
    adapter; common code does not parse site grammar; query changes reset
    continuation and stale results cannot overwrite new results.
  - Verify: adapter request-capture test and controller race test, then
    `make check`
  - Files: query model, yande.re adapter, explore controller, search widget,
    tests
  - Dependencies: Task 4

- [ ] Task 6: inspect a post and select a media variant
  - Acceptance: detail shows normalized tags, dimensions, source, rating, and
    only available media variants; missing optional fields remain absent rather
    than fabricated; navigation preserves the current query/page.
  - Verify: mapping tests and detail journey widget test, then `make check`
  - Files: post detail model, yande.re mapping, detail controller/widget, tests
  - Dependencies: Task 5

### Checkpoint: exploration

- [ ] All deterministic tests and `make check` pass.
- [ ] Anonymous live read-only smoke verifies one page, one search, and one
  detail against dated endpoint metadata.
- [ ] No remote response body is committed as an unreviewed fixture.

## Phase 3: download

- [ ] Task 7: save one selected media variant without overwrite
  - Acceptance: the UI submits a post reference plus variant; the adapter
    resolves remote media; the save boundary writes atomically and returns
    completed, existing-target, cancelled, or failed; existing files are never
    overwritten.
  - Verify: temporary-destination integration tests and one manual host save,
    then `make check`
  - Files: download contract/service, yande.re resolver, host save adapter,
    detail action, tests
  - Dependencies: Task 6 and the Task 0 destination decision

### Checkpoint: first milestone

- [ ] Test-only non-Moebooru adapter drives explore without feature/UI changes.
- [ ] Production feature/UI code contains no yande.re or Moebooru imports.
- [ ] README and spec match shipped behavior and exact commands.
- [ ] `make check` passes from a clean checkout.
- [ ] Owner completes the browse -> search -> inspect -> download journey.
