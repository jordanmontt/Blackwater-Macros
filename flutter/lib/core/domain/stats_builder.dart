/// Port de `src/lib/stats-builder.ts`: cálculo puro compartido por el
/// servidor y el cliente/estadísticas.
library;

import 'dates.dart';
import 'models.dart';
import 'nutrition.dart';
import 'stats.dart';

const int trendWindowDays = 7;

/// Forma mínima de una comida para calcular la serie de nutrición diaria.
class StatsMeal {
  const StatsMeal({
    required this.logDate,
    required this.resolvedCalories,
    required this.resolvedProtein,
    this.resolvedCarbs = 0,
    this.resolvedFat = 0,
  });

  final String logDate;
  final double resolvedCalories;
  final double resolvedProtein;
  final double resolvedCarbs;
  final double resolvedFat;
}

/// Forma mínima de un peso para calcular series de peso/grasa/masa magra.
class StatsWeight {
  const StatsWeight({
    required this.measuredAt,
    required this.weightKg,
    this.bodyFatPct,
  });

  final DateTime measuredAt;
  final double weightKg;
  final double? bodyFatPct;
}

StatsSummary buildStatsFromData(
  List<StatsMeal> meals,
  List<StatsWeight> weights,
  StatsRange range,
  String todayKeyParam,
) {
  final days = rangeToDays(range);
  final toKey = todayKeyParam;
  final fromKey = days == null ? null : addDaysToKey(toKey, -(days - 1));

  // --- Nutrición diaria ---
  final totalsByDate = <String, DailyNutritionPoint>{};
  for (final meal in meals) {
    final current = totalsByDate.putIfAbsent(
      meal.logDate,
      () => DailyNutritionPoint(date: meal.logDate, calories: 0, protein: 0, carbs: 0, fat: 0),
    );
    final updated = DailyNutritionPoint(
      date: meal.logDate,
      calories: current.calories + meal.resolvedCalories,
      protein: current.protein + meal.resolvedProtein,
      carbs: current.carbs + meal.resolvedCarbs,
      fat: current.fat + meal.resolvedFat,
    );
    totalsByDate[meal.logDate] = updated;
  }

  final seriesFrom = fromKey ?? (meals.isEmpty ? toKey : meals.first.logDate);
  final nutritionSeries = (meals.isNotEmpty || fromKey != null)
      ? buildDailyNutritionSeries(totalsByDate, seriesFrom, toKey)
      : <DailyNutritionPoint>[];

  List<DailyNutritionPoint> positivePoints(double Function(DailyNutritionPoint) pick) =>
      nutritionSeries.where((point) => pick(point) > 0).toList();

  final caloriesPoints = positivePoints((point) => point.calories);
  final proteinPoints = positivePoints((point) => point.protein);
  final carbsPoints = positivePoints((point) => point.carbs);
  final fatPoints = positivePoints((point) => point.fat);

  // --- Peso ---
  final fromTime = fromKey == null
      ? 0
      : DateTime.parse('${fromKey}T00:00:00').millisecondsSinceEpoch;
  final inRange = weights
      .where((row) => row.measuredAt.millisecondsSinceEpoch >= fromTime)
      .toList();
  final weightPoints = inRange
      .map((row) => DataPoint(date: toDateKey(row.measuredAt), value: row.weightKg))
      .toList();

  final trendValues = movingAverageByDays(weightPoints, trendWindowDays);
  final weightSeries = List.generate(weightPoints.length, (i) {
    final point = weightPoints[i];
    final trend = trendValues[i];
    return TrendPoint(
      date: point.date,
      value: round2(point.value),
      trend: trend == null ? null : round1(trend),
    );
  });

  // --- Grasa corporal (solo entradas válidas) ---
  final bodyFatRows = inRange
      .where((row) => row.bodyFatPct != null && row.bodyFatPct! > 0 && row.bodyFatPct! < 100)
      .toList();
  final bodyFatPoints = bodyFatRows
      .map((row) => DataPoint(date: toDateKey(row.measuredAt), value: row.bodyFatPct!))
      .toList();
  final bodyFatTrendValues = movingAverageByDays(bodyFatPoints, trendWindowDays);
  final bodyFatSeries = List.generate(bodyFatPoints.length, (i) {
    final trend = bodyFatTrendValues[i];
    return TrendPoint(
      date: bodyFatPoints[i].date,
      value: round1(bodyFatPoints[i].value),
      trend: trend == null ? null : round1(trend),
    );
  });

  // --- Masa magra (peso × (1 - grasa/100)) ---
  final leanMassPoints = bodyFatRows
      .map((row) => DataPoint(
            date: toDateKey(row.measuredAt),
            value: round2(row.weightKg * (1 - row.bodyFatPct! / 100)),
          ))
      .toList();
  final leanMassTrendValues = movingAverageByDays(leanMassPoints, trendWindowDays);
  final leanMassSeries = List.generate(leanMassPoints.length, (i) {
    final trend = leanMassTrendValues[i];
    return TrendPoint(
      date: leanMassPoints[i].date,
      value: round2(leanMassPoints[i].value),
      trend: trend == null ? null : round1(trend),
    );
  });

  final lastValue = weightPoints.isEmpty ? null : weightPoints.last.value;
  final firstValue = weightPoints.isEmpty ? null : weightPoints.first.value;
  final lastTrend = trendValues.reversed.where((value) => value != null).cast<double?>().isEmpty
      ? null
      : trendValues.reversed.where((value) => value != null).first;
  final ratePerWeekKg = linearRatePerWeek(weightPoints);

  final lastBodyFat = bodyFatPoints.isEmpty ? null : bodyFatPoints.last.value;
  final firstBodyFat = bodyFatPoints.isEmpty ? null : bodyFatPoints.first.value;
  final lastLeanMass = leanMassPoints.isEmpty ? null : leanMassPoints.last.value;
  final firstLeanMass = leanMassPoints.isEmpty ? null : leanMassPoints.first.value;

  const double? nullDouble = null;

  double? minOrNull(List<DataPoint> pts) =>
      pts.isEmpty ? nullDouble : pts.map((p) => p.value).reduce((a, b) => a < b ? a : b);

  double? maxOrNull(List<DataPoint> pts) =>
      pts.isEmpty ? nullDouble : pts.map((p) => p.value).reduce((a, b) => a > b ? a : b);

  final weightSummary = CompositionStats(
    currentWeightKg: lastValue == null ? null : round2(lastValue),
    currentTrendKg: lastTrend == null ? null : round1(lastTrend),
    changeSinceStartKg: firstValue != null && lastValue != null
        ? round2(lastValue - firstValue)
        : null,
    ratePerWeekKg: ratePerWeekKg == null ? null : round2(ratePerWeekKg),
    minKg: weightPoints.isEmpty ? null : round2(minOrNull(weightPoints)!),
    maxKg: weightPoints.isEmpty ? null : round2(maxOrNull(weightPoints)!),
    currentBodyFatPct: lastBodyFat == null ? null : round1(lastBodyFat),
    changeBodyFatPct: firstBodyFat != null && lastBodyFat != null
        ? round1(lastBodyFat - firstBodyFat)
        : null,
    minBodyFatPct: bodyFatPoints.isEmpty ? null : round1(minOrNull(bodyFatPoints)!),
    maxBodyFatPct: bodyFatPoints.isEmpty ? null : round1(maxOrNull(bodyFatPoints)!),
    currentLeanMassKg: lastLeanMass == null ? null : round2(lastLeanMass),
    changeLeanMassKg: firstLeanMass != null && lastLeanMass != null
        ? round2(lastLeanMass - firstLeanMass)
        : null,
  );

  final weeklyWeight = weeklyAverages(weightPoints)
      .map((week) => WeeklyWeightAverage(weekStart: week.weekStart, avg: round2(week.avg)))
      .toList();

  return StatsSummary(
    calories: nutritionSeries
        .map((point) => DailyNutritionPoint(
              date: point.date,
              calories: round1(point.calories),
              protein: point.protein,
              carbs: point.carbs,
              fat: point.fat,
            ))
        .toList(),
    protein: nutritionSeries
        .map((point) => DailyNutritionPoint(
              date: point.date,
              calories: point.calories,
              protein: round1(point.protein),
              carbs: point.carbs,
              fat: point.fat,
            ))
        .toList(),
    carbs: nutritionSeries
        .map((point) => DailyNutritionPoint(
              date: point.date,
              calories: point.calories,
              protein: point.protein,
              carbs: round1(point.carbs),
              fat: point.fat,
            ))
        .toList(),
    fat: nutritionSeries
        .map((point) => DailyNutritionPoint(
              date: point.date,
              calories: point.calories,
              protein: point.protein,
              carbs: point.carbs,
              fat: round1(point.fat),
            ))
        .toList(),
    weights: weightSeries,
    bodyFat: bodyFatSeries,
    leanMass: leanMassSeries,
    caloriesAvg: averageOf(caloriesPoints.map((p) => p.calories)),
    caloriesMaxDay: maxBy(caloriesPoints, (p) => p.calories),
    proteinAvg: averageOf(proteinPoints.map((p) => p.protein)),
    proteinMaxDay: maxBy(proteinPoints, (p) => p.protein),
    carbsAvg: averageOf(carbsPoints.map((p) => p.carbs)),
    carbsMaxDay: maxBy(carbsPoints, (p) => p.carbs),
    fatAvg: averageOf(fatPoints.map((p) => p.fat)),
    fatMaxDay: maxBy(fatPoints, (p) => p.fat),
    weight: weightSummary,
    weeklyWeightAvg: weeklyWeight,
  );
}

double? averageOf(Iterable<double> values) {
  final list = values.toList();
  if (list.isEmpty) return null;
  return (list.reduce((a, b) => a + b) / list.length).roundToDouble();
}

DailyNutritionPoint? maxBy(List<DailyNutritionPoint> items, double Function(DailyNutritionPoint) pick) {
  DailyNutritionPoint? best;
  for (final item in items) {
    if (best == null || pick(item) > pick(best)) best = item;
  }
  return best;
}