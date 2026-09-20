import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/design/latte_toast.dart';
import 'package:toastification/toastification.dart';

Widget testApp(Widget child) => ToastificationWrapper(
  config: LatteToast.config,
  child: MaterialApp(home: Scaffold(body: child)),
);

Future<void> cleanUpToasts(WidgetTester tester) async {
  LatteToast.hide();
  await tester.pump(const Duration(milliseconds: 300));
  await tester.pumpAndSettle();
}

void main() {
  tearDown(() {
    toastification.dismissAll(delayForAnimation: false);
  });

  testWidgets('shows toast with message and correct icon for types', (
    tester,
  ) async {
    await tester.pumpWidget(
      testApp(
        Builder(
          builder: (context) {
            return ElevatedButton(
              onPressed: () {
                LatteToast.show(
                  context,
                  message: 'Download complete',
                  type: ToastType.success,
                );
              },
              child: const Text('Show'),
            );
          },
        ),
      ),
    );

    await tester.tap(find.text('Show'));
    await tester.pump();
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('latte-toast-pill')), findsOneWidget);
    expect(find.text('Download complete'), findsOneWidget);
    expect(find.byIcon(Icons.check_circle_outline), findsOneWidget);

    // Auto-dismisses after 2.5s
    await tester.pump(const Duration(milliseconds: 2600));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('latte-toast-pill')), findsNothing);
  });

  testWidgets('shows action widget when provided', (tester) async {
    var actionTapped = false;
    await tester.pumpWidget(
      testApp(
        Builder(
          builder: (context) {
            return ElevatedButton(
              onPressed: () {
                LatteToast.show(
                  context,
                  message: 'File downloaded',
                  action: TextButton(
                    onPressed: () => actionTapped = true,
                    child: const Text('VIEW'),
                  ),
                );
              },
              child: const Text('Show'),
            );
          },
        ),
      ),
    );

    await tester.tap(find.text('Show'));
    await tester.pump();
    await tester.pumpAndSettle();

    expect(find.text('VIEW'), findsOneWidget);
    await tester.tap(find.text('VIEW'));
    expect(actionTapped, isTrue);

    await cleanUpToasts(tester);
    expect(find.byKey(const ValueKey('latte-toast-pill')), findsNothing);
  });

  testWidgets('swiping up on toast dismisses it', (tester) async {
    await tester.pumpWidget(
      testApp(
        Builder(
          builder: (context) {
            return ElevatedButton(
              onPressed: () {
                LatteToast.show(
                  context,
                  message: 'Swipe me away',
                  duration: const Duration(seconds: 10),
                );
              },
              child: const Text('Show'),
            );
          },
        ),
      ),
    );

    await tester.tap(find.text('Show'));
    await tester.pump();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('latte-toast-pill')), findsOneWidget);

    // Swipe up
    await tester.drag(
      find.byKey(const ValueKey('latte-toast-pill')),
      const Offset(0, -300),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('latte-toast-pill')), findsNothing);
  });

  testWidgets('showing multiple toasts stacks them vertically', (tester) async {
    await tester.pumpWidget(
      testApp(
        Builder(
          builder: (context) {
            return Column(
              children: [
                ElevatedButton(
                  onPressed: () {
                    LatteToast.show(context, message: 'First toast');
                  },
                  child: const Text('First'),
                ),
                ElevatedButton(
                  onPressed: () {
                    LatteToast.show(context, message: 'Second toast');
                  },
                  child: const Text('Second'),
                ),
              ],
            );
          },
        ),
      ),
    );

    await tester.tap(find.text('First'));
    await tester.pump();
    await tester.pumpAndSettle();
    expect(find.text('First toast'), findsOneWidget);

    await tester.tap(find.text('Second'));
    await tester.pump();
    await tester.pumpAndSettle();
    expect(find.text('First toast'), findsOneWidget);
    expect(find.text('Second toast'), findsOneWidget);

    await cleanUpToasts(tester);
  });
}
