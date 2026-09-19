# Contract: site adapter

## Purpose

The product sees one narrow remote boundary. It does not know yande.re routes,
Moebooru DTOs, numeric page indexes, media hosts, or HTTP status codes.

## Dart-facing interface

```dart
abstract interface class SiteAdapter {
  SiteDescriptor get descriptor;

  Future<PostPage> queryPosts(PostQuery query);

  Future<PostDetail> getPost(PostRef reference);

  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  );
}
```

`queryPosts` accepts discovery or opaque tag-search intent and an optional
adapter-owned continuation. `getPost` may implement lookup by any verified site
mechanism. `resolveMedia` revalidates reference, variant, scheme, and allowed
host immediately before transfer.

## Success rules

- Returned posts belong to `descriptor.id`.
- Default mode permits all normalized ratings, including explicit. When Safe Mode
  is active, explicit-rated posts are filtered before the production adapter
  returns presentation data.
- A Safe Mode query that explicitly requests explicit-rated content returns a
  structured policy conflict; default mode preserves the owner's expression.
- Page order matches the site result after invalid/duplicate items are handled.
- Missing optional metadata stays absent.
- Continuation is opaque outside the adapter.

## Failure rules

- Every failure maps to the structured `SiteFailure` taxonomy.
- `421`, `429`, and retryable `503` may be retried once for reads, honoring a
  valid `Retry-After` value when present.
- HTML returned where JSON is expected is `malformedResponse` or a mapped remote
  failure, never an empty page.
- Raw response bodies, headers containing secrets, and remote URLs are not
  included in owner-facing messages.

## Contract tests

The production yande.re adapter and deterministic substitute adapter must pass
the same behavior suite for identity, opaque queries, continuation, content
policy, normalized nullability, failure mapping, and media resolution authority.
