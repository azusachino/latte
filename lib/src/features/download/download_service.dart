import 'package:flutter/services.dart';

import '../../domain/post.dart';
import '../../sites/site_adapter.dart';

class DownloadReceipt {
  const DownloadReceipt({
    required this.status,
    required this.contentUri,
    required this.album,
    required this.displayName,
  });

  final DownloadStatus status;
  final String contentUri;
  final String album;
  final String displayName;
}

enum DownloadStatus { started, alreadyRunning, completed, alreadySaved }

abstract interface class DownloadStore {
  Future<DownloadReceipt> save({
    required ResolvedMedia media,
    required String displayName,
    bool force = false,
  });
}

class MethodChannelDownloadStore implements DownloadStore {
  MethodChannelDownloadStore({MethodChannel? channel})
    : _channel =
          channel ?? const MethodChannel('com.azusachino.latte/download');

  final MethodChannel _channel;

  @override
  Future<DownloadReceipt> save({
    required ResolvedMedia media,
    required String displayName,
    bool force = false,
  }) async {
    final result = await _channel.invokeMapMethod<String, dynamic>(
      'saveImage',
      <String, Object?>{
        'sourceUrl': media.source.toString(),
        'displayName': displayName,
        'mimeType': _mimeType(media.variant.extension),
        'force': force,
      },
    );
    if (result == null) {
      throw const DownloadException('Latte could not save this image');
    }
    final status = switch (result['status']) {
      'started' => DownloadStatus.started,
      'already_running' => DownloadStatus.alreadyRunning,
      'already_saved' => DownloadStatus.alreadySaved,
      _ => DownloadStatus.completed,
    };
    return DownloadReceipt(
      status: status,
      contentUri: result['contentUri'] as String? ?? '',
      album: result['album'] as String? ?? 'Pictures/Latte',
      displayName: result['displayName'] as String? ?? displayName,
    );
  }
}

class DownloadService {
  DownloadService({required this.adapter, DownloadStore? store})
    : _store = store ?? MethodChannelDownloadStore();

  final SiteAdapter adapter;
  final DownloadStore _store;

  Future<DownloadReceipt> save({
    required PostRef reference,
    required MediaVariant variant,
    bool force = false,
  }) async {
    final media = await adapter.resolveMedia(reference, variant.id);
    return _store.save(
      media: media,
      displayName: _displayName(media),
      force: force,
    );
  }

  static String _displayName(ResolvedMedia media) {
    final extension = media.variant.extension?.toLowerCase() ?? 'jpg';
    final safeExtension = extension.replaceAll(RegExp(r'[^a-z0-9]'), '');
    return 'latte_${media.reference.siteId.value}_'
        '${media.reference.remoteId}_${media.variant.id.name}.'
        '${safeExtension.isEmpty ? 'jpg' : safeExtension}';
  }
}

class DownloadException implements Exception {
  const DownloadException(this.message);

  final String message;

  @override
  String toString() => message;
}

String _mimeType(String? extension) => switch (extension?.toLowerCase()) {
  'png' => 'image/png',
  'webp' => 'image/webp',
  'gif' => 'image/gif',
  _ => 'image/jpeg',
};
