import 'package:blackwater_macros/core/domain/models.dart';
import 'package:blackwater_macros/core/domain/stats_builder.dart';
import 'package:flutter_test/flutter_test.dart';

const String today = '2026-08-23';

List<StatsMeal> meals(List<({String date, double kcal, double protein})> points) => points
    .map((point) => StatsMeal(
          logDate: point.date,
          resolvedCalories: point.kcal,
          resolvedProtein: point.protein,
        ))
    .toList();

List<StatsWeight> weights(List<({String iso, double kg, double? bodyFatPct})> entries) => entries
    .map((entry) => StatsWeight(
          measuredAt: DateTime.parse(entry.iso),
          weightKg: entry.kg,
          bodyFatPct: entry.bodyFatPct,
        ))
    .toList();

String addDays(String key, int amount) {
  final date = DateTime.parse('${key}T00:00:00').add(Duration(days: amount));
  String pad(int n) => n.toString().padLeft(2, '0');
  return '${date.year}-${pad(date.month)}-${pad(date.day)}';
}

void main() {
  group('buildStatsFromData', () {
    test('suma todas las comidas de un día y rellena con ceros los días vacíos', () {
      final summary = buildStatsFromData(
        meals([
          (date: today, kcal: 475, protein: 32),
          (date: today, kcal: 850, protein: 45),
          (date: today, kcal: 600, protein: 40),
        ]),
        const [],
        StatsRange.d7,
        today,
      );

      expect(summary.calories.firstWhere((point) => point.date == today).calories, 1925);
      expect(summary.calories, hasLength(7));
      final yesterday = addDays(today, -1);
      expect(summary.calories.firstWhere((point) => point.date == yesterday).calories, 0);
    });

    test('calcula media y día pico solo entre días con registros', () {
      final twoDaysAgo = addDays(today, -2);
      final summary = buildStatsFromData(
        meals([
          (date: today, kcal: 1500, protein: 90),
          (date: twoDaysAgo, kcal: 2500, protein: 150),
        ]),
        const [],
        StatsRange.d7,
        today,
      );

      expect(summary.caloriesAvg, 2000);
      expect(summary.caloriesMaxDay?.date, twoDaysAgo);
      expect(summary.proteinAvg, 120);
    });

    test('peso actual igual al último registro y tendencia suavizada presente', () {
      final summary = buildStatsFromData(
        const [],
        weights([
          (iso: '${addDays(today, -6)}T12:00:00Z', kg: 82, bodyFatPct: null),
          (iso: '${addDays(today, -3)}T12:00:00Z', kg: 81.5, bodyFatPct: null),
          (iso: '${today}T12:00:00Z', kg: 81.2, bodyFatPct: null),
        ]),
        StatsRange.d30,
        today,
      );

      expect(summary.weight.currentWeightKg, 81.2);
      expect(summary.weights.last.trend, isNotNull);
    });

    test('un mes perdiendo ~0,5 kg/semana produce ritmo semanal negativo y cambio total', () {
      final entries = [
        for (var week = 0; week < 5; week++)
          (
            iso: '${addDays(addDays(today, -28), week * 7)}T12:00:00Z',
            kg: 85 - week * 0.5,
            bodyFatPct: null,
          ),
      ];
      final summary = buildStatsFromData(const [], weights(entries), StatsRange.d30, today);

      expect(summary.weight.changeSinceStartKg, closeTo(-2, 6));
      expect(summary.weight.ratePerWeekKg, closeTo(-0.5, 1));
    });

    test('construye series de grasa corporal y masa magra solo con datos válidos de grasa', () {
      final summary = buildStatsFromData(
        const [],
        weights([
          (iso: '${addDays(today, -5)}T08:00:00Z', kg: 86, bodyFatPct: 20),
          (iso: '${today}T08:00:00Z', kg: 84, bodyFatPct: 18),
          (iso: '${addDays(today, -2)}T08:00:00Z', kg: 85, bodyFatPct: null),
        ]),
        StatsRange.d30,
        today,
      );

      expect(summary.bodyFat, hasLength(2));
      expect(summary.weight.currentBodyFatPct, 18);
      // masa magra = peso × (1 - grasa/100)
      expect(summary.weight.currentLeanMassKg, closeTo(84 * 0.82, 2));
      expect(summary.leanMass, hasLength(2));
    });

    test('devuelve nulos cuando no hay comidas ni pesos en el rango', () {
      final summary = buildStatsFromData(const [], const [], StatsRange.d7, today);
      expect(summary.caloriesAvg, isNull);
      expect(summary.weight.currentWeightKg, isNull);
      expect(summary.weights, isEmpty);
    });
  });
}