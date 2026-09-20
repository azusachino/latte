import '../../domain/failure.dart';
import '../../domain/post.dart';

class MoebooruDecoder {
  const MoebooruDecoder();

  List<DecodedMoebooruPost> decodePage(Object decoded) {
    if (decoded is! List) {
      throw const SiteFailureException(
        SiteFailure(
          kind: SiteFailureKind.malformedResponse,
          retryable: false,
          message: 'Yande.re returned an unreadable response',
        ),
      );
    }

    final posts = <DecodedMoebooruPost>[];
    final seen = <String>{};
    for (final item in decoded) {
      if (item is! Map) continue;
      final post = _decodePost(item);
      if (post == null || !seen.add(post.summary.reference.remoteId)) {
        continue;
      }
      posts.add(post);
    }
    return posts;
  }

  DecodedMoebooruPost? _decodePost(Map item) {
    final id = item['id'];
    if (id is! num || id <= 0) return null;

    final remoteId = id.toInt().toString();
    final rating = _rating(item['rating']);
    final tags = _tags(item['tags']);
    final variants = <MediaVariant>[];
    final sources = <MediaVariantId, Uri>{};
    _addVariant(
      item,
      variants,
      sources,
      id: MediaVariantId.preview,
      width: _int(item['preview_width']) ?? _int(item['sample_width']),
      height: _int(item['preview_height']) ?? _int(item['sample_height']),
      byteSize:
          _int(item['preview_file_size']) ?? _int(item['sample_file_size']),
      extension: _string(item['file_ext']),
      source: _uri(item['preview_url']),
    );
    _addVariant(
      item,
      variants,
      sources,
      id: MediaVariantId.sample,
      width: _int(item['sample_width']),
      height: _int(item['sample_height']),
      byteSize: _int(item['sample_file_size']),
      extension: _string(item['file_ext']),
      source: _uri(item['sample_url']),
    );
    _addVariant(
      item,
      variants,
      sources,
      id: MediaVariantId.jpeg,
      width: _int(item['jpeg_width']),
      height: _int(item['jpeg_height']),
      byteSize: _int(item['jpeg_file_size']),
      extension: 'jpg',
      source: _uri(item['jpeg_url']),
    );
    _addVariant(
      item,
      variants,
      sources,
      id: MediaVariantId.original,
      width: _int(item['width']),
      height: _int(item['height']),
      byteSize: _int(item['file_size']),
      extension: _string(item['file_ext']),
      source: _uri(item['file_url']),
    );

    final summary = PostSummary(
      reference: PostRef(siteId: const SiteId('yandere'), remoteId: remoteId),
      rating: rating,
      tags: tags,
      preview: variants
          .where((variant) => variant.id == MediaVariantId.preview)
          .firstOrNull,
      score: _int(item['score']),
      width: _int(item['width']),
      height: _int(item['height']),
      source: _string(item['source']),
      createdAt: _date(item['created_at']),
    );
    return DecodedMoebooruPost(
      summary: summary,
      variants: variants,
      sources: sources,
    );
  }

  void _addVariant(
    Map item,
    List<MediaVariant> variants,
    Map<MediaVariantId, Uri> sources, {
    required MediaVariantId id,
    required int? width,
    required int? height,
    required int? byteSize,
    required String? extension,
    required Uri? source,
  }) {
    if (width == null && height == null && byteSize == null && source == null) {
      return;
    }
    variants.add(
      MediaVariant(
        id: id,
        width: width,
        height: height,
        byteSize: byteSize,
        extension: extension,
      ),
    );
    if (source != null) {
      sources[id] = source;
    }
  }

  static PostRating _rating(Object? value) => switch (value) {
    'safe' => PostRating.safe,
    'questionable' => PostRating.questionable,
    'explicit' => PostRating.explicit,
    _ => PostRating.unknown,
  };

  static List<String> _tags(Object? value) {
    if (value is String) {
      return List.unmodifiable(
        value.split(RegExp(r'\s+')).where((tag) => tag.isNotEmpty),
      );
    }
    if (value is List) {
      return List.unmodifiable(
        value.whereType<String>().where((tag) => tag.isNotEmpty),
      );
    }
    return const [];
  }

  static int? _int(Object? value) => switch (value) {
    int value => value,
    num value => value.toInt(),
    _ => null,
  };

  static String? _string(Object? value) =>
      value is String && value.isNotEmpty ? value : null;

  static Uri? _uri(Object? value) {
    final string = _string(value);
    if (string == null) return null;
    final uri = Uri.tryParse(string);
    return uri?.hasScheme == true ? uri : null;
  }

  static DateTime? _date(Object? value) {
    final string = _string(value);
    return string == null ? null : DateTime.tryParse(string);
  }
}

class DecodedMoebooruPost {
  const DecodedMoebooruPost({
    required this.summary,
    required this.variants,
    required this.sources,
  });

  final PostSummary summary;
  final List<MediaVariant> variants;
  final Map<MediaVariantId, Uri> sources;

  PostDetail get detail => PostDetail(summary: summary, media: variants);
}
