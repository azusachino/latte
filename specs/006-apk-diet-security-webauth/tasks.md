# Tasks: 006 APK Diet, Security Hardening, and Web Auth

## Execution Tasks

- [ ] T001 Create feature branch `feat/milestone-0.2.0-diet-security-webauth`
- [ ] T002 Implement `LatteIcons.kt` with required vectors and remove `material-icons-extended` dependency
- [ ] T003 Enable R8 minification, resource shrinking, and write `proguard-rules.pro`
- [ ] T004 Add `network_security_config.xml` and wire in `AndroidManifest.xml`
- [ ] T005 Implement dynamic DNS-over-HTTPS (DoH) resolver in `OkHttpProvider.kt` removing hardcoded IP
- [ ] T006 Add optional HTTP proxy preference in `LattePreferences` and `OkHttpProvider`
- [ ] T007 Harden `SecurePluginStorage.kt` and `PersistentCookieJar.kt` with Keystore desync recovery
- [ ] T008 Implement `MoebooruLoginActivity` with cookie interception and register in manifest
- [ ] T009 Update `PluginLoginDialog.kt` with "Log in with Web" and "Import Session Cookie"
- [ ] T010 Run tests, lint, release APK size benchmark (<12MB), commit, and push PR
