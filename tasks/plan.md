# Implementation plan: Latte yande.re foundation

> Historical input, superseded on 2026-09-20 by the plan and task graph in
> `specs/001-yandere-core-journey/`.

The active Popular-first decision and removal of Safe Mode are recorded only in
the Spec Kit feature directory; this file remains historical context.

Status: proposed. Do not implement until the owner approves
`docs/PROJECT-SPEC.md`, including its test seams and first host target.

## Overview

Build the smallest yande.re client loop in vertical slices. Start with one
fixture-backed page rendered in Flutter, then add pagination, search, detail,
and download. Each slice begins with a failing test at a public seam and ends
with `make check` green.

## Architecture decisions

- Keep one Flutter package. Split packages only after a second production
  adapter creates a real independent lifecycle.
- Make site identity plus remote ID the universal reference.
- Use small capability interfaces instead of one adapter with placeholder
  methods or descriptor booleans that can drift from implementations.
- Keep Moebooru response decoding and query construction reusable but below the
  site adapter boundary.
- Keep the UI protocol-blind and inject the selected adapter only at the
  composition root.
- Treat live-site evidence as a separate gate from deterministic fixture tests.

## Dependency graph

```text
site contract
  -> Moebooru protocol -> yande.re adapter
                           -> explore grid -> search -> detail -> download
```

## Phases

### Phase 0: approve the contract

- Confirm the capability map, first host target, content policy, download
  destination behavior, and public test seams in the project spec.
- Resolve research unknowns that would change the anonymous read-only contract.

Checkpoint: the owner approves the spec; no code exists before this point.

### Phase 1: executable foundation and tracer bullet

- Bootstrap one Flutter application with mise and Make commands.
- In one red-green slice, parse a minimal yande.re fixture through the public
  adapter and render the normalized first page in a grid.
- Add explicit loading, empty, malformed-response, and network-failure states
  through the same observable controller/UI seam.

Checkpoint: a clean checkout runs `make check`, and the first host renders the
fixture-backed grid without importing yande.re types into widgets.

### Phase 2: owner-useful anonymous exploration

- Add real page continuation and short-page termination.
- Add tag-expression search without interpreting the expression in common code.
- Add post detail and media-variant selection.

Checkpoint: adapter fixture tests and widget journeys pass; an explicit live
read-only smoke records endpoint/date/result metadata without response dumps.

### Phase 3: bounded local download

- Define download behavior with a failing test at `DownloadService.save`.
- Resolve media only from a normalized post reference and selected variant.
- Save atomically to the approved destination and return an existing-target
  result instead of overwriting.

Checkpoint: automated download tests pass in a temporary destination and the
owner completes one manual save on the first host.

### Phase 4: abstraction audit and milestone close

- Drive the explore flow with a test-only non-Moebooru adapter without changing
  feature/UI code.
- Search production feature/UI code for site-name and protocol imports.
- Update docs with actual commands, supported behavior, and live evidence.

Checkpoint: every first-milestone success criterion passes; deferred
capabilities remain absent and unadvertised.

## Risks and mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Designing for every future site recreates Dreamland's broad unfinished surface | High | Only extract contracts exercised by yande.re slices; prove substitution with a test fake, not placeholder production adapters |
| yande.re responses or access behavior drift | High | Validate untrusted JSON, keep dated minimal fixtures, map errors structurally, and require a separate live read-only receipt |
| Moebooru common code absorbs yande.re policy | High | Keep site configuration and policy in `yandere`; shared code owns only observed wire mechanics |
| Flutter dependencies choose the architecture before behavior is known | Medium | Start with SDK tooling and `package:http`; add packages only against an accepted slice need |
| Download semantics differ across host platforms | Medium | Select one first host before the slice and hide platform behavior behind one save boundary |
| Explicit content leaks into an unsafe default | High | Default to the owner-approved all-ratings mode; make Safe Mode an explicit opt-in filter and keep fixtures minimal and non-sensitive |

## Evidence gates

- Local deterministic gate: `make check`.
- Adapter gate: local HTTP server plus provenance-labelled fixtures.
- Host gate: one manual smoke on the approved first Flutter host.
- Live remote gate: anonymous, read-only calls only for this milestone.
- Any authenticated or mutating operation requires a later spec and explicit
  authorization.

## Open decisions before Task 1

- First Flutter host.
- Content policy is settled: all ratings by default; Safe Mode filters explicit
  content.
- Download destination behavior.
- Approval of the proposed public test seams.

The detailed, session-sized checklist lives in `tasks/todo.md`.
