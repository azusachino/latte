import 'package:flutter/material.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({
    required this.themeMode,
    required this.onThemeModeChanged,
    super.key,
  });

  final ThemeMode themeMode;
  final ValueChanged<ThemeMode> onThemeModeChanged;

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
            groupValue: themeMode,
            onChanged: (value) {
              if (value != null) onThemeModeChanged(value);
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
