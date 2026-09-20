# Changelog

## Unreleased

- Hardened account and session persistence to fail closed with encrypted
  preferences, and require an authenticated Yande session cookie before saving
  credentials.
- Routed detail capabilities through the plugin manager, moved Yande pool and
  favorites query construction into the adapter, defaulted Safe Mode on, and
  replaced action failures with inline errors.
- Added exact password-hash, authenticated-login, and adapter-query regression
  coverage.

## 0.0.1 - 2026-09-20

- Added the first Yande-compatible Latte exploration and detail journey.
- Added thumbnail-first detail loading, pager swipes, and two-finger zoom.
- Added best-quality Android downloads with progress, grouped notifications,
  duplicate detection, and default-viewer opening.
