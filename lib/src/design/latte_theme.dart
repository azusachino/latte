import 'package:flutter/material.dart';

class LatteTheme {
  const LatteTheme._();

  static const pagePadding = EdgeInsets.all(16);
  static const contentGap = 16.0;
  static const cardRadius = BorderRadius.all(Radius.circular(12));

  static ThemeData light() => _theme(Brightness.light);

  static ThemeData dark() => _theme(Brightness.dark);

  static ThemeData _theme(Brightness brightness) {
    final scheme = ColorScheme.fromSeed(
      seedColor: const Color(0xFF675D56),
      brightness: brightness,
    );
    return ThemeData(
      brightness: brightness,
      colorScheme: scheme,
      textTheme: brightness == Brightness.dark
          ? Typography.material2021().white
          : Typography.material2021().black,
      useMaterial3: true,
      cardTheme: const CardThemeData(
        shape: RoundedRectangleBorder(borderRadius: cardRadius),
      ),
    );
  }
}
