# Contributing to Latte

Thanks for helping improve Latte. The project is early-stage and intentionally
keeps its first milestone narrow: a Yande-compatible exploration and download
journey on native Android.

## Development setup

Install the pinned Java toolchain:

```bash
mise install
```

Run the complete local gate before opening a pull request:

```bash
make md-format
make check
make lint
make validate
```

The live Yande verification is opt-in; automated tests use doubles and do not
require a live account. `make check` runs Markdown checks and unit tests;
`make lint` and `make validate` provide the separate Android lint and debug-APK
checks. Run `make md-format` to apply the configured Markdown formatting.
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
