import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'design/latte_theme.dart';
import 'features/explore/explore_controller.dart';
import 'features/explore/explore_screen.dart';
import 'features/settings/settings_screen.dart';
import 'features/settings/latte_preferences.dart';
import 'sites/site_adapter.dart';
import 'sites/site_registry.dart';
import 'sites/yandere/yandere_adapter.dart';

class LatteApp extends StatefulWidget {
  const LatteApp({
    this.adapter,
    this.siteRegistry,
    this.preferences,
    super.key,
  });

  final SiteAdapter? adapter;
  final SiteRegistry? siteRegistry;
  final LattePreferences? preferences;

  @override
  State<LatteApp> createState() => _LatteAppState();
}

class _LatteAppState extends State<LatteApp> {
  late final ExploreController _controller;
  late final LattePreferences _preferences;
  final _navigatorKey = GlobalKey<NavigatorState>();
  var _themeMode = ThemeMode.system;
  int? _columnCount;

  @override
  void initState() {
    super.initState();
    final adapter =
        widget.adapter ?? widget.siteRegistry?.defaultAdapter ?? YandeAdapter();
    _controller = ExploreController(adapter: adapter);
    _preferences = widget.preferences ?? LattePreferences();
    unawaited(_loadPreferences());
  }

  @override
  void dispose() {
    _controller.dispose();
    final adapter = _controller.adapter;
    if (adapter case YandeAdapter()) adapter.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: _navigatorKey,
      title: 'Latte',
      theme: LatteTheme.light(),
      darkTheme: LatteTheme.dark(),
      themeMode: _themeMode,
      home: ExploreScreen(
        controller: _controller,
        columnCount: _columnCount,
        onSettings: () {
          _navigatorKey.currentState?.push<void>(
            MaterialPageRoute<void>(
              builder: (_) => SettingsScreen(
                themeMode: _themeMode,
                onThemeModeChanged: (mode) {
                  setState(() => _themeMode = mode);
                  unawaited(_preferences.saveThemeMode(mode));
                },
                columnCount: _columnCount,
                onOpenDownloadNotifications: () {
                  unawaited(
                    const MethodChannel('com.azusachino.latte/download')
                        .invokeMethod<void>('openDownloadNotifications'),
                  );
                },
                onColumnCountChanged: (value) {
                  setState(() => _columnCount = value);
                  unawaited(_preferences.saveColumnCount(value));
                },
              ),
            ),
          );
        },
      ),
    );
  }

  Future<void> _loadPreferences() async {
    try {
      final values = await Future.wait<Object?>([
        _preferences.loadThemeMode(),
        _preferences.loadColumnCount(),
      ]);
      if (!mounted) return;
      setState(() {
        _themeMode = values[0]! as ThemeMode;
        _columnCount = values[1] as int?;
      });
    } on Exception {
      // Widget tests and first-run platform initialization may not expose a
      // preferences channel yet; in-memory defaults remain usable.
    }
  }
}

class LatteShell extends StatelessWidget {
  const LatteShell({
    required this.body,
    this.title = 'Latte',
    this.onSearch,
    super.key,
  });

  final String title;
  final Widget body;
  final VoidCallback? onSearch;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final expanded = constraints.maxWidth >= 600;
        return Scaffold(
          appBar: AppBar(
            title: Text(title),
            actions: [
              IconButton(
                icon: const Icon(Icons.search),
                onPressed: onSearch,
                tooltip: 'Search',
                constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
              ),
            ],
          ),
          body: KeyedSubtree(
            key: ValueKey(expanded ? 'expanded-shell' : 'compact-shell'),
            child: Padding(padding: LatteTheme.pagePadding, child: body),
          ),
        );
      },
    );
  }
}
