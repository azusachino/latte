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

`queryPosts` accepts Newest or opaque tag-search intent and an optional
adapter-owned continuation. Popular queries carry a normalized period and
anchor/window identity; the adapter owns their route and query mapping.
`getPost` may implement lookup by any verified site mechanism. `resolveMedia`
revalidates reference, variant, scheme, and allowed host immediately before
transfer.

## Capability boundary

The core interface is intentionally small. Optional platform behavior is
represented by separate capability ports, collected through
`SiteCapabilitiesProvider`, rather than nullable methods on `SiteAdapter` or a
Yande-specific superclass. Current ports include tag suggestions, related
tags, authentication, remote favorites, browser routes, and authenticated
personal scoring.

The application discovers these ports from the adapter's runtime capability
provider. It renders an operation only when the corresponding port exists and
has a verified implementation. The personal-score port is roadmap-only in this
milestone and must not appear in the UI. A future platform can therefore add a
site adapter and only implement the capabilities that its API supports.

`LatteSiteRegistry` owns the set of adapters and rejects duplicate site IDs;
exploration remains site-neutral and receives one selected `SiteAdapter`.

## Success rules

- Returned posts belong to `descriptor.id`.
- All normalized ratings, including explicit, are returned and preserved.
- Opaque tag expressions, including rating terms, are forwarded exactly as
  entered after the domain's whitespace validation; the adapter does not append
  exclusions or manufacture a policy-conflict result.
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
