import 'package:flutter/material.dart';

import 'design/latte_theme.dart';
import 'features/explore/explore_controller.dart';
import 'features/explore/explore_screen.dart';
import 'features/settings/settings_screen.dart';
import 'sites/site_adapter.dart';
import 'sites/yandere/yandere_adapter.dart';

class LatteApp extends StatefulWidget {
  const LatteApp({this.adapter, super.key});

  final SiteAdapter? adapter;

  @override
  State<LatteApp> createState() => _LatteAppState();
}

class _LatteAppState extends State<LatteApp> {
  late final ExploreController _controller;
  final _navigatorKey = GlobalKey<NavigatorState>();
  var _themeMode = ThemeMode.system;

  @override
  void initState() {
    super.initState();
    _controller = ExploreController(adapter: widget.adapter ?? YandeAdapter());
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
        onSettings: () {
          _navigatorKey.currentState?.push<void>(
            MaterialPageRoute<void>(
              builder: (_) => SettingsScreen(
                themeMode: _themeMode,
                onThemeModeChanged: (mode) => setState(() => _themeMode = mode),
              ),
            ),
          );
        },
      ),
    );
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
