import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/sites/site_adapter.dart';
import 'package:latte/src/sites/site_capabilities.dart';
import 'package:latte/src/sites/site_registry.dart';

void main() {
  test('keeps site lookup outside the explore controller', () {
    final first = _Adapter(SiteId('first'), 'First');
    final second = _Adapter(SiteId('second'), 'Second');
    final registry = LatteSiteRegistry([first, second]);

    expect(registry.defaultAdapter, same(first));
    expect(registry.find(SiteId('second')), same(second));
    expect(registry.find(SiteId('missing')), isNull);
  });

  test('optional capabilities are discovered only from a provider', () {
    final adapter = _CapabilityAdapter();

    expect(adapter.capabilities.tagSuggestions, same(adapter.suggestions));
    expect(
      _Adapter(SiteId('plain'), 'Plain').capabilities.tagSuggestions,
      isNull,
    );
  });

  test('rejects duplicate site IDs', () {
    final first = _Adapter(SiteId('same'), 'First');
    final second = _Adapter(SiteId('same'), 'Second');

    expect(() => LatteSiteRegistry([first, second]), throwsArgumentError);
  });
}

class _Adapter implements SiteAdapter {
  _Adapter(this.id, this.name);

  final SiteId id;
  final String name;

  @override
  SiteDescriptor get descriptor => SiteDescriptor(id: id, displayName: name);

  @override
  Future<PostPage> queryPosts(PostQuery query) async =>
      const PostPage(posts: []);

  @override
  Future<PostDetail> getPost(PostRef reference) async =>
      throw UnimplementedError();

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  ) async => throw UnimplementedError();
}

class _CapabilityAdapter extends _Adapter implements SiteCapabilitiesProvider {
  _CapabilityAdapter() : super(SiteId('capable'), 'Capable');

  final suggestions = _Suggestions();

  @override
  SiteCapabilities get capabilities =>
      SiteCapabilities(tagSuggestions: suggestions);
}

class _Suggestions implements TagSuggestionCapability {
  @override
  Future<List<SiteTagSuggestion>> suggestTags(String prefix) async => const [];
}
