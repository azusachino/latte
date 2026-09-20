# Latte

[![CI](https://github.com/azusachino/latte/actions/workflows/ci.yml/badge.svg)](https://github.com/azusachino/latte/actions/workflows/ci.yml)

Latte is a native Android image-board client built with Kotlin, Jetpack Compose,
Material 3, and Coil 3. Its first supported site is
[yande.re](https://yande.re/); its architecture cleanly isolates network models,
domain entities, and the presentation layer.

The active product contract is recorded in the Spec Kit artifacts under
`specs/002-account-manager/`. The 0.0.1 artifacts remain the shipped baseline
and historical record for the anonymous browse journey.
Material 3 is the visual foundation, preserving the essential Moebooru/Yande
discovery workflow, dataflow, and gestures.

## Start here

- [Feasibility decision](.specify/assessments/latte-yandere-client/decision.md)
- [Active product specification](specs/002-account-manager/spec.md)
- [Active tasks & implementation record](specs/002-account-manager/tasks.md)
- [0.0.1 baseline specification](specs/001-yandere-core-journey/spec.md)
- [Spec Kit evaluation](docs/spec-kit-evaluation.md)
- [Historical Dart/Flutter research](docs/research/2026-09-20-dart-moebooru-yandere.md)

## Core journey & features

```text
Popular / Newest / Favorites / Pools / Search -> YandeApi -> Staggered Grid
  -> Detail Pager (Zoomable) -> Table Metadata & Tags -> WorkManager Download
```

- **Discovery Feeds**: Popular (Day, Week, Month, Year with `yyyy-MM-dd` date navigation) and Newest feeds with smooth horizontal tab swiping.
- **Infinite Scrolling**: Moebooru-parity continuous scrolling for Popular (`order:score date:...`), Newest, and Search.
- **Clean Image Grid**: Staggered cards preserving aspect ratio, with 1, 2, or 3 column density cycling.
- **Image-First Detail Pager**: Full-screen zoomable viewer with memory-cached preview transitions.
- **Table-Style Metadata**: Clean key-value information sheet displaying resolution, color-coded rating, score, date (`yyyy-MM-dd HH:mm`), file size, and clickable author search.
- **Colorful Tag Chips**: Deterministic 16-color palette with comfortable touch targets; tap any tag to immediately search.
- **Safe Mode**: User-configurable toggle under Settings (default: safe) that applies `rating:safe` across all feeds.
- **Reliable Saves**: Background downloads via Android WorkManager into `Pictures/Latte` with duplicate prevention and notifications.

## Development

Latte uses the Java runtime pinned in `.mise.toml`:

```bash
mise install
make check     # Run unit tests
make validate  # Run tests and assemble debug APK
make install   # Install debug APK to connected device
make dev       # Install and launch via ADB
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the contribution workflow and
[CHANGELOG.md](CHANGELOG.md) for release history.

## License

Latte is released under the [MIT License](LICENSE).
