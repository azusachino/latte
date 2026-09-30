# Changelog

## 0.1.3 - 2026-09-30

### Added

- Release APK signing automation via GitHub Actions with dedicated release keystore and SHA-256 checksums (#11).
- In-app update checker querying GitHub Releases API with changelog dialog and streamed APK downloader (#11).
- Android package installer integration via `FileProvider` with unknown sources permission handling.
- Minimum Android version (`minSdk`) compatibility protection and minimum supported version check for mandatory updates.

## 0.1.2 - 2026-09-29

### Added

- Multi-select batch download at the Explore screen across all platforms (Yande, Konachan, Pixiv): long-press any card to enter multi-select mode and select up to 10 artworks with a bottom-right floating download action, downloading the first page (`p0`) for multi-page Pixiv works.

### Fixed

- Clear stale authentication-required errors across all Pixiv feeds upon login so that background tabs (such as Following) unblock and reload cleanly instead of staying in an error state (#9).
- Reset user-specific Pixiv feeds upon logout to avoid displaying stale session state.
- Elevated Explore floating batch download button with navigation bar padding to prevent obstruction on gesture navigation devices.

## 0.1.1 - 2026-09-27

### Fixed

- Resolved Pixiv multi-image download page index mismatch where proxy URL was
  resolving to the previous page.
- Raised default toast vertical offset so download notifications do not obstruct
  the multi-image page swiper.
- Attached `Referer` headers for Pixiv CDN download worker requests.

## 0.1.0 - 2026-09-26

### Added

- Added konachan.net as a third Moebooru platform: the Yande plugin and API
  are parameterized by site identity, so browsing, search, pools, and favorite
  tags work with no new transport code. konachan.com remains behind its
  Cloudflare wall.
- Added Pixiv author follow/unfollow from the author works view, with the
  current follow state read from Pixiv and the Following feed refreshed after
  a change.
- Added local favorite tags and recent searches for yande.re, with autocomplete
  suggestions from tag counts, a Favorite Tags view reached from the toolbar
  star, long-press chip favoriting, and the star toggle in the search bar.
- Pixiv now opens on the Following tab by default.

### Changed

- Local settings moved from SharedPreferences to Preferences DataStore with a
  one-time migration.
- The Explore layer now consumes the platform-neutral plugin feed contract
  (`PluginFeedSource`) instead of Pixiv transport types.

### Fixed

- Platform switching now snaps the pager to the selected tab so the stale
  page cannot override the Pixiv Following default.
- Pixiv feed failures are logged (`LatteExplore`) for device diagnostics.
- Resolved coroutine return label scoping in `ExploreViewModel.loadPoolCover()`.

## 0.0.3 - 2026-09-21

### Added

- Added the bounded Pixiv experience: browser-first OAuth, Popular, Following,
  Favorites, illustration search, multi-page detail viewing, and `pixiv.cat`
  image fallback resolution.
- Added Pixiv author-work navigation from detail metadata and platform-local
  Safe Mode filtering for Pixiv `x_restrict` results.
- Added the Material 3 platform palette/switcher and source-local feed state.
- Enabled `android:largeHeap="true"` for image-heavy gallery browsing.

### Fixed

- Preserved exact work index and multi-page index (`initialPageIndex`) across
  nested author works and tag search back navigation.
- Followed continuation cursor automatically when Safe Mode filters all items on
  a page.
- Fixed HTTP 5xx errors mapping to `TransportFailure` distinct from 429 rate limits.
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
- Preserved the originating detail screen when opening Pixiv author works;
  Back now returns through author works to the same detail page before the
  source feed, including during animated transitions.
- Followed PixEz's direct Pixiv image transport: use official medium URLs for
  grid previews with the required Referer, retain full URLs for detail, and
  keep `i.pixiv.re` plus the ID resolver as fallbacks.
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
