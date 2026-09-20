import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/features/cache/cache_manager.dart';

class FakeCacheStore implements CacheStore {
  FakeCacheStore({this.size = 0, this.clearResult = true});

  int size;
  bool clearResult;
  int clearCalls = 0;

  @override
  Future<int> getSizeBytes() async => size;

  @override
  Future<bool> clear() async {
    clearCalls++;
    return clearResult;
  }
}

void main() {
  group('LatteCacheManager.formatBytes', () {
    test('formats zero and negative bytes', () {
      expect(LatteCacheManager.formatBytes(0), '0 B');
      expect(LatteCacheManager.formatBytes(-10), '0 B');
    });

    test('formats small bytes under 1 KB', () {
      expect(LatteCacheManager.formatBytes(512), '512 B');
      expect(LatteCacheManager.formatBytes(1023), '1023 B');
    });

    test('formats kilobytes', () {
      expect(LatteCacheManager.formatBytes(1024), '1.0 KB');
      expect(LatteCacheManager.formatBytes(1536), '1.5 KB');
    });

    test('formats megabytes', () {
      expect(LatteCacheManager.formatBytes(1024 * 1024), '1.0 MB');
      expect(
        LatteCacheManager.formatBytes((1024 * 1024 * 2.5).round()),
        '2.5 MB',
      );
    });

    test('formats gigabytes', () {
      expect(LatteCacheManager.formatBytes(1024 * 1024 * 1024), '1.00 GB');
      expect(
        LatteCacheManager.formatBytes((1024 * 1024 * 1024 * 3.75).round()),
        '3.75 GB',
      );
    });
  });

  group('LatteCacheManager operations', () {
    test('delegates size check to store', () async {
      final store = FakeCacheStore(size: 1024 * 500);
      final manager = LatteCacheManager(store: store);

      final size = await manager.getCacheSizeBytes();
      expect(size, 1024 * 500);
    });

    test('delegates cache clear to store', () async {
      final store = FakeCacheStore(clearResult: true);
      final manager = LatteCacheManager(store: store);

      final result = await manager.clearCache();
      expect(result, isTrue);
      expect(store.clearCalls, 1);
    });
  });
}
