# Validation quickstart: yande.re core journey

This guide describes the executable bootstrap and deterministic gates. Story
work remains blocked until the Android feasibility gate is verified.

## Prerequisites

- mise
- Android SDK, ADB, Java 21, and an API 29+ emulator or device
- network access only for explicit live acceptance

## Bootstrap gates

```bash
mise install
make doctor
make probe
```

The bootstrap must prove both gates before feature work:

1. a minimal Dart client receives and decodes one anonymous yande.re post page;
2. a stock Flutter application builds and launches on the chosen Android target.

Before Story 3, `make feasibility-save DEVICE=<adb-serial>` must stream a tiny
fixture into `Pictures/Latte`, prove duplicate detection, and prove failed-write
cleanup.

## Deterministic repository gate

```bash
make check
```

The gate formats, analyzes, and runs unit, contract, widget, and host tests. It
does not call yande.re.

## Story acceptance

```bash
make test-story STORY=discover
make test-story STORY=search
make test-story STORY=save
```

- `discover`: two fixture pages, detail/back position, and all read failures.
- `search`: opaque positive/negative/meta terms, replacement races, no-results,
  and clear-to-discovery.
- `save`: variant choice, progress, MediaStore publication, repeat-save
  non-overwrite, failure, and interrupted-publication cleanup.

Material acceptance covers compact and expanded widths, light and dark themes,
200% text scale, reduced motion, semantics, and 48-by-48 interaction targets.

## Live/device receipt

```bash
make acceptance-live DEVICE=<adb-serial>
```

This opt-in target performs anonymous reads and one explicit owner-selected save.
The receipt records date, application revision, endpoint families, target API,
counts/statuses, and redacted error codes. It stores no response bodies, remote
media, device identifiers, account data, cookies, or credentials.

## Expected milestone result

On the accepted Android target, the owner can open Latte, browse all ratings by
default, optionally enable Safe Mode to filter explicit posts, search an opaque
tag expression, inspect a post, save one chosen variant to the public Latte
album, and repeat without overwrite.
