# Contract: Android public-image save

## Authority boundary

Flutter supplies validated bytes or a temporary app-owned file descriptor plus
the deterministic display name, MIME type, and relative Latte album. It cannot
supply an arbitrary filesystem path. Android owns MediaStore insertion,
publication, collision detection, cleanup, and returned content URI.

## Operation

```text
publishImage(displayName, mimeType, temporarySource) -> SaveResult
```

## Rules

- API 29+ only for this milestone.
- Target collection is public images under a Latte-relative album.
- A pending MediaStore item becomes visible only after all bytes are written and
  flushed successfully.
- Failure or interrupted publication deletes the pending item and temporary
  source.
- An existing deterministic target returns `existing` without overwriting.
- Returned locations contain a content URI, `Pictures/Latte` album, and display
  name, never a raw filesystem path.
- Platform exceptions map to retryable or terminal failure without leaking
  stack traces into UI copy.

## Contract evidence

- Dart contract tests use a fake `PublicImageStore` boundary.
- Kotlin tests cover metadata, pending publication, collision, and cleanup.
- Android integration acceptance saves one fixture image, locates/opens it, and
  repeats the operation to prove non-overwrite behavior.
- This fixture-backed device spike is a blocking gate before Story 3 UI work.
