# Implementation Plan: yande.re core journey

**Branch**: `docs/dart-rewrite-plan` | **Date**: 2026-09-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-yandere-core-journey/spec.md`

**Note**: This plan follows the `$speckit-plan` execution workflow.

## Summary

Deliver Latte's first finishable Android journey: anonymously explore yande.re
Popular by day, week, and month, move through readable anchor windows, scan a
dense aspect-preserving feed, inspect normalized post/media details in a
context-preserving pager, and save one variant into a public Latte image album.
Newest and opaque tag search remain discovery modes. Use one Flutter package, a
narrow site-neutral adapter consumed by feature controllers, private
Moebooru/yande.re wire decoding, Android MediaStore behind a save port, a
token-owned Material 3 experience, and strict vertical TDD.

## Technical Context

**Language/Version**: Flutter 3.47.5 with its bundled Dart SDK, pinned by mise

**Primary Dependencies**: Flutter SDK and `package:http`; no application state,
routing, persistence, DI, JSON-generation, or media-gallery framework

**Storage**: no database; Android MediaStore owns completed images under a
public Latte album; temporary bytes remain app-owned until publication

**Testing**: `flutter_test`, `integration_test`, `package:http/testing`, local
HTTP fixtures, and Kotlin host/unit instrumentation where MediaStore needs it

**Target Platform**: Android API 29+ for the first claimed product target

**Product Design**: Flutter Material 3 components; `ColorScheme` and `TextTheme`
semantic roles; system light/dark mode; responsive layouts selected from local
constraints; no third-party design system or custom font in milestone one

**Project Type**: single-package Flutter mobile application with a small Kotlin
host bridge for public image publication

**Performance Goals**: first usable content or terminal state within five
seconds on stable broadband; no fixture-backed interaction stall over 100 ms;
bounded page size at or below the site's documented limit of 100

**Constraints**: anonymous/read-only remote use except local saves; all returned
ratings remain visible and opaque expressions are not rewritten; no
database/user-visible queue/authentication or personal scoring; no arbitrary URL
or path authority from widgets; durable native saves use WorkManager; one
bounded retry for retryable reads

**Scale/Scope**: one owner, one production site, three product screens, one
active feed/search result set, and multiple independent native save tasks

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Product Slice First**: PASS. Each story is owner-visible and independently
  accepted; no deferred capability has placeholder production code.
- **Test-First at Public Seams**: PASS. Tasks must order failing contract,
  controller, widget, and save tests before each implementation.
- **Site Policy Stays in Adapters**: PASS. The adapter contract exposes only
  site-qualified identity, opaque query intent, continuation, normalized posts,
  media resolution, and structured failures.
- **Evidence Before Capability Claims**: PASS WITH BLOCKING GATES. Dart transport
  and stock Android build/run evidence precede story implementation; live
  read-only and device-save receipts precede support claims.
- **Simplicity Is a Gate**: PASS. One package and one runtime dependency beyond
  Flutter; no persistence, queue, DI framework, plugin SDK, or generated models.
- **Material product baseline**: PASS. The UI contract uses SDK Material 3
  components and theme roles with widget/golden acceptance at required states;
  it introduces no parallel design-system package.
- **Post-design re-check**: PASS. The data model and contracts below do not add
  a second site, persistent state, unbounded remote behavior, or hidden authority.

## Project Structure

### Documentation (this feature)

```text
specs/001-yandere-core-journey/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/           # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command)
```

### Source Code (repository root)

```text
lib/
├── main.dart
└── src/
    ├── app.dart
    ├── design/                 # Material 3 theme and responsive layout tokens
    ├── domain/                 # normalized values, states, and errors
    ├── sites/
    │   ├── site_adapter.dart   # public remote boundary
    │   ├── moebooru/           # private common wire/query mechanics
    │   └── yandere/            # site policy and production adapter
    ├── features/
│   ├── explore/            # Popular/Newest, search, detail/pager flow
    │   └── save/               # background save boundary
    └── platform/               # public-image save port

android/app/src/main/kotlin/    # MediaStore bridge only
test/
├── fixtures/yandere/           # minimal provenance-labelled responses
├── contract/                   # adapter and platform-port contracts
├── features/                   # controller and widget behavior
└── support/                    # handwritten deterministic fakes
integration_test/               # owner-visible Android journey
test/goldens/                   # reviewed Material states at bounded viewports
```

**Structure Decision**: one feature-oriented Flutter package. The `sites/`
boundary isolates external policy; `features/` owns product state and widgets;
`platform/` owns the Dart-facing save port; Kotlin contains only the Android
MediaStore implementation. There is no packages workspace or repository layer.

## Complexity Tracking

No constitution violation requires justification. Authenticated 0–3 personal
scoring is deliberately deferred to a later specification and is not represented
by the aggregate `PostSummary.score` field.
