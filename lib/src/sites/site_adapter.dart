import '../domain/post.dart';

abstract interface class SiteAdapter {
  SiteDescriptor get descriptor;

  Future<PostPage> queryPosts(PostQuery query);

  Future<PostDetail> getPost(PostRef reference);

  Future<ResolvedMedia> resolveMedia(PostRef reference, MediaVariantId variant);
}

class ResolvedMedia {
  const ResolvedMedia({
    required this.reference,
    required this.variant,
    required this.source,
  });

  final PostRef reference;
  final MediaVariant variant;
  final Uri source;
}
