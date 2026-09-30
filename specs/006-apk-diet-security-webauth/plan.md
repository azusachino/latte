# Implementation Plan: 006 APK Diet, Security Hardening, and Web Auth

## Proposed Phases

### Phase 1: APK Footprint Optimization

- [ ] Task 1.1: Build `LatteIcons.kt` with the required vector icons and migrate all UI usages away from `material-icons-extended`.
- [ ] Task 1.2: Remove `androidx.compose.material:material-icons-extended` from `app/build.gradle.kts`.
- [ ] Task 1.3: Enable `isMinifyEnabled = true` and `isShrinkResources = true` in `app/build.gradle.kts`.
- [ ] Task 1.4: Author production `app/proguard-rules.pro`.
- [ ] Task 1.5: Update `Makefile` to include `assembleRelease` in `make validate`.

### Phase 2: Network & Storage Security

- [ ] Task 2.1: Add `res/xml/network_security_config.xml` and register in `AndroidManifest.xml`.
- [ ] Task 2.2: Implement dynamic DNS-over-HTTPS (DoH) fallback in `OkHttpProvider.kt`, removing the hardcoded IP.
- [ ] Task 2.3: Add optional HTTP proxy configuration to `LattePreferences` and wire into `OkHttpProvider.client`.
- [ ] Task 2.4: Harden `SecurePluginStorage.kt` and `PersistentCookieJar.kt` against Android Keystore hardware desynchronization exceptions.

### Phase 3: Web-Based Seamless Auth

- [ ] Task 3.1: Implement `MoebooruLoginActivity` with hardware-accelerated WebView, Autofill support, and `CookieManager` interception.
- [ ] Task 3.2: Register `MoebooruLoginActivity` in `AndroidManifest.xml` (`exported = false`).
- [ ] Task 3.3: Wire "Log in with Web" and "Import Session Cookie" into `PluginLoginDialog.kt`.

### Phase 4: Verification & Benchmarking

- [ ] Task 4.1: Run `make check` (unit tests and markdown formatting).
- [ ] Task 4.2: Run `make lint`.
- [ ] Task 4.3: Run `make validate` (debug + release assembly with full R8).
- [ ] Task 4.4: Benchmark release APK size and verify it is under 12MB.
