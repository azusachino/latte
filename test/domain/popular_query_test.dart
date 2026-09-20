import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/popular_query.dart';

void main() {
  test('normalizes the anchor and derives a calendar window', () {
    final query = PopularQuery(
      period: PopularPeriod.week,
      anchor: DateTime(2026, 9, 20, 23, 45),
    );

    expect(query.anchor, DateTime.utc(2026, 9, 20));
    expect(query.window.start, DateTime.utc(2026, 9, 14));
    expect(query.window.end, DateTime.utc(2026, 9, 20));
  });

  test('period and anchor are part of stable query identity', () {
    final day = PopularQuery(
      period: PopularPeriod.day,
      anchor: DateTime.utc(2026, 9, 20),
    );
    final sameDay = PopularQuery(
      period: PopularPeriod.day,
      anchor: DateTime.utc(2026, 9, 20, 8),
    );
    final month = PopularQuery(
      period: PopularPeriod.month,
      anchor: DateTime.utc(2026, 9, 20),
    );

    expect(day, sameDay);
    expect(day.identity, 'day:2026-09-20:2026-09-20..2026-09-20');
    expect(day, isNot(month));
    expect(day.hashCode, sameDay.hashCode);
  });

  test('shifts by one period while preserving normalized windows', () {
    final month = PopularQuery(
      period: PopularPeriod.month,
      anchor: DateTime.utc(2026, 1, 31),
    );

    expect(month.shifted(-1).anchor, DateTime.utc(2025, 12, 1));
    expect(month.shifted(1).anchor, DateTime.utc(2026, 2, 1));
  });
}
