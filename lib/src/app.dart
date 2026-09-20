import 'package:flutter/material.dart';

import 'design/latte_theme.dart';

class LatteApp extends StatelessWidget {
  const LatteApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Latte',
      theme: LatteTheme.light(),
      darkTheme: LatteTheme.dark(),
      themeMode: ThemeMode.system,
      home: const LatteShell(body: SizedBox.shrink()),
    );
  }
}

class LatteShell extends StatelessWidget {
  const LatteShell({
    required this.body,
    this.title = 'Latte',
    this.onSearch,
    this.safeMode = false,
    this.onSafeModeChanged,
    super.key,
  });

  final String title;
  final Widget body;
  final VoidCallback? onSearch;
  final bool safeMode;
  final ValueChanged<bool>? onSafeModeChanged;

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
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: FilterChip(
                  label: const Text('Safe Mode'),
                  selected: safeMode,
                  onSelected: onSafeModeChanged,
                ),
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
