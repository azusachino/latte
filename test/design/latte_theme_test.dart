import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/design/latte_theme.dart';
import 'package:latte/src/app.dart';

void main() {
  test('light and dark themes expose Material 3 semantic roles', () {
    final light = LatteTheme.light();
    final dark = LatteTheme.dark();

    expect(light.useMaterial3, isTrue);
    expect(dark.useMaterial3, isTrue);
    expect(light.colorScheme.surface, isNot(equals(light.colorScheme.primary)));
    expect(dark.brightness, Brightness.dark);
    expect(light.textTheme.titleLarge, isNotNull);
    expect(LatteTheme.pagePadding, const EdgeInsets.all(16));
  });

  testWidgets('compact shell keeps labelled actions and content visible', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: LatteTheme.light(),
        home: LatteShell(
          title: 'Explore',
          body: const Text('Content'),
          onSearch: () {},
        ),
      ),
    );

    await tester.binding.setSurfaceSize(const Size(320, 640));
    await tester.pump();

    expect(find.byKey(const ValueKey('compact-shell')), findsOneWidget);
    expect(find.text('Content'), findsOneWidget);
    expect(find.byTooltip('Search'), findsOneWidget);
    expect(find.text('Safe Mode'), findsNothing);
    expect(tester.getSize(find.byTooltip('Search')), const Size(48, 48));

    addTearDown(() => tester.binding.setSurfaceSize(null));
  });

  testWidgets('expanded shell exposes the same content in an expanded layout', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(800, 640));
    await tester.pumpWidget(
      MaterialApp(
        theme: LatteTheme.dark(),
        home: LatteShell(
          title: 'Explore',
          body: const Text('Content'),
          onSearch: () {},
        ),
      ),
    );
    await tester.pump();

    expect(find.byKey(const ValueKey('expanded-shell')), findsOneWidget);
    expect(find.text('Content'), findsOneWidget);
    expect(find.byTooltip('Search'), findsOneWidget);
    expect(find.text('Safe Mode'), findsNothing);
    expect(find.byType(FilterChip), findsNothing);

    addTearDown(() => tester.binding.setSurfaceSize(null));
  });
}
