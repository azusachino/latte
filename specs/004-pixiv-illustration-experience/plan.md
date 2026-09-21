# Implementation Plan: Pixiv illustration experience

**Branch**: `research/konachan-pixiv` | **Date**: 2026-09-21
**Spec**: [spec.md](spec.md)

## Summary

Implement Pixiv as a bounded second source inside Latte's existing Android
application. The first vertical slice normalizes Pixiv illustration responses
into the existing grid/detail/save journey, resolves page media through
Pixiv.Cat, and keeps protocol errors explicit. Search, account-scoped feeds,
bookmark mutation, author works, Safe Mode filtering, platform switching, and
the redesigned account screen are then added behind the same seams.

The implementation follows Latte's downsized Spec Kit convention: this plan
and one flat `tasks.md` drive the work; the dated research note remains the
source record for protocol evidence. No separate module, service, database, or
generated contract tree is introduced.

## Technical Context

**Language/Version**: Kotlin/JVM 17

**Primary Dependencies**: Jetpack Compose Material 3, AndroidX Lifecycle,
Coroutines, OkHttp 4.12, kotlinx.serialization, Coil 3, WorkManager, Android
Keystore-backed secure storage

**Storage**: Existing encrypted `PluginStorage` for Pixiv session fields;
MediaStore/WorkManager for local image saves; no new database

**Testing**: JUnit 4, kotlinx-coroutines-test, MockWebServer, `make check`

**Target Platform**: Android API 29+, compile/target SDK 35, one `app` module

**Project Type**: Native mobile application

**Performance Goals**: Preserve existing grid/detail behavior; avoid blocking
the main thread; cancel stale source requests; keep platform transitions short
and reduced-motion safe

**Constraints**: Treat all Pixiv responses as untrusted; keep original and
proxy media identities separate; never persist a Pixiv password or Pixiv.Cat
service token; do not implement Konachan in this feature

**Scale/Scope**: One owner, one active account per platform, four Pixiv feed
entry points plus a bounded author-works feed, fixture-backed protocol
coverage, and a bounded live metadata/auth spike before advertising account
feeds as verified

## Constitution Check

### Pre-implementation gate

- **I — Product Slice First**: Pass. US1 is a runnable Popular → detail → save
  journey and is implemented before the account-only surfaces.
- **II — Test-First at Public Seams**: Pass. Every adapter/model/state behavior
  begins with a failing JUnit or MockWebServer test; `make check` is the slice
  gate.
- **III — Site Policy in Adapters**: Pass. Pixiv paths, response mapping,
  cursors, auth headers, and Pixiv.Cat URL resolution stay out of Compose.
- **IV — Evidence Before Claims**: Pass. Fixtures prove deterministic behavior;
  live Pixiv access remains explicitly gated and opt-in.
- **V — Simplicity**: Pass. Work remains in the existing `app` module and uses
  Kotlin/Compose/OkHttp facilities already present.
- **VI — Device-First Ergonomics**: Pass with an explicit gate. The first
  Explore/platform-switch slice must be installed on hardware before UI work is
  declared complete.

### Post-design gate

The approved design keeps the same six gates. The only deliberate extension is
source-neutral fields on the existing `Post` seam, because the current Compose
journey and WorkManager saver already consume that public model. A separate
Pixiv domain module would violate the simplicity gate without improving the
owner-visible slice.

## Project Structure

### Feature artifacts

```text
specs/004-pixiv-illustration-experience/
├── spec.md
├── plan.md
└── tasks.md
```

### Source and tests

```text
app/src/main/kotlin/com/azusachino/latte/
├── data/model/                 # shared Post fields and Pixiv DTO/domain mapping
├── data/network/               # Pixiv API, Pixiv.Cat resolver, result classification
├── plugin/                     # declared auth flows and Pixiv account plugin
├── ui/account/                 # Platforms & accounts and auth entry points
├── ui/explore/                 # platform-aware feed state and switcher
└── ui/detail/                  # Pixiv page identity/canonical actions

app/src/test/kotlin/com/azusachino/latte/
├── data/model/                 # mapper and identity tests
├── data/network/               # MockWebServer adapter/resolver tests
├── plugin/pixiv/               # session/auth lifecycle tests
└── ui/explore/                 # platform state tests where public seams allow
```

## Delivery order

1. Freeze the source-neutral model and error/result seams with tests.
2. Ship fixture-backed Pixiv Popular/detail/page transport and connect it to
   the existing Explore grid/detail/save flow.
3. Add Pixiv search, autocomplete, trending tags, and opaque cursor paging.
4. Add encrypted token import, refresh/session invalidation, followed and
   Favorites feeds, and bookmark mutation.
5. Add the author-works action and apply the global Safe Mode contract across
   normalized Pixiv results.
6. Add the platform picker, platform-local state restoration, and redesigned
   account manager.
7. Run the live/auth spike, install a debug APK on hardware, and complete the
   project gates. Keep unverified live behavior visibly gated.

## Complexity Tracking

No constitution violations. The implementation intentionally reuses the
existing `Post`, `SitePlugin`, Compose grid, detail pager, WorkManager, and
secure-storage seams instead of adding a new module or persistence layer.
