import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/post.dart';

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
}
