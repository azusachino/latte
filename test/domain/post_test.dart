import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/sites/site_adapter.dart';

void main() {
  final reference = PostRef(siteId: SiteId('yandere'), remoteId: '42');

  PostSummary summary(PostRating rating) => PostSummary(
    reference: reference,
    rating: rating,
    tags: const ['blue_eyes', 'test'],
  );

  test('post identity includes site and remote id', () {
    expect(
      reference,
      equals(const PostRef(siteId: SiteId('yandere'), remoteId: '42')),
    );
    expect(
      reference,
      isNot(equals(const PostRef(siteId: SiteId('other'), remoteId: '42'))),
    );
    expect(
      reference.hashCode,
      const PostRef(siteId: SiteId('yandere'), remoteId: '42').hashCode,
    );
  });

  test('explicit rating remains presentation metadata', () {
    final post = summary(PostRating.explicit);

    expect(post.rating, PostRating.explicit);
  });

  test('tag query preserves an opaque site expression', () {
    final query = PostQuery.tagSearch('blue_eyes -rating:explicit order:score');

    expect(query.source, PostQuerySource.tagSearch);
    expect(query.expression, 'blue_eyes -rating:explicit order:score');
    expect(() => PostQuery.tagSearch('  '), throwsArgumentError);
  });

  test('subscribed query models following updates', () {
    const query = PostQuery.subscribed();

    expect(query.source, PostQuerySource.subscribed);
    expect(query.expression, isNull);
    expect(query.popularQuery, isNull);

    final continued = query.withContinuation('page_2');
    expect(continued.source, PostQuerySource.subscribed);
    expect(continued.continuation, 'page_2');
  });

  test('ResolvedMedia holds optional request headers', () {
    final media = ResolvedMedia(
      reference: reference,
      variant: const MediaVariant(id: MediaVariantId.original),
      source: Uri.parse('https://example.com/image.jpg'),
      headers: const {'Referer': 'https://example.com/'},
    );

    expect(media.headers['Referer'], 'https://example.com/');
  });
}
