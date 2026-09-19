import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

const _defaultEndpoint = 'https://yande.re/post.json?limit=1';
const _maxResponseBytes = 512 * 1024;
const _requestTimeout = Duration(seconds: 8);

Future<void> main() async {
  final probe = YandeProbe();
  try {
    final result = await probe.run();
    stdout.writeln(
      jsonEncode({
        'statusCode': result.statusCode,
        'contentType': result.contentType,
        'redirectHost': result.redirectHost,
        'normalizedItemCount': result.normalizedItemCount,
      }),
    );
    if (result.statusCode < 200 || result.statusCode >= 300) {
      exitCode = 1;
    }
  } on YandeProbeException catch (error) {
    stderr.writeln(error.message);
    exitCode = 1;
  } on Object {
    stderr.writeln('transport probe failed');
    exitCode = 1;
  } finally {
    probe.close();
  }
}

class YandeProbeResult {
  const YandeProbeResult({
    required this.statusCode,
    required this.contentType,
    required this.redirectHost,
    required this.normalizedItemCount,
  });

  final int statusCode;
  final String? contentType;
  final String? redirectHost;
  final int normalizedItemCount;

  @override
  String toString() =>
      'YandeProbeResult(statusCode: $statusCode, '
      'contentType: $contentType, redirectHost: $redirectHost, '
      'normalizedItemCount: $normalizedItemCount)';
}

class YandeProbeException implements Exception {
  const YandeProbeException(this.message);

  final String message;

  @override
  String toString() => 'YandeProbeException: $message';
}

class YandeProbe {
  YandeProbe({http.Client? client, Uri? endpoint})
    : _client = client ?? http.Client(),
      _endpoint = endpoint ?? Uri.parse(_defaultEndpoint);

  final http.Client _client;
  final Uri _endpoint;

  Future<YandeProbeResult> run() async {
    final request = http.Request('GET', _endpoint)
      ..headers['accept'] = 'application/json';

    final response = await _client.send(request).timeout(_requestTimeout);
    final body = await _readBounded(response.stream).timeout(_requestTimeout);
    final finalUri = response is http.BaseResponseWithUrl
        ? (response as http.BaseResponseWithUrl).url
        : response.request?.url;
    final redirectHost = finalUri != null && finalUri.host != _endpoint.host
        ? finalUri.host
        : null;
    final contentType = _contentType(response.headers['content-type']);

    var normalizedItemCount = 0;
    if (response.statusCode >= 200 && response.statusCode < 300) {
      normalizedItemCount = _countPosts(body, contentType);
    }

    return YandeProbeResult(
      statusCode: response.statusCode,
      contentType: contentType,
      redirectHost: redirectHost,
      normalizedItemCount: normalizedItemCount,
    );
  }

  void close() => _client.close();

  static String? _contentType(String? value) =>
      value?.split(';').first.trim().toLowerCase();

  static int _countPosts(Uint8List body, String? contentType) {
    if (contentType != 'application/json') {
      throw const YandeProbeException('response was not JSON');
    }

    final dynamic decoded;
    try {
      decoded = jsonDecode(utf8.decode(body));
    } on FormatException {
      throw const YandeProbeException('response JSON was malformed');
    }

    if (decoded is! List) {
      throw const YandeProbeException('response JSON was not a post list');
    }

    return decoded.whereType<Map>().where(_hasIdentity).length;
  }

  static bool _hasIdentity(Map item) {
    final id = item['id'];
    return id is int && id > 0;
  }

  static Future<Uint8List> _readBounded(Stream<List<int>> stream) async {
    final builder = BytesBuilder(copy: false);
    var length = 0;
    await for (final chunk in stream) {
      length += chunk.length;
      if (length > _maxResponseBytes) {
        throw const YandeProbeException('response exceeded the size limit');
      }
      builder.add(chunk);
    }
    return builder.takeBytes();
  }
}
