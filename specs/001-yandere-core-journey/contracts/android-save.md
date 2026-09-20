# Contract: Android public-image save

## Authority boundary

Flutter supplies validated bytes or a temporary app-owned file descriptor plus
the deterministic site/post/variant identity, known checksum/display name, MIME
type, and relative Latte album. It cannot supply an arbitrary filesystem path.
Android owns MediaStore lookup, insertion, publication, collision detection,
cleanup, and returned content URI.

## Operation

```text
publishImage(displayName, mimeType, temporarySource) -> SaveResult
```

## Rules

- API 29+ only for this milestone.
- Target collection is public images under a Latte-relative album.
- A pending MediaStore item becomes visible only after all bytes are written and
  flushed successfully.
- A failed or interrupted transfer keeps its MediaStore row pending and hidden;
  a later WorkManager run resumes it with an HTTP range request when the server
  supports ranges. It is never published as partial media.
- Before transfer, query published entries under `Pictures/Latte` by the
  deterministic identity; immediately before publication, query again to close
  the concurrent-save race. An existing target returns `Already saved` with its
  content URI, album, and display name without overwriting. This remains true
  after app restart or process death; session memory is not authoritative.
- Returned locations contain a content URI, `Pictures/Latte` album, and display
  name, never a raw filesystem path.
- Platform exceptions map to retryable or terminal failure without leaking
  stack traces into UI copy.
- The worker uses unique work per deterministic save identity, keeps duplicate
  requests from starting a second transfer, and allows different identities to
  progress concurrently.

## Contract evidence

- Dart contract tests use a fake `PublicImageStore` boundary.
- Kotlin tests cover metadata, pending publication, collision, and cleanup.
- Android integration acceptance saves one fixture image, records its bytes,
  terminates/relaunches the app, repeats the operation, and proves an
  `Already saved` result with the same URI/metadata and unchanged bytes.
- This fixture-backed device spike is a blocking gate before Story 3 UI work.
