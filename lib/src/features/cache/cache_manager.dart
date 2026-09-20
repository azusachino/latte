import 'package:extended_image/extended_image.dart';
import 'package:flutter/painting.dart';

abstract interface class CacheStore {
  Future<int> getSizeBytes();
  Future<bool> clear();
}

class ExtendedImageCacheStore implements CacheStore {
  const ExtendedImageCacheStore();

  @override
  Future<int> getSizeBytes() async {
    try {
      return await getCachedSizeBytes();
    } on Object {
      return 0;
    }
  }

  @override
  Future<bool> clear() async {
    try {
      final cleared = await clearDiskCachedImages();
      PaintingBinding.instance.imageCache.clear();
      PaintingBinding.instance.imageCache.clearLiveImages();
      return cleared;
    } on Object {
      return false;
    }
  }
}

class LatteCacheManager {
  LatteCacheManager({CacheStore? store})
    : _store = store ?? const ExtendedImageCacheStore();

  final CacheStore _store;

  Future<int> getCacheSizeBytes() => _store.getSizeBytes();

  Future<bool> clearCache() => _store.clear();

  static String formatBytes(int bytes) {
    if (bytes <= 0) return '0 B';
    if (bytes < 1024) return '$bytes B';
    final kb = bytes / 1024;
    if (kb < 1024) return '${kb.toStringAsFixed(1)} KB';
    final mb = kb / 1024;
    if (mb < 1024) return '${mb.toStringAsFixed(1)} MB';
    final gb = mb / 1024;
    return '${gb.toStringAsFixed(2)} GB';
  }
}
