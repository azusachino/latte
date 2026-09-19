# Latte

Latte is a planned Dart/Flutter image-board client. Its first supported site is
[yande.re](https://yande.re/); its application boundary is designed so another
site can be added without teaching the UI that site's wire format, pagination,
authentication, or download rules.

The repository is intentionally documentation-only while the rewrite contract
is reviewed. No implementation should begin until the project spec, public test
seams, and first vertical slice are accepted.

## Start here

- [Project spec](docs/PROJECT-SPEC.md)
- [Research](docs/research/2026-09-20-dart-moebooru-yandere.md)
- [Implementation plan](tasks/plan.md)
- [Task checklist](tasks/todo.md)

## Proposed first slice

```text
yande.re fixture -> Yande adapter -> normalized post page -> Flutter grid
```

The first slice is anonymous and read-only: load one page of yande.re posts,
render it, and expose honest loading, empty, malformed-response, and network
failure states. Search, detail, and downloads follow as separate
red-green-refactor slices. Authentication, favorites, pools, and a second real
site stay out of the first milestone.
