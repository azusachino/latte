import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/features/download/download_service.dart';
import 'package:latte/src/sites/site_adapter.dart';

void main() {
  test('resolves the selected variant before saving it', () async {
    final adapter = _DownloadAdapter();
    final store = _DownloadStore();
    final service = DownloadService(adapter: adapter, store: store);
    const reference = PostRef(siteId: SiteId('yandere'), remoteId: '42');

    final receipt = await service.save(
      reference: reference,
      variant: const MediaVariant(
        id: MediaVariantId.original,
        extension: 'png',
      ),
    );

    expect(adapter.resolved, reference);
    expect(adapter.variant, MediaVariantId.original);
    expect(store.media?.source, Uri.parse('https://fake.test/42.png'));
    expect(store.displayName, 'latte_yandere_42_original.png');
    expect(receipt.status, DownloadStatus.completed);
  });
}

class _DownloadAdapter implements SiteAdapter {
  PostRef? resolved;
  MediaVariantId? variant;

  @override
  SiteDescriptor get descriptor =>
      const SiteDescriptor(id: SiteId('yandere'), displayName: 'yande.re');

  @override
  Future<PostPage> queryPosts(PostQuery query) async =>
      const PostPage(posts: []);

  @override
  Future<PostDetail> getPost(PostRef reference) async => PostDetail(
    summary: PostSummary(reference: reference, rating: PostRating.safe),
    media: const [],
  );

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId requestedVariant,
  ) async {
    resolved = reference;
    variant = requestedVariant;
    return ResolvedMedia(
      reference: reference,
      variant: MediaVariant(id: requestedVariant, extension: 'png'),
      source: Uri.parse('https://fake.test/${reference.remoteId}.png'),
    );
  }
}

class _DownloadStore implements DownloadStore {
  ResolvedMedia? media;
  String? displayName;

  @override
  Future<DownloadReceipt> save({
    required ResolvedMedia media,
    required String displayName,
  }) async {
    this.media = media;
    this.displayName = displayName;
    return const DownloadReceipt(
      status: DownloadStatus.completed,
      contentUri: 'content://fake/42',
      album: 'Pictures/Latte',
      displayName: 'latte_yandere_42_original.png',
    );
  }
}
