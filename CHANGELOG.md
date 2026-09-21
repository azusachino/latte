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
  viewport, and resolved the authenticated-feed transition without a signed-out
  flash.
- Kept Pixiv feed navigation separate from page navigation inside a work.

### Verification

- Fixture and contract tests pass; the unlocked OnePlus 8 verified browser
  login, authenticated Popular rendering, author-work navigation, image cache
  re-entry, and Pixiv Safe Mode reload behavior.
- Live Followed/Favorites/bookmark/download acceptance remains open for the
  next device pass. Konachan remains postponed.

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
