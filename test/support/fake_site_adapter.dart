import 'package:latte/src/domain/post.dart';
import 'package:latte/src/sites/site_adapter.dart';

class FakeSiteAdapter implements SiteAdapter {
  FakeSiteAdapter({List<PostSummary>? posts})
    : _posts =
          posts ??
          const [
            PostSummary(
              reference: PostRef(siteId: SiteId('fake'), remoteId: '1'),
              rating: PostRating.explicit,
              tags: ['fixture'],
            ),
          ];

  final List<PostSummary> _posts;
  PostQuery? lastQuery;
  PostRef? lastDetailReference;
  PostRef? lastMediaReference;
  MediaVariantId? lastMediaVariant;

  @override
  SiteDescriptor get descriptor =>
      const SiteDescriptor(id: SiteId('fake'), displayName: 'Fake Site');

  @override
  Future<PostPage> queryPosts(PostQuery query) async {
    lastQuery = query;
    return PostPage(
      posts: _posts
          .where((post) => post.isVisibleTo(query.contentPolicy))
          .toList(),
      next: null,
    );
  }

  @override
  Future<PostDetail> getPost(PostRef reference) async {
    lastDetailReference = reference;
    final post = _posts.singleWhere((post) => post.reference == reference);
    return PostDetail(summary: post, media: const []);
  }

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  ) async {
    lastMediaReference = reference;
    lastMediaVariant = variant;
    return ResolvedMedia(
      reference: reference,
      variant: MediaVariant(id: variant),
      source: Uri.parse('https://fake.test/${reference.remoteId}/$variant.jpg'),
    );
  }
}
