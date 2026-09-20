import 'popular_query.dart';

enum PostRating { safe, questionable, explicit, unknown }

enum PostQuerySource { discovery, popular, tagSearch }

enum MediaVariantId { preview, sample, jpeg, original }

class SiteId {
  const SiteId(this.value) : assert(value != '');

  final String value;

  @override
  bool operator ==(Object other) => other is SiteId && other.value == value;

  @override
  int get hashCode => value.hashCode;

  @override
  String toString() => value;
}

class SiteDescriptor {
  const SiteDescriptor({required this.id, required this.displayName});

  final SiteId id;
  final String displayName;
}

class PostRef {
  const PostRef({required this.siteId, required this.remoteId})
    : assert(remoteId != '');

  final SiteId siteId;
  final String remoteId;

  @override
  bool operator ==(Object other) =>
      other is PostRef && other.siteId == siteId && other.remoteId == remoteId;

  @override
  int get hashCode => Object.hash(siteId, remoteId);
}

class MediaVariant {
  const MediaVariant({
    required this.id,
    this.width,
    this.height,
    this.byteSize,
    this.extension,
  });

  final MediaVariantId id;
  final int? width;
  final int? height;
  final int? byteSize;
  final String? extension;

  @override
  bool operator ==(Object other) =>
      other is MediaVariant &&
      other.id == id &&
      other.width == width &&
      other.height == height &&
      other.byteSize == byteSize &&
      other.extension == extension;

  @override
  int get hashCode => Object.hash(id, width, height, byteSize, extension);
}

class PostSummary {
  const PostSummary({
    required this.reference,
    required this.rating,
    this.tags = const [],
    this.preview,
    this.score,
    this.width,
    this.height,
    this.source,
    this.createdAt,
  });

  final PostRef reference;
  final PostRating rating;
  final List<String> tags;
  final MediaVariant? preview;
  final int? score;
  final int? width;
  final int? height;
  final String? source;
  final DateTime? createdAt;

  @override
  bool operator ==(Object other) =>
      other is PostSummary &&
      other.reference == reference &&
      other.rating == rating &&
      _listEquals(other.tags, tags) &&
      other.preview == preview &&
      other.score == score &&
      other.width == width &&
      other.height == height &&
      other.source == source &&
      other.createdAt == createdAt;

  @override
  int get hashCode => Object.hash(
    reference,
    rating,
    Object.hashAll(tags),
    preview,
    score,
    width,
    height,
    source,
    createdAt,
  );
}

class PostDetail {
  const PostDetail({
    required this.summary,
    required this.media,
    this.parentId,
    this.hasChildren,
    this.fileChecksum,
    this.fileExtension,
  });

  final PostSummary summary;
  final List<MediaVariant> media;
  final String? parentId;
  final bool? hasChildren;
  final String? fileChecksum;
  final String? fileExtension;
}

class PostQuery {
  const PostQuery.discovery({this.continuation})
    : source = PostQuerySource.discovery,
      expression = null,
      popularQuery = null;

  PostQuery.popular(this.popularQuery, {this.continuation})
    : source = PostQuerySource.popular,
      expression = null;

  PostQuery.tagSearch(String expression, {this.continuation})
    : source = PostQuerySource.tagSearch,
      expression = _validatedExpression(expression),
      popularQuery = null;

  final PostQuerySource source;
  final String? expression;
  final PopularQuery? popularQuery;
  final String? continuation;

  PostQuery withContinuation(String? value) => switch (source) {
    PostQuerySource.discovery => PostQuery.discovery(continuation: value),
    PostQuerySource.popular => PostQuery.popular(
      popularQuery!,
      continuation: value,
    ),
    PostQuerySource.tagSearch => PostQuery.tagSearch(
      expression!,
      continuation: value,
    ),
  };

  @override
  bool operator ==(Object other) =>
      other is PostQuery &&
      other.source == source &&
      other.expression == expression &&
      other.popularQuery == popularQuery &&
      other.continuation == continuation;

  @override
  int get hashCode =>
      Object.hash(source, expression, popularQuery, continuation);
}

class PostPage {
  const PostPage({required this.posts, this.next});

  final List<PostSummary> posts;
  final String? next;
}

String _validatedExpression(String expression) {
  final trimmed = expression.trim();
  if (trimmed.isEmpty || trimmed.codeUnits.any((unit) => unit < 0x20)) {
    throw ArgumentError.value(
      expression,
      'expression',
      'must contain printable text',
    );
  }
  return trimmed;
}

bool _listEquals<T>(List<T> left, List<T> right) {
  if (left.length != right.length) return false;
  for (var index = 0; index < left.length; index++) {
    if (left[index] != right[index]) return false;
  }
  return true;
}
