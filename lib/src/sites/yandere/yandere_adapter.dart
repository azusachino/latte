import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../domain/failure.dart';
import '../../domain/popular_query.dart';
import '../../domain/post.dart';
import '../moebooru/moebooru_decoder.dart';
import '../site_adapter.dart';

class YandeAdapter implements SiteAdapter {
  YandeAdapter({http.Client? client, Uri? baseUri})
    : _client = client ?? http.Client(),
      _baseUri = baseUri ?? Uri.parse('https://yande.re');

  static const _limit = 100;
  static const _site = SiteDescriptor(
    id: SiteId('yandere'),
    displayName: 'yande.re',
  );

  final http.Client _client;
  final Uri _baseUri;
  final MoebooruDecoder _decoder = const MoebooruDecoder();
  final Map<PostRef, DecodedMoebooruPost> _cache = {};

  @override
  SiteDescriptor get descriptor => _site;

  @override
  Future<PostPage> queryPosts(PostQuery query) async {
    final parameters = <String, String>{'limit': '$_limit'};
    if (query.continuation != null) parameters['page'] = query.continuation!;
    switch (query.source) {
      case PostQuerySource.discovery:
        break;
      case PostQuerySource.popular:
        parameters['tags'] = _popularTags(query.popularQuery!);
      case PostQuerySource.tagSearch:
        parameters['tags'] = query.expression!;
    }
    final response = await _get(
      Uri(path: '/post.json', queryParameters: parameters),
    );
    final decoded = _decodeJson(response);
    final decodedPosts = _decoder.decodePage(decoded);
    final posts = decodedPosts
        .map((post) => post.summary)
        .toList(growable: false);
    for (final post in decodedPosts) {
      _cache[post.summary.reference] = post;
    }
    return PostPage(posts: posts, next: _nextPage(query, decoded));
  }

  @override
  Future<PostDetail> getPost(PostRef reference) async {
    _ensureReference(reference);
    final cached = _cache[reference];
    if (cached != null) return cached.detail;
    final response = await _get(
      Uri(
        path: '/post.json',
        queryParameters: {'tags': 'id:${reference.remoteId}', 'limit': '1'},
      ),
    );
    final posts = _decoder.decodePage(_decodeJson(response));
    final post = posts
        .where((candidate) => candidate.summary.reference == reference)
        .firstOrNull;
    if (post == null) {
      throw const SiteFailureException(
        SiteFailure(
          kind: SiteFailureKind.notFound,
          retryable: false,
          message: 'Yande.re post was not found',
        ),
      );
    }
    _cache[reference] = post;
    return post.detail;
  }

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  ) async {
    final detail = await getPost(reference);
    final media = detail.media
        .where((candidate) => candidate.id == variant)
        .firstOrNull;
    final source = _cache[reference]?.sources[variant];
    if (media == null || source == null || !_isAllowedSource(source)) {
      throw const SiteFailureException(
        SiteFailure(
          kind: SiteFailureKind.rejectedMedia,
          retryable: false,
          message: 'Yande.re media is unavailable',
        ),
      );
    }
    return ResolvedMedia(reference: reference, variant: media, source: source);
  }

  void close() => _client.close();

  Future<http.Response> _get(Uri path) async {
    final uri = _baseUri.replace(
      path: path.path,
      queryParameters: path.queryParameters,
    );
    var attempt = 0;
    try {
      while (true) {
        final response = await _client
            .get(uri, headers: const {'accept': 'application/json'})
            .timeout(const Duration(seconds: 8));
        if (response.statusCode >= 200 && response.statusCode < 300) {
          return response;
        }
        final failure = _failureForStatus(response);
        if (attempt == 0 && failure.retryable) {
          attempt++;
          if (failure.retryAfter case final delay? when delay > Duration.zero) {
            await Future<void>.delayed(delay);
          }
          continue;
        }
        throw SiteFailureException(failure);
      }
    } on SiteFailureException {
      rethrow;
    } on TimeoutException {
      throw const SiteFailureException(
        SiteFailure(
          kind: SiteFailureKind.transportUnavailable,
          retryable: true,
          message: 'Can\'t reach Yande.re',
        ),
      );
    } on http.ClientException {
      throw const SiteFailureException(
        SiteFailure(
          kind: SiteFailureKind.transportUnavailable,
          retryable: true,
          message: 'Can\'t reach Yande.re',
        ),
      );
    }
  }

  Object _decodeJson(http.Response response) {
    final contentType = response.headers['content-type']
        ?.split(';')
        .first
        .trim()
        .toLowerCase();
    if (contentType != 'application/json') {
      throw const SiteFailureException(
        SiteFailure(
          kind: SiteFailureKind.malformedResponse,
          retryable: false,
          message: 'Yande.re returned an unreadable response',
        ),
      );
    }
    try {
      return jsonDecode(response.body);
    } on FormatException {
      throw const SiteFailureException(
        SiteFailure(
          kind: SiteFailureKind.malformedResponse,
          retryable: false,
          message: 'Yande.re returned an unreadable response',
        ),
      );
    }
  }

  String? _nextPage(PostQuery query, Object decoded) {
    if (decoded is! List || decoded.length < _limit) return null;
    final current = int.tryParse(query.continuation ?? '1') ?? 1;
    return '${current + 1}';
  }

  static SiteFailure _failureForStatus(http.Response response) {
    final retryAfter = int.tryParse(response.headers['retry-after'] ?? '');
    if (response.statusCode == 421 || response.statusCode == 429) {
      return SiteFailure(
        kind: SiteFailureKind.throttled,
        retryable: true,
        message: 'Yande.re asked us to slow down',
        retryAfter: retryAfter == null ? null : Duration(seconds: retryAfter),
      );
    }
    if (response.statusCode >= 500) {
      return const SiteFailure(
        kind: SiteFailureKind.remoteUnavailable,
        retryable: true,
        message: 'Yande.re is unavailable',
      );
    }
    return const SiteFailure(
      kind: SiteFailureKind.remoteFailure,
      retryable: false,
      message: 'Yande.re is unavailable',
    );
  }

  static void _ensureReference(PostRef reference) {
    if (reference.siteId != _site.id) {
      throw const SiteFailureException(
        SiteFailure(
          kind: SiteFailureKind.invalidRequest,
          retryable: false,
          message: 'Post belongs to another site',
        ),
      );
    }
  }

  bool _isAllowedSource(Uri source) =>
      source.scheme == 'https' &&
      (source.host == _baseUri.host ||
          source.host.endsWith('.${_baseUri.host}'));

  static String _popularTags(PopularQuery query) {
    final start = _formatDate(query.window.start);
    final end = _formatDate(query.window.end);
    return start == end
        ? 'order:score date:$start'
        : 'order:score date:$start..$end';
  }

  static String _formatDate(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-'
      '${value.month.toString().padLeft(2, '0')}-'
      '${value.day.toString().padLeft(2, '0')}';
}
