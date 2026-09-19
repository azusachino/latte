import 'package:flutter_test/flutter_test.dart';

import 'package:latte/main.dart';

void main() {
  testWidgets('launches the Latte shell', (WidgetTester tester) async {
    await tester.pumpWidget(const LatteApp());

    expect(find.text('Latte'), findsOneWidget);
  });
}
