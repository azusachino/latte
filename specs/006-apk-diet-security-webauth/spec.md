# Feature Specification: 006 APK Diet, Security Hardening, and Web Auth

**Feature Branch**: `feat/milestone-0.2.0-diet-security-webauth`  
**Created**: 2026-09-30  
**Status**: Ready for implementation  
**Input**: [azusachino/latte#13](https://github.com/azusachino/latte/issues/13)  

## Objective

Deliver Milestone 0.2.0 across three foundational pillars:

1. **APK Footprint Optimization**: Shrink the release APK from ~47MB to <12MB (>70% reduction) by enabling R8 minification & resource shrinking, authoring ProGuard rules, and removing the bloated `material-icons-extended` dependency in favor of a local icon registry (`LatteIcons`).
2. **Security Hardening**:
   - Dynamic DNS-over-HTTPS (DoH via Cloudflare/Google) fallback in `OkHttpProvider` replacing the brittle hardcoded IP (`198.251.89.183`).
   - Explicit `network_security_config.xml` enforcing HTTPS and blocking cleartext HTTP.
   - Keystore corruption / desynchronization resilience in `SecurePluginStorage` and `PersistentCookieJar` to prevent unrecoverable launch crash loops on OEM ROMs.
   - In-app proxy settings (HTTP host & port) in `SettingsScreen` to support local proxy clients (Clash/V2Ray).
3. **Web-Based Seamless Auth**:
   - In-app `MoebooruLoginActivity` (hosting hardware-accelerated WebView) allowing 1-tap Autofill & biometric authentication on `https://<site>/user/login`.
   - Intercepts authenticated `user_id` and `pass_hash` cookies directly into `PersistentCookieJar`.
   - Direct cookie/token import sheet as an advanced fallback.

---

## Technical Design

### 1. APK Diet Architecture

- **Dependency Pruning**:
  Remove `androidx.compose.material:material-icons-extended`.
  Create `com.azusachino.latte.ui.common.LatteIcons` declaring only the ~15 icons used by the app.
- **R8 & ProGuard**:
  In `app/build.gradle.kts`:

  ```kotlin
  buildTypes {
      release {
          isMinifyEnabled = true
          isShrinkResources = true
          proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      }
  }
  ```

  `app/proguard-rules.pro` retains keep rules for `kotlinx.serialization`, Coil 3, OkHttp, Tink, and AndroidX Datastore.
- **Verification Gate**:
  Add `assembleRelease` to `make validate` to guarantee release shrinking is verified on CI before tags are cut.

### 2. Network & Storage Security

- **Dynamic DoH Resolver**:
  Implement `DnsOverHttps` fallback inside `OkHttpProvider.kt`:
  Queries `Dns.SYSTEM` first for speed and local caching; if `UnknownHostException` or lookup failure occurs, queries Cloudflare (`1.1.1.1`) or Google (`8.8.8.8`) DoH.
- **Network Security Config**:
  Declare `res/xml/network_security_config.xml`:

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <network-security-config>
      <base-config cleartextTrafficPermitted="false" />
  </network-security-config>
  ```

- **Keystore Fallback**:
  Wrap `EncryptedSharedPreferences.create` with recovery logic catching `KeyStoreException` and `GeneralSecurityException`. On unrecoverable hardware desync, securely wipe the corrupted file and instantiate clean storage without crashing the app.

### 3. Web-Based Seamless Auth (Moebooru)

- **`MoebooruLoginActivity`**:
  Dedicated activity, `exported = false`.
  Loads target site login URL (`https://yande.re/user/login` or `https://konachan.net/user/login`).
  Enables DOM storage, JavaScript, and WebView Autofill.
  `WebViewClient.onPageFinished`: checks `CookieManager.getInstance().getCookie(url)`. When `user_id` and `pass_hash` are detected, persists them into `PersistentCookieJar`, sets `RESULT_OK`, and finishes.
- **UI in `PluginLoginDialog`**:
  Primary button: "Log in with Web".
  Secondary: "Import Session Cookie" text sheet.
