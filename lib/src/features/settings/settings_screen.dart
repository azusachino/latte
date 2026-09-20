import 'package:flutter/material.dart';

import '../../design/latte_toast.dart';
import '../cache/cache_manager.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    required this.themeMode,
    required this.onThemeModeChanged,
    required this.columnCount,
    required this.onColumnCountChanged,
    this.onOpenDownloadNotifications,
    this.cacheManager,
    super.key,
  });

  final ThemeMode themeMode;
  final ValueChanged<ThemeMode> onThemeModeChanged;
  final int? columnCount;
  final ValueChanged<int?> onColumnCountChanged;
  final VoidCallback? onOpenDownloadNotifications;
  final LatteCacheManager? cacheManager;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late ThemeMode _themeMode;
  late int? _columnCount;
  late final LatteCacheManager _cacheManager;
  var _cacheSizeBytes = 0;
  var _isClearingCache = false;

  @override
  void initState() {
    super.initState();
    _themeMode = widget.themeMode;
    _columnCount = widget.columnCount;
    _cacheManager = widget.cacheManager ?? LatteCacheManager();
    _loadCacheSize();
  }

  Future<void> _loadCacheSize() async {
    final size = await _cacheManager.getCacheSizeBytes();
    if (mounted) setState(() => _cacheSizeBytes = size);
  }

  Future<void> _clearCache() async {
    setState(() => _isClearingCache = true);
    await _cacheManager.clearCache();
    final size = await _cacheManager.getCacheSizeBytes();
    if (!mounted) return;
    setState(() {
      _cacheSizeBytes = size;
      _isClearingCache = false;
    });
    LatteToast.show(
      context,
      message: 'Image cache cleared',
      type: ToastType.success,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        children: [
          const ListTile(
            title: Text('Appearance'),
            subtitle: Text('Choose how Latte follows the device theme'),
          ),
          RadioGroup<ThemeMode>(
            groupValue: _themeMode,
            onChanged: (value) {
              if (value == null) return;
              setState(() => _themeMode = value);
              widget.onThemeModeChanged(value);
            },
            child: Column(
              children: [
                for (final option in _themeOptions)
                  RadioListTile<ThemeMode>(
                    value: option.mode,
                    title: Text(option.label),
                  ),
              ],
            ),
          ),
          const Divider(),
          const ListTile(
            title: Text('Explore'),
            subtitle: Text('Use the same dense image browsing workflow'),
          ),
          ListTile(
            title: const Text('Automatic'),
            subtitle: const Text('Two compact columns, four expanded columns'),
            trailing: _columnCount == null ? const Icon(Icons.check) : null,
            onTap: () {
              setState(() => _columnCount = null);
              widget.onColumnCountChanged(null);
            },
          ),
          RadioGroup<int>(
            groupValue: _columnCount ?? 0,
            onChanged: (value) {
              if (value == null) return;
              setState(() => _columnCount = value);
              widget.onColumnCountChanged(value);
            },
            child: Column(
              children: [
                for (final columns in _columnOptions)
                  RadioListTile<int>(
                    value: columns,
                    title: Text('$columns columns'),
                  ),
              ],
            ),
          ),
          const Divider(),
          const ListTile(
            leading: Icon(Icons.download_outlined),
            title: Text('Download quality'),
            subtitle: Text('Best available quality'),
            trailing: Icon(Icons.check),
          ),
          ListTile(
            leading: const Icon(Icons.notifications_outlined),
            title: const Text('Download notifications'),
            subtitle: const Text('Managed by Android system settings'),
            onTap: widget.onOpenDownloadNotifications,
          ),
          const Divider(),
          const ListTile(
            title: Text('Storage & Cache'),
            subtitle: Text('Manage temporary downloaded pictures'),
          ),
          ListTile(
            leading: const Icon(Icons.storage_outlined),
            title: const Text('Image cache'),
            subtitle: Text(LatteCacheManager.formatBytes(_cacheSizeBytes)),
            trailing: _isClearingCache
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : TextButton(
                    onPressed: _cacheSizeBytes <= 0 ? null : _clearCache,
                    child: const Text('Clear'),
                  ),
          ),
        ],
      ),
    );
  }
}

const _themeOptions = [
  (mode: ThemeMode.system, label: 'System default'),
  (mode: ThemeMode.light, label: 'Light'),
  (mode: ThemeMode.dark, label: 'Dark'),
];

const _columnOptions = [2, 3, 4];
