/// Port de `src/lib/stats.ts`: análisis de series de peso y nutrición.
library;

import 'dates.dart';
import 'models.dart';

class DataPoint {
  const DataPoint({required this.date, required this.value});

  final String date;
  final double value;
}

/// Media móvil de ventana de calendario (inclusiva). Tolerante a huecos: los
/// días sin registros no rompen la media, simplemente no aportan valor.
List<double?> movingAverageByDays(List<DataPoint> points, int windowDays) {
  final windowStartOffsets = points.map((point) => addDaysToKey(point.date, -(windowDays - 1))).toList();
  final result = <double?>[];
  for (var i = 0; i < points.length; i++) {
    final start = windowStartOffsets[i];
    final valuesInWindow = <double>[];
    for (var j = 0; j <= i; j++) {
      if (points[j].date.compareTo(start) >= 0) {
        valuesInWindow.add(points[j].value);
      }
    }
    if (valuesInWindow.isEmpty) {
      result.add(null);
    } else {
      result.add(valuesInWindow.reduce((a, b) => a + b) / valuesInWindow.length);
    }
  }
  return result;
}

/// Pendiente por mínimos cuadrados (unidades por día). Null si hay menos de
/// dos fechas distintas (den=0).
double? linearSlopePerDay(List<DataPoint> points) {
  if (points.length < 2) return null;
  final x0 = DateTime.parse('${points.first.date}T00:00:00').millisecondsSinceEpoch;
  final xs = points
      .map((point) => (DateTime.parse('${point.date}T00:00:00').millisecondsSinceEpoch - x0) / 86400000)
      .toList();
  final ys = points.map((point) => point.value).toList();
  final n = points.length;
  final meanX = xs.reduce((a, b) => a + b) / n;
  final meanY = ys.reduce((a, b) => a + b) / n;
  var num = 0.0;
  var den = 0.0;
  for (var i = 0; i < n; i++) {
    num += (xs[i] - meanX) * (ys[i] - meanY);
    den += (xs[i] - meanX) * (xs[i] - meanX);
  }
  if (den == 0) return null;
  return num / den;
}

/// Ritmo de cambio expresado por semana.
double? linearRatePerWeek(List<DataPoint> points) {
  final slope = linearSlopePerDay(points);
  return slope == null ? null : slope * 7;
}

String _previousMonday(String key) {
  final date = DateTime.parse('${key}T00:00:00');
  final diff = date.weekday - DateTime.monday; // 0..6
  return addDaysToKey(key, -diff);
}

/// Agrupa puntos en semanas ISO (desde el lunes) y promedia cada una.
/// Solo se devuelven semanas con al menos un registro.
List<({String weekStart, double avg})> weeklyAverages(List<DataPoint> points) {
  final buckets = <String, List<double>>{};
  for (final point in points) {
    final weekStart = _previousMonday(point.date);
    buckets.putIfAbsent(weekStart, () => <double>[]).add(point.value);
  }
  final keys = buckets.keys.toList()..sort();
  return keys
      .map((weekStart) => (
            weekStart: weekStart,
            avg: buckets[weekStart]!.reduce((a, b) => a + b) / buckets[weekStart]!.length,
          ))
      .toList();
}

/// Serie diaria densa entre dos claves (inclusive): los días sin comidas
/// aparecen con ceros para mostrar huecos honestos en los gráficos.
List<DailyNutritionPoint> buildDailyNutritionSeries(
  Map<String, DailyNutritionPoint> totalsByDate,
  String fromKey,
  String toKey,
) {
  final series = <DailyNutritionPoint>[];
  var cursor = fromKey;
  while (daysBetweenKeys(cursor, toKey) >= 0) {
    final entry = totalsByDate[cursor];
    series.add(entry ??
        DailyNutritionPoint(date: cursor, calories: 0, protein: 0, carbs: 0, fat: 0));
    cursor = addDaysToKey(cursor, 1);
  }
  return series;
}

/// Días cubiertos por un rango nominado; null para "todo".
int? rangeToDays(StatsRange range) {
  switch (range) {
    case StatsRange.d7:
      return 7;
    case StatsRange.d30:
      return 30;
    case StatsRange.d90:
      return 90;
    case StatsRange.all:
      return null;
  }
}