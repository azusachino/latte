# Architecture & Design: APK Diet, Security Hardening, and Web Auth

**Date**: 2026-09-30  
**Milestone**: 0.2.0  
**Issue**: [azusachino/latte#13](https://github.com/azusachino/latte/issues/13)  
**PR**: [azusachino/latte#14](https://github.com/azusachino/latte/pull/14)  

---

## 1. Executive Summary

Milestone 0.2.0 resolves three fundamental design challenges discovered during early hardware trials:
1. **DEX Bloat**: The release APK was ~47MB, 96% of which was uncompressed DEX bytecode (45.2MB across 3 DEX files) caused by disabled R8 shrinking and the monolithic `material-icons-extended` dependency.
2. **Brittle Network Assumptions**: A hardcoded IP in `customDns` created operational risk, while cleartext HTTP traffic was not explicitly blocked via `NetworkSecurityConfig`.
3. **Frictional Authentication**: Moebooru platforms (Yande.re and Konachan.net) lacked OAuth, forcing users to type raw credentials into native text fields without Autofill, biometrics, or Turnstile support.

---

## 2. APK Diet & Binary Footprint

### The 47MB Problem
Inspection of the `0.1.3` release APK revealed:
- `classes.dex`: 32.3 MB
- `classes2.dex`: 8.5 MB
- `classes3.dex`: 6.5 MB
- Uncompressed DEX Total: **45.17 MB**
- Resources: 1.39 MB
- Native Libraries: 0.05 MB

The root cause was twofold:
1. `isMinifyEnabled = false` was set in `buildTypes.release`, leaving R8 dead-code elimination and tree-shaking completely disabled.
2. `androidx.compose.material:material-icons-extended` was included as a runtime dependency. The extended icons artifact contains tens of thousands of auto-generated vector path classes representing the entire Material Design icon library. Without R8, all of these unused icon classes were bundled into DEX.

### Architectural Solution
1. **Remove `material-icons-extended`**: Pruned the dependency from `app/build.gradle.kts`.
2. **Zero-Dependency Icon Registry (`LatteIcons.kt`)**: Implemented a lightweight object providing only the ~15 icons actually referenced in the application (`Download`, `ChevronLeft`, `ChevronRight`, `OpenInBrowser`, `StarBorder`, `GridView`, `ViewAgenda`, `ViewColumn`, `Code`, `Security`, `SystemUpdate`, `AccountCircle`, `Visibility`, `VisibilityOff`), using pure Compose `ImageVector.Builder`.
3. **Enable R8 Minification & Resource Shrinking**:
   ```kotlin
   buildTypes {
       release {
           isMinifyEnabled = true
           isShrinkResources = true
           signingConfig = signingConfigs.getByName("release")
           proguardFiles(
               getDefaultProguardFile("proguard-android-optimize.txt"),
               "proguard-rules.pro"
           )
       }
   }
   ```
4. **ProGuard Rules (`app/proguard-rules.pro`)**: Added strict retain rules for `kotlinx.serialization` serializers, Coil 3 networking, OkHttp public suffixes, Tink/AndroidX crypto, and Datastore.

### Benchmark Results
- **DEX size**: Reduced from 45.17 MB (3 DEX files) to **4.45 MB** (1 clean `classes.dex`).
- **Release APK size**: Reduced from **47 MB to 5.8 MB** (**87.6% size reduction**).
- **Compilation Speed**: Fast-forwarded local debug dexing and eliminated extended icon parsing overhead.

---

## 3. Network & Storage Security Hardening

### Dynamic DNS-over-HTTPS (DoH) Resolver
The legacy `OkHttpProvider.kt` used a hardcoded fallback IP (`198.251.89.183`) when `yande.re` failed to resolve through system DNS. Hardcoding IP addresses risks severe outages when an upstream site rotates hosting providers, adopts DDoS mitigation, or shifts server infrastructure.

**New Resolution Hierarchy**:
1. Query `Dns.SYSTEM` first. On clean networks, this provides native zero-latency lookup and respects Android's private DNS settings.
2. If `Dns.SYSTEM` throws `UnknownHostException` (due to ISP DNS poisoning or regional blocking), `DohResolver` kicks in.
3. `DohResolver` queries Cloudflare (`https://1.1.1.1/dns-query`) and Google (`https://8.8.8.8/resolve`) over HTTPS directly by IP address with `Accept: application/dns-json`.
4. Resolved IP addresses are cached in memory in a `ConcurrentHashMap` to minimize downstream network requests.

### Explicit Network Security Configuration
Added `res/xml/network_security_config.xml` wired into `AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```
Cleartext HTTP is strictly rejected across all network clients and WebViews.

### In-App HTTP Proxy
Added `httpProxyHost` and `httpProxyPort` preferences to `LattePreferences` with live propagation to `OkHttpProvider.setProxy()`. This allows users running local proxy/VPN clients (e.g., Clash, V2Ray, SagerNet on `127.0.0.1:7890`) to route all API traffic and Coil image downloads through their local proxy.

### Keystore Hardware Desynchronization Recovery
On certain OEM Android distributions (Samsung OneUI, Xiaomi MIUI/HyperOS), Android Keystore hardware keys occasionally desynchronize or corrupt after system OTA updates, throwing unrecoverable `KeyStoreException` or `AEADBadTagException` upon initializing `EncryptedSharedPreferences`.

Both `SecurePluginStorage.kt` and `PersistentCookieJar.kt` now implement resilient recovery:
1. Try standard initialization with `MasterKey.Builder`.
2. On failure, delete the corrupted preferences file and purge the stale master key entry from `AndroidKeyStore`.
3. Retry initialization.
4. If hardware Keystore remains unrecoverable, degrade safely to private `SharedPreferences` instead of crashing into an unrecoverable launch loop.

---

## 4. Web-Based Seamless Auth for Moebooru

### The Problem
Moebooru sites (Yande.re and Konachan) run on a Ruby on Rails backend without OAuth2 providers. Authentication relies on cookie sessions (`user_id`, `pass_hash`, and Rails session cookies). Manually typing complex passwords into native Compose text fields prevents using modern password managers, biometrics, and Cloudflare challenge solvers.

### In-App WebView SSO Flow
Created `MoebooruLoginActivity.kt` as an isolated, unexported activity:
1. Loads `https://<site>/user/login`.
2. Enables hardware acceleration, DOM storage, JavaScript, and `View.IMPORTANT_FOR_AUTOFILL_YES`.
3. System password managers (Google Autofill, 1Password, Bitwarden, Samsung Pass) detect the web login form and offer 1-tap biometric fill.
4. If Cloudflare Turnstile presents a challenge, the user solves it directly in the browser context.
5. In `WebViewClient.onPageFinished` and `shouldOverrideUrlLoading`, Latte monitors `CookieManager.getInstance().getCookie()`.
6. Once `user_id` and `pass_hash` cookies are observed, `MoebooruLoginActivity`:
   - Builds OkHttp `Cookie` instances and writes them to `OkHttpProvider.cookieJar`.
   - Emits a `MoebooruAuthResult` on `MoebooruAuthCallbackBus`.
   - Returns `Activity.RESULT_OK` and finishes.
7. `PluginLoginDialog` observes `MoebooruAuthCallbackBus`, completes login on the plugin, displays a success notice, and closes.

### Advanced Token Import Fallback
For power users who already have active sessions on desktop or behind strict bot walls, `PluginLoginDialog` and `MoebooruPlugin.login` support importing `pass_hash` directly without revealing plaintext passwords.

---

## 5. Verification on Tablet / Pad

To verify Milestone 0.2.0 on a physical device or tablet:

### 1. Build and Install Release Build
```bash
make assemble-release
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 2. Verification Checklist
- **App Launch**: Verify app boots immediately without Keystore crashes.
- **APK Footprint**: Verify installed application size in Android Settings (`Settings -> Apps -> Latte`) is under 20MB installed.
- **Web SSO Login**:
  - Open `Settings -> Platforms & accounts -> Yande (or Konachan)`.
  - Tap **"Sign in with Web (Autofill & Biometrics)"**.
  - Verify WebView loads `https://yande.re/user/login`.
  - Log in via system Autofill or web form.
  - Verify dialog automatically closes, toast displays *"Signed in as <username>"*, and account displays green checkmark.
- **HTTP Proxy Setting**:
  - Open `Settings -> HTTP Proxy`.
  - Set host/port (or test Disabling).
  - Verify preference persists across restart.
- **Image Browsing & Downloads**:
  - Verify Explore feeds load normally via DoH.
  - Verify downloading images and multi-select batch downloads work cleanly under R8 minification.
