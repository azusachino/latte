import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:latte/src/domain/failure.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/sites/yandere/yandere_adapter.dart';

void main() {
  test('decodes a yande.re page into normalized posts', () async {
    late http.Request request;
    final client = MockClient((incoming) async {
      request = incoming;
      return http.Response(
        jsonEncode([
          {
            'id': 42001,
            'tags': 'latte_fixture blue_eyes',
            'rating': 'explicit',
            'score': 7,
            'width': 1200,
            'height': 1600,
            'source': 'https://example.test/source',
            'file_ext': 'jpg',
            'sample_width': 600,
            'sample_height': 800,
            'sample_file_size': 1024,
            'jpeg_width': 1200,
            'jpeg_height': 1600,
            'file_size': 4096,
          },
        ]),
        200,
        headers: {'content-type': 'application/json; charset=utf-8'},
      );
    });

    final adapter = YandeAdapter(client: client);
    final page = await adapter.queryPosts(
      const PostQuery.discovery(continuation: '2'),
    );

    expect(request.method, 'GET');
    expect(request.url.path, '/post.json');
    expect(request.url.queryParameters['page'], '2');
    expect(request.url.queryParameters['limit'], '100');
    expect(page.posts, hasLength(1));
    final post = page.posts.single;
    expect(
      post.reference,
      const PostRef(siteId: SiteId('yandere'), remoteId: '42001'),
    );
    expect(post.rating, PostRating.explicit);
    expect(post.tags, ['latte_fixture', 'blue_eyes']);
    expect(post.score, 7);
    expect(post.width, 1200);
    expect(post.height, 1600);
    expect(
      post.preview,
      const MediaVariant(
        id: MediaVariantId.preview,
        width: 600,
        height: 800,
        byteSize: 1024,
        extension: 'jpg',
      ),
    );
    expect(page.next, isNull);
  });

  test('keeps every returned rating in the normalized page', () async {
    final client = MockClient(
      (_) async => http.Response(
        jsonEncode([
          {'id': 1, 'rating': 'safe'},
          {'id': 2, 'rating': 'questionable'},
          {'id': 3, 'rating': 'explicit'},
        ]),
        200,
        headers: {'content-type': 'application/json'},
      ),
    );
    final adapter = YandeAdapter(client: client);

    final page = await adapter.queryPosts(const PostQuery.discovery());

    expect(page.posts.map((post) => post.reference.remoteId), ['1', '2', '3']);
    expect(page.posts.map((post) => post.rating), [
      PostRating.safe,
      PostRating.questionable,
      PostRating.explicit,
    ]);
  });

  test('tag query remains opaque at the yande.re boundary', () async {
    late http.Request request;
    final client = MockClient((incoming) async {
      request = incoming;
      return http.Response(
        '[]',
        200,
        headers: {'content-type': 'application/json'},
      );
    });
    final adapter = YandeAdapter(client: client);

    await adapter.queryPosts(
      PostQuery.tagSearch('artist_name -tag:example order:score'),
    );

    expect(
      request.url.queryParameters['tags'],
      'artist_name -tag:example order:score',
    );
  });

  test('malformed JSON is exposed as a structured failure', () async {
    final client = MockClient(
      (_) async => http.Response(
        '<html>slow down</html>',
        200,
        headers: {'content-type': 'text/html'},
      ),
    );

    expect(
      () =>
          YandeAdapter(client: client).queryPosts(const PostQuery.discovery()),
      throwsA(
        isA<SiteFailureException>().having(
          (error) => error.failure.kind,
          'kind',
          SiteFailureKind.malformedResponse,
        ),
      ),
    );
  });

  test(
    'retries one throttled read and then returns the successful page',
    () async {
      var calls = 0;
      final client = MockClient((_) async {
        calls++;
        if (calls == 1) {
          return http.Response('slow down', 429, headers: {'retry-after': '0'});
        }
        return http.Response(
          '[]',
          200,
          headers: {'content-type': 'application/json'},
        );
      });

      final page = await YandeAdapter(client: client)
          .queryPosts(const PostQuery.discovery());

      expect(calls, 2);
      expect(page.posts, isEmpty);
    },
  );
}
