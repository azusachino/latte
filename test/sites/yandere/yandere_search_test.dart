import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
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

  test('forwards explicit rating intent without a policy conflict', () async {
    late http.Request request;
    final client = MockClient((incoming) async {
      request = incoming;
      return http.Response(
        '[]',
        200,
        headers: {'content-type': 'application/json'},
      );
    });

    await YandeAdapter(client: client).queryPosts(
      PostQuery.tagSearch('artist_name rating:explicit order:score'),
    );

    expect(
      request.url.queryParameters['tags'],
      'artist_name rating:explicit order:score',
    );
  });
}
