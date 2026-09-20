import '../domain/post.dart';
import 'site_capabilities.dart';

abstract interface class SiteAdapter {
  SiteDescriptor get descriptor;

  Future<PostPage> queryPosts(PostQuery query);

  Future<PostDetail> getPost(PostRef reference);

  Future<ResolvedMedia> resolveMedia(PostRef reference, MediaVariantId variant);
}

/// Implement this small optional port when a site has verified extra
/// operations. Existing and minimal adapters do not need to invent support.
abstract interface class SiteCapabilitiesProvider {
  SiteCapabilities get capabilities;
}

/// Optional ports are supplied by a site adapter only when they are
/// implemented and verified. Consumers must not infer support from the site
/// name or cast the adapter to a monolithic API. An adapter can shadow this
/// extension with a real [SiteCapabilities] getter without changing the core
/// interface implemented by existing adapters.
extension SiteAdapterCapabilities on SiteAdapter {
  SiteCapabilities get capabilities => this is SiteCapabilitiesProvider
      ? (this as SiteCapabilitiesProvider).capabilities
      : const SiteCapabilities();
}

class ResolvedMedia {
  const ResolvedMedia({
    required this.reference,
    required this.variant,
    required this.source,
    this.headers = const {},
  });

  final PostRef reference;
  final MediaVariant variant;
  final Uri source;
  final Map<String, String> headers;
}
