import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/post.dart';

import '../support/fake_site_adapter.dart';

void main() {
  late FakeSiteAdapter adapter;

  setUp(() {
    adapter = FakeSiteAdapter();
  });

  test('exposes stable site identity', () {
    expect(adapter.descriptor.id, const SiteId('fake'));
    expect(adapter.descriptor.displayName, 'Fake Site');
  });

  test('query accepts opaque intent and applies content policy', () async {
    final page = await adapter.queryPosts(
      PostQuery.tagSearch(
        'artist_name -tag:example',
        contentPolicy: ContentPolicy.safe,
      ),
    );

    expect(adapter.lastQuery?.expression, 'artist_name -tag:example');
    expect(page.posts, isEmpty);
    expect(page.next, isNull);
  });

  test('detail and media resolution use normalized identity', () async {
    const reference = PostRef(siteId: SiteId('fake'), remoteId: '1');

    final detail = await adapter.getPost(reference);
    final media = await adapter.resolveMedia(
      reference,
      MediaVariantId.original,
    );

    expect(adapter.lastDetailReference, reference);
    expect(detail.summary.reference, reference);
    expect(adapter.lastMediaReference, reference);
    expect(adapter.lastMediaVariant, MediaVariantId.original);
    expect(media.reference, reference);
    expect(media.variant.id, MediaVariantId.original);
    expect(media.source.scheme, 'https');
  });
}
