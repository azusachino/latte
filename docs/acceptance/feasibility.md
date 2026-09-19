# Phase 1 feasibility receipt

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
| Flutter doctor | Flutter SDK passed; Android toolchain reported missing command-line tools |

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

`adb devices -l` returned no connected Android device. `flutter devices` found
only the macOS desktop target, and `flutter emulators` found no emulator
sources, so no API 29+ target was available to launch.

`flutter build apk --debug` could not reach compilation because the local SDK
requires an unaccepted `ndk;28.2.13676358` license. No SDK license was accepted
as part of this task.

**Android build/launch gate: UNVERIFIED / BLOCKED BY LOCAL SDK STATE.** Do not
claim Android support or begin Story 1 until an owner-approved SDK setup provides
an API 29+ emulator/device and the stock debug build launches there.
