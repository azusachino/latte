import '../domain/post.dart';
import 'site_adapter.dart';

/// The application-facing catalog of supported site adapters.
///
/// Site selection is not part of the first Yande journey, but keeping the
/// catalog separate means adding another platform does not widen the explore
/// controller or make it know site names.
abstract interface class SiteRegistry {
  List<SiteAdapter> get adapters;

  SiteAdapter get defaultAdapter;

  SiteAdapter? find(SiteId id);
}

class LatteSiteRegistry implements SiteRegistry {
  LatteSiteRegistry(Iterable<SiteAdapter> adapters)
    : adapters = List<SiteAdapter>.unmodifiable(adapters) {
    if (this.adapters.isEmpty) {
      throw ArgumentError.value(adapters, 'adapters', 'must not be empty');
    }
    final ids = <SiteId>{};
    for (final adapter in this.adapters) {
      if (!ids.add(adapter.descriptor.id)) {
        throw ArgumentError.value(
          adapters,
          'adapters',
          'site IDs must be unique',
        );
      }
    }
  }

  @override
  final List<SiteAdapter> adapters;

  @override
  SiteAdapter get defaultAdapter => adapters.first;

  @override
  SiteAdapter? find(SiteId id) {
    for (final adapter in adapters) {
      if (adapter.descriptor.id == id) return adapter;
    }
    return null;
  }
}
