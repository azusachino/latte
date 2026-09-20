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

## Active first journey

```text
Popular day/week/month -> Yande adapter -> normalized masonry feed
  -> image-first detail/pager -> local download
```

The first journey is anonymous and read-only at the remote boundary: load
Popular results for a visible day/week/month period, render a dense
thumbnail-only aspect-preserving feed, inspect a post in a
context-preserving pager, and expose honest loading, empty, malformed-response,
and transport-failure states. Newest and opaque tag search remain supported
discovery modes. Local download follows the read-only foundation as the first
device side effect. Authenticated personal scoring (0–3) is a later distinct
slice; the displayed aggregate score is never treated as the owner's vote.

Ratings remain adapter/search metadata but are not rendered in the Latte UI.
Latte has no Safe Mode filter or policy-driven query rewrite.
