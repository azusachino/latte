# Changelog

## 0.0.3 - Unreleased

### Added

- Added the bounded Pixiv experience: browser-first OAuth, Popular, Following,
  Favorites, illustration search, multi-page detail viewing, and `pixiv.cat`
  image fallback resolution.
- Added Pixiv author-work navigation from detail metadata and platform-local
  Safe Mode filtering for Pixiv `x_restrict` results.
- Added the Material 3 platform palette/switcher and source-local feed state.

### Fixed

- Preserved successful image candidates when grid cards leave and re-enter the
  viewport, restored the active feed position after detail Back, and resolved
  the authenticated-feed transition without a signed-out flash.
- Added visible thumbnail loading, proxy fallback, and retry states so a slow
  or failed image request cannot leave a grid card or detail viewer blank.
- Kept fallback attempts in the loading state so a transient proxy failure does
  not flash `Image unavailable` before the next candidate is tried.
- Preferred the path-preserving `i.pixiv.re` candidate before the ID/page
  fallback and retained WebP extensions when constructing Pixiv fallback URLs.
- Retained Pixiv `large`-only image candidates so Favorites does not collapse
  to a single failing proxy URL.
- Kept preview fallback failures in the loading state so a Pixiv proxy miss
  cannot flash `Image unavailable` while another candidate is loading.
- Settled exploration tabs immediately so rapid switches cannot leave the
  pager between feeds while adjacent grids are composing.
- Kept Pixiv feed navigation separate from page navigation inside a work.
- Added a directional transition for inner Pixiv page changes so cached images
  do not swap invisibly.

### Verification

- Fixture and contract tests pass; the unlocked OnePlus 8 verified browser
  login, authenticated Popular rendering, author-work navigation, image cache
  re-entry, and Pixiv Safe Mode reload behavior.
- The same device pass verified authenticated Following and Favorites grids,
  bookmark toggle round-trip, WorkManager download completion, and the visible
  duplicate-save warning. Konachan remains postponed.

## 0.0.2 - 2026-09-21

### Added

- Added a Mihon-style account manager with Yande login, profile, scoring, and
  favorites flows.
- Added dedicated Favorites and Pools exploration tabs.

### Changed

- Routed detail capabilities through the plugin manager, moved Yande pool and
  favorites query construction into the adapter, defaulted Safe Mode on, and
  replaced action failures with inline errors.

### Fixed

- Added exact password-hash, authenticated-login, and adapter-query regression
  coverage.

### Security

- Hardened account and session persistence to fail closed with encrypted
  preferences, and require an authenticated Yande session cookie before saving
  credentials.

## 0.0.1 - 2026-09-20

- Added the first Yande-compatible Latte exploration and detail journey.
- Added thumbnail-first detail loading, pager swipes, and two-finger zoom.
- Added best-quality Android downloads with progress, grouped notifications,
  duplicate detection, and default-viewer opening.
