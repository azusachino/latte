enum PopularPeriod { day, week, month }

class PopularWindow {
  const PopularWindow({required this.start, required this.end});

  final DateTime start;
  final DateTime end;

  @override
  bool operator ==(Object other) =>
      other is PopularWindow && other.start == start && other.end == end;

  @override
  int get hashCode => Object.hash(start, end);
}

class PopularQuery {
  PopularQuery({required this.period, required DateTime anchor})
    : anchor = _dateOnly(anchor),
      window = _windowFor(period, _dateOnly(anchor));

  final PopularPeriod period;
  final DateTime anchor;
  final PopularWindow window;

  String get identity =>
      '${period.name}:${_format(anchor)}:${_format(window.start)}..${_format(window.end)}';

  PopularQuery shifted(int amount) {
    final next = switch (period) {
      PopularPeriod.day => DateTime.utc(
        anchor.year,
        anchor.month,
        anchor.day + amount,
      ),
      PopularPeriod.week => DateTime.utc(
        anchor.year,
        anchor.month,
        anchor.day + (amount * 7),
      ),
      PopularPeriod.month => DateTime.utc(
        anchor.year,
        anchor.month + amount,
        1,
      ),
    };
    return PopularQuery(period: period, anchor: next);
  }

  @override
  bool operator ==(Object other) =>
      other is PopularQuery &&
      other.period == period &&
      other.anchor == anchor &&
      other.window == window;

  @override
  int get hashCode => Object.hash(period, anchor, window);

  @override
  String toString() => identity;
}

PopularWindow _windowFor(PopularPeriod period, DateTime anchor) {
  return switch (period) {
    PopularPeriod.day => PopularWindow(start: anchor, end: anchor),
    PopularPeriod.week => () {
      final start = anchor.subtract(Duration(days: anchor.weekday - 1));
      return PopularWindow(
        start: _dateOnly(start),
        end: _dateOnly(start.add(const Duration(days: 6))),
      );
    }(),
    PopularPeriod.month => PopularWindow(
      start: DateTime.utc(anchor.year, anchor.month),
      end: DateTime.utc(anchor.year, anchor.month + 1, 0),
    ),
  };
}

DateTime _dateOnly(DateTime value) =>
    DateTime.utc(value.year, value.month, value.day);

String _format(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')}';
