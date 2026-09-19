# Technical research: yande.re core journey

## Decision: pin Flutter, not a second standalone Dart SDK

- **Decision**: Pin Flutter 3.47.5 with mise and use the Dart SDK bundled with
  Flutter.
- **Rationale**: Flutter owns the compatible Dart toolchain. A separate Dart pin
  creates a second version surface without a consumer.
- **Alternatives considered**: Pin Flutter and Dart independently; build a pure
  Dart package workspace before creating the app.
- **Source**: <https://docs.flutter.dev/reference/supported-platforms>

## Decision: Android API 29+ is the first product target

- **Decision**: Claim Android 10/API 29 and newer only for this milestone.
- **Rationale**: Flutter supports Android API 24-37, while API 29 provides the
  scoped-storage/MediaStore baseline needed for a public owner-visible image
  album without legacy broad storage permission handling.
- **Alternatives considered**: Flutter's minimum API 24 with legacy permission
  branches; app-private storage; macOS-first.
- **Sources**: <https://docs.flutter.dev/reference/supported-platforms> and
  <https://developer.android.com/training/data-storage/shared/media>

## Decision: inject `http.Client` and hand-write the first wire decoder

- **Decision**: Use `package:http`, inject `http.Client` into the yande.re
  adapter, construct requests with `Uri`, and decode private wire DTOs manually.
- **Rationale**: Flutter's official networking/testing recipes use an injected
  client. `package:http/testing` supplies deterministic boundary behavior
  without Mockito. One small response model does not justify generated code.
- **Alternatives considered**: Dio/interceptor stack; top-level HTTP calls;
  Mockito; `json_serializable` plus build runner.
- **Sources**: <https://docs.flutter.dev/cookbook/networking/fetch-data>,
  <https://docs.flutter.dev/cookbook/testing/unit/mocking>, and
  <https://dart.dev/libraries/serialization/json>

## Decision: no state-management or routing dependency

- **Decision**: Use Flutter SDK navigation and explicit feature controllers
  with immutable observable states for the three-screen milestone.
- **Rationale**: The active product has one feed/search state, one detail, and
  one foreground save. A framework would choose architecture before complexity
  exists and weaken the constitution's dependency gate.
- **Alternatives considered**: Riverpod, Bloc, Provider, declarative router.

## Decision: Material 3 is the product design baseline

- **Decision**: Use Flutter's Material 3 component implementations and theme
  owner roles from `ColorScheme` and `TextTheme`. Start from a restrained
  image-led seed palette, support system light/dark mode, and branch layout from
  available constraints rather than device names or locked orientation.
- **Rationale**: Material 3 is Flutter's default design language and already
  supplies accessible component states, motion, typography, and adaptive
  foundations. Latte still needs deliberate hierarchy and tokens; enabling the
  default alone is not a product design.
- **Alternatives considered**: custom component library; Material 2; third-party
  theme package; phone-portrait-only layouts.
- **Sources**: <https://docs.flutter.dev/ui/design/material>,
  <https://docs.flutter.dev/ui/widgets/material>, and
  <https://docs.flutter.dev/ui/adaptive-responsive>

## Decision: publish completed bytes through Android MediaStore

- **Decision**: Download into app-owned temporary storage, validate completion,
  then publish through a narrow Flutter platform channel into a public Latte
  album. The Android side owns `MediaStore` details and returns a content URI,
  album, and display name.
- **Rationale**: Widgets never gain filesystem authority, partial files never
  become visible media, and no broad storage framework is needed.
- **Alternatives considered**: per-save document picker, app-private directory,
  third-party gallery saver, direct path writes.
- **Sources**: <https://docs.flutter.dev/platform-integration/platform-channels>
  and <https://developer.android.com/training/data-storage/shared/media>

## Decision: keep Moebooru below the yande.re adapter

- **Decision**: Shared Moebooru request/decoder helpers are private
  implementation composition. The product depends on `SiteAdapter`, not a
  Moebooru superclass or registry/plugin SDK.
- **Rationale**: Dreamland proves site policy diverges and that a broad
  capability catalog can precede real consumers. Latte extracts only the post
  query/media behavior exercised by yande.re and a deterministic test adapter.
- **Alternatives considered**: one nullable `BooruApi`; generic plugin registry;
  copy all Dreamland capability ports.
- **Sources**: `vendor/dreamland/docs/adr/0010-solid-layer-boundaries.md` and
  `docs/research/2026-09-20-dart-moebooru-yandere.md`

## Decision: feasibility gates precede dependent implementation

- **Decision**: First run a throwaway Dart request/decoder probe and a generated
  Android Flutter build/run smoke. Before Story 3, separately prove MediaStore
  publication, duplicate detection, and failed-write cleanup with a fixture.
  Do not retain speculative app architecture from any probe.
- **Rationale**: Live 2026-09-20 checks showed `curl` receiving `200` from
  `/post.json`, while `xh` with a custom client header unexpectedly received
  `404`. The machine has ADB and Java but no installed Flutter SDK. These are
  cheap risks to retire before product code.
- **Alternatives considered**: Trust curl/reference implementations; scaffold
  the whole app and debug transport/toolchain inside the first story.

## Deferred decisions

Authentication, favorites, pools, saved-query persistence, background work,
tag suggestions, a second production adapter, and non-Android targets receive
no implementation decision in this feature.
