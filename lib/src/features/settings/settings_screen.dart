import 'package:flutter/material.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    required this.themeMode,
    required this.onThemeModeChanged,
    required this.columnCount,
    required this.onColumnCountChanged,
    super.key,
  });

  final ThemeMode themeMode;
  final ValueChanged<ThemeMode> onThemeModeChanged;
  final int? columnCount;
  final ValueChanged<int?> onColumnCountChanged;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late ThemeMode _themeMode;
  late int? _columnCount;

  @override
  void initState() {
    super.initState();
    _themeMode = widget.themeMode;
    _columnCount = widget.columnCount;
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
