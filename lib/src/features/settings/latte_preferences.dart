import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class LattePreferences {
  LattePreferences({LattePreferenceStore? store})
    : _store = store ?? SharedPreferencesLattePreferenceStore();

  static const _themeModeKey = 'theme_mode';
  static const _columnCountKey = 'column_count';

  final LattePreferenceStore _store;

  Future<ThemeMode> loadThemeMode() async {
    return switch (await _store.readString(_themeModeKey)) {
      'light' => ThemeMode.light,
      'dark' => ThemeMode.dark,
      _ => ThemeMode.system,
    };
  }

  Future<int?> loadColumnCount() async {
    final value = await _store.readInt(_columnCountKey);
    return switch (value) {
      2 || 3 || 4 => value,
      _ => null,
    };
  }

  Future<void> saveThemeMode(ThemeMode mode) =>
      _store.writeString(_themeModeKey, mode.name);

  Future<void> saveColumnCount(int? value) {
    if (value == null) return _store.remove(_columnCountKey);
    if (value != 2 && value != 3 && value != 4) return Future.value();
    return _store.writeInt(_columnCountKey, value);
  }
}

abstract interface class LattePreferenceStore {
  Future<String?> readString(String key);

  Future<int?> readInt(String key);

  Future<void> writeString(String key, String value);

  Future<void> writeInt(String key, int value);

  Future<void> remove(String key);
}

class SharedPreferencesLattePreferenceStore implements LattePreferenceStore {
  SharedPreferencesAsync? _preferences;

  SharedPreferencesAsync get _store =>
      _preferences ??= SharedPreferencesAsync();

  @override
  Future<String?> readString(String key) async {
    try {
      return await _store.getString(key);
    } on StateError {
      return null;
    }
  }

  @override
  Future<int?> readInt(String key) async {
    try {
      return await _store.getInt(key);
    } on StateError {
      return null;
    }
  }

  @override
  Future<void> writeString(String key, String value) async {
    try {
      await _store.setString(key, value);
    } on StateError {
      // Platform persistence is unavailable in pure widget tests.
    }
  }

  @override
  Future<void> writeInt(String key, int value) async {
    try {
      await _store.setInt(key, value);
    } on StateError {
      // Platform persistence is unavailable in pure widget tests.
    }
  }

  @override
  Future<void> remove(String key) async {
    try {
      await _store.remove(key);
    } on StateError {
      // Platform persistence is unavailable in pure widget tests.
    }
  }
}
