# Latte

[![CI](https://github.com/azusachino/latte/actions/workflows/ci.yml/badge.svg)](https://github.com/azusachino/latte/actions/workflows/ci.yml)

Latte is a personal image-board client for [yande.re](https://yande.re/) and
[Pixiv](https://www.pixiv.net/), built with Kotlin, Jetpack Compose, Material 3,
and Coil 3. It focuses on the daily loop — explore feeds, read detail pages,
continue with a tag — with follow, favorite tags, and local saves on both
platforms. Its architecture cleanly isolates network models, domain entities,
and the presentation layer.

The Yande/account baseline is recorded in the Spec Kit artifacts under
`specs/002-account-manager/`; the Pixiv illustration experience in
`specs/004-pixiv-illustration-experience/`; favorite tags and following in
`specs/005-favorite-tags-and-following/`. Konachan remains postponed. The
0.0.1 artifacts remain the shipped baseline and historical record for the
anonymous browse journey.
Material 3 is the visual foundation, preserving the essential Moebooru/Yande
discovery workflow, dataflow, and gestures.

## Start here

- [Feasibility decision](.specify/assessments/latte-yandere-client/decision.md)
- [Favorite tags & following specification](specs/005-favorite-tags-and-following/spec.md)
- [Pixiv illustration specification](specs/004-pixiv-illustration-experience/spec.md)
- [0.0.2 account-manager specification](specs/002-account-manager/spec.md)
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
- **Table-Style Metadata**: Clean key-value information sheet displaying resolution, color-coded rating, score, date (`yyyy-MM-dd HH:mm`), file size, and clickable author works/search.
- **Colorful Tag Chips**: Deterministic 16-color palette with comfortable touch targets; tap any tag to immediately search, long-press to favorite it (favorited tags are visually distinct).
- **Favorite Tags**: Platform-bound saved tags, favorited from detail pages, search chips, or the search-bar star; the Favorite Tags view opens each tag's feed on its own platform for update checks.
- **Pixiv Following**: Follow/unfollow artists from their works view; Pixiv opens on the Following tab with followed updates first.
- **Search Support**: Recent searches and tag autocomplete on yande.re; trending tags and autocomplete on Pixiv.
- **Safe Mode**: User-configurable toggle under Settings (default: safe). Yande adds `rating:safe`; Pixiv keeps only normalized `x_restrict == 0` results and advances through cursors when a page is filtered out.
- **Reliable Saves**: Background downloads via Android WorkManager into `Pictures/Latte` with duplicate prevention and notifications.

## Development

Latte uses the Gradle, Java, Android SDK, and Markdown tooling pinned in
`.mise.toml`:

```bash
mise install
make check     # Run Markdown checks and unit tests
make md-format # Apply Markdown formatting
make lint      # Run Android lint
make validate  # Run tests and assemble debug APK
make install   # Install debug APK to connected device
make dev       # Install and launch via ADB
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the contribution workflow and
[CHANGELOG.md](CHANGELOG.md) for release history.

## License

Latte is released under the [MIT License](LICENSE).
