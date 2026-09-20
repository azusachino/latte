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

  test('all content policy keeps explicit posts by default', () {
    expect(ContentPolicy.all.allows(PostRating.explicit), isTrue);
    expect(summary(PostRating.explicit).isVisibleTo(ContentPolicy.all), isTrue);
  });

  test('safe mode filters only explicit posts', () {
    expect(ContentPolicy.safe.allows(PostRating.safe), isTrue);
    expect(ContentPolicy.safe.allows(PostRating.questionable), isTrue);
    expect(ContentPolicy.safe.allows(PostRating.unknown), isTrue);
    expect(ContentPolicy.safe.allows(PostRating.explicit), isFalse);
    expect(
      summary(PostRating.explicit).isVisibleTo(ContentPolicy.safe),
      isFalse,
    );
  });

  test('tag query preserves opaque site expression and policy', () {
    final query = PostQuery.tagSearch(
      'blue_eyes -rating:explicit order:score',
      contentPolicy: ContentPolicy.safe,
    );

    expect(query.source, PostQuerySource.tagSearch);
    expect(query.expression, 'blue_eyes -rating:explicit order:score');
    expect(query.contentPolicy, ContentPolicy.safe);
    expect(() => PostQuery.tagSearch('  '), throwsArgumentError);
  });
}
