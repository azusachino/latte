# Contributing to Latte

Thanks for helping improve Latte. The project is early-stage and intentionally
keeps its first milestone narrow: a Yande-compatible exploration and download
journey on Android.

## Development setup

Install the pinned Flutter toolchain and fetch dependencies:

```bash
mise install
mise exec -- flutter pub get
```

Run the complete local gate before opening a pull request:

```bash
make check
mise exec -- flutter build apk --debug
```

The live Yande probe is opt-in; tests use checked-in fixtures and doubles.
Do not add credentials, private URLs, generated build output, or downloaded
post media to the repository.

## Pull requests

- Keep changes focused and explain the user-visible result.
- Preserve Yande's component composition, workflow, dataflow, and gesture
  behavior when changing the UI; Material 3 is the implementation layer.
- Add or update tests for behavior changes.
- Include screenshots for meaningful visual changes.
- Use conventional commit prefixes such as `feat:`, `fix:`, `test:`, `docs:`,
  and `chore:`.

By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
