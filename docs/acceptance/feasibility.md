# Phase 1 feasibility receipt

> **Historical receipt**: This records the superseded Flutter/Dart feasibility
> experiment from 0.0.1. The current implementation is native Kotlin/Compose;
> use the active 0.0.2 tasks and current Gradle gates for present verification.

**Observed**: 2026-09-20

This receipt records the bounded T001–T004 checks. It contains status metadata
only; no yande.re response body, credentials, cookies, or raw headers are
stored.

## Toolchain

| Check | Result |
| --- | --- |
| mise pin | `flutter = "3.47.5"` in `.mise.toml` |
| Flutter | `3.47.5` |
| Bundled Dart | `3.13.4` |
| Deterministic gates | `make check` passed |
| Flutter doctor | Flutter SDK and Android toolchain passed after selecting `/opt/homebrew/share/android-commandlinetools`; the doctor reported the machine's existing SDK licenses as accepted. No license command was run by this task. |

The app is generated for Android with `minSdk = 29`. Flutter reports Android
deployment support for API 24–37 in the selected stable line; Latte claims API
29+ only.

## Anonymous transport probe

Command: `make probe`

The probe uses an anonymous bounded GET of `/post.json?limit=1`, an eight-second
timeout, and a 512 KiB response limit. It prints only these fields:

```text
statusCode=200
contentType=application/json
redirectHost=null
normalizedItemCount=1
```

The public behavior test in
`tool/feasibility/yandere_probe_test.dart` was observed RED before
`yandere_probe.dart` existed, then GREEN with an injected deterministic HTTP
client. The live probe is manual evidence and is not part of `make check`.

**Dart transport gate: PASS.**

## Android build and launch boundary

Flutter initially selected the platform-tools directory
`/opt/homebrew/Caskroom/android-platform-tools/37.0.1` as the SDK. The SDK
path was corrected with:

```text
mise exec -- flutter config --android-sdk /opt/homebrew/share/android-commandlinetools
```

The connected OnePlus 8 was observed as `0cadf428`, Android 16 / API 36. The
stock debug APK then built successfully:

```text
mise exec -- flutter build apk --debug
✓ Built build/app/outputs/flutter-apk/app-debug.apk
```

The APK was installed with `adb install -r`, and the launch was verified with
`adb shell monkey -p com.azusachino.latte 1`; Android reported the visible
activity as `com.azusachino.latte/.MainActivity`. The build used the machine's
already-installed SDK components and licenses; this task did not accept any
license or legal terms.

**Android build/launch gate: PASS.** The API 36 device is above Latte's API
29+ support floor.
