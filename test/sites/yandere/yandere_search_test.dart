import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:latte/src/domain/failure.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/sites/yandere/yandere_adapter.dart';

void main() {
  test('forwards the complete opaque expression in default mode', () async {
    late http.Request request;
    final client = MockClient((incoming) async {
      request = incoming;
      return http.Response(
        '[]',
        200,
        headers: {'content-type': 'application/json'},
      );
    });
    final expression = 'artist_name  -tag:example order:score';

    await YandeAdapter(client: client)
        .queryPosts(PostQuery.tagSearch(expression));

    expect(request.url.queryParameters['tags'], expression);
  });

  test('reports an explicit policy conflict in Safe Mode', () async {
    var requests = 0;
    final client = MockClient((_) async {
      requests++;
      return http.Response(
        '[]',
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    await expectLater(
      YandeAdapter(client: client).queryPosts(
        PostQuery.tagSearch(
          'artist_name rating:explicit order:score',
          contentPolicy: ContentPolicy.safe,
        ),
      ),
      throwsA(
        isA<SiteFailureException>().having(
          (error) => error.failure.kind,
          'kind',
          SiteFailureKind.policyConflict,
        ),
      ),
    );
    expect(requests, 0);
  });

  test('adds an adapter-owned explicit exclusion in Safe Mode', () async {
    late http.Request request;
    final client = MockClient((incoming) async {
      request = incoming;
      return http.Response(
        '[]',
        200,
        headers: {'content-type': 'application/json'},
      );
    });
    const expression = 'artist_name -tag:example order:score';

    await YandeAdapter(client: client).queryPosts(
      PostQuery.tagSearch(expression, contentPolicy: ContentPolicy.safe),
    );

    expect(request.url.queryParameters['tags'], '$expression -rating:explicit');
  });
}
