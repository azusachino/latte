import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:flutter_test/flutter_test.dart';

import 'yandere_probe.dart';

void main() {
  test('reports bounded normalized transport metadata', () async {
    final client = MockClient((request) async {
      expect(request.method, 'GET');
      expect(request.url, Uri.parse('https://yande.re/post.json?limit=1'));
      expect(request.headers['accept'], 'application/json');

      return http.Response(
        jsonEncode([
          {'id': 42, 'tags': 'latte test'},
        ]),
        200,
        headers: {'content-type': 'application/json; charset=utf-8'},
      );
    });

    final result = await YandeProbe(client: client).run();

    expect(result.statusCode, 200);
    expect(result.contentType, 'application/json');
    expect(result.redirectHost, isNull);
    expect(result.normalizedItemCount, 1);
    expect(result.toString(), isNot(contains('latte test')));
  });
}
