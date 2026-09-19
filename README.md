# Latte

Latte is a planned Dart/Flutter image-board client. Its first supported site is
[yande.re](https://yande.re/); its application boundary is designed so another
site can be added without teaching the UI that site's wire format, pagination,
authentication, or download rules.

The active rewrite contract is recorded in Spec Kit artifacts. Implementation
begins with bounded feasibility gates; feature work remains blocked until the
Dart transport and Android target checks pass.

## Start here

- [Feasibility decision](.specify/assessments/latte-yandere-client/decision.md)
- [Active product specification](specs/001-yandere-core-journey/spec.md)
- [Product and Material 3 contract](specs/001-yandere-core-journey/contracts/product-experience.md)
- [Technical plan](specs/001-yandere-core-journey/plan.md)
- [Spec Kit evaluation](docs/spec-kit-evaluation.md)
- [Research](docs/research/2026-09-20-dart-moebooru-yandere.md)

`docs/PROJECT-SPEC.md` and `tasks/` are retained as historical inputs. The
active feature directory is the execution source of truth.

## Proposed first slice

```text
yande.re fixture -> Yande adapter -> normalized post page -> Flutter grid
```

The first slice is anonymous and read-only: load one page of yande.re posts,
render it with a token-owned Material 3 theme, and expose honest loading, empty,
malformed-response, and transport-failure states. Search, detail, and downloads
follow as separate red-green-refactor slices. Authentication, favorites, pools,
and a second real site stay out of the first milestone. All ratings are visible
by default; Safe Mode is an opt-in filter for explicit content.
