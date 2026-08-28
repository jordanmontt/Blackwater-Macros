import 'package:blackwater_macros/core/domain/csv.dart';
import 'package:blackwater_macros/core/domain/models.dart';
import 'package:blackwater_macros/core/domain/nutrition.dart';
import 'package:blackwater_macros/core/domain/protein.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('calculateProteinRecommendation', () {
    const weight = 80.0;

    test('maintain: 1.2–1.6 g/kg BW', () {
      final rec = calculateProteinRecommendation(weight, Goal.maintain);
      expect(rec.bwRange.min, 96);
      expect(rec.bwRange.max, 128);
      expect(rec.bwPerKg.min, 1.2);
      expect(rec.bwPerKg.max, 1.6);
    });

    test('surplus: 1.6–2.0 g/kg BW', () {
      final rec = calculateProteinRecommendation(weight, Goal.surplus);
      expect(rec.bwRange.min, 128);
      expect(rec.bwRange.max, 160);
      expect(rec.bwPerKg.min, 1.6);
      expect(rec.bwPerKg.max, 2.0);
    });

    test('cut: 1.6–2.2 g/kg BW', () {
      final rec = calculateProteinRecommendation(weight, Goal.cut);
      expect(rec.bwRange.min, 128);
      expect(rec.bwRange.max, 176);
      expect(rec.bwPerKg.min, 1.6);
      expect(rec.bwPerKg.max, 2.2);
    });

    test('redondea al entero más cercano', () {
      final rec = calculateProteinRecommendation(75, Goal.maintain);
      expect(rec.bwRange.min, 90);
      expect(rec.bwRange.max, 120);
    });

    test('devuelve la metadata correctamente', () {
      final rec = calculateProteinRecommendation(82.5, Goal.surplus);
      expect(rec.goal, Goal.surplus);
      expect(rec.bodyWeightKg, 82.5);
    });
  });

  group('resolveMealTotals', () {
    test('total_only usa los valores manuales', () {
      final totals = resolveMealTotals(
        EntryMode.totalOnly,
        const [],
        manualTotalCalories: 500,
        manualTotalProtein: 30,
      );
      expect(totals.calories, 500);
      expect(totals.protein, 30);
    });

    test('per_ingredient suma lo introducido por ingrediente', () {
      final totals = resolveMealTotals(EntryMode.perIngredient, const [
        Ingredient(name: 'Huevos', calories: 144, protein: 12),
        Ingredient(name: 'Pan', calories: 120, protein: 4),
      ]);
      expect(totals.calories, 264);
      expect(totals.protein, 16);
    });

    test('los ingredientes vacíos aportan cero', () {
      final totals = resolveMealTotals(
        EntryMode.perIngredient,
        const [Ingredient(name: 'Sin datos')],
      );
      expect(totals.calories, 0);
      expect(totals.protein, 0);
    });
  });

  group('exportación CSV', () {
    test('escapa comas, comillas y saltos de línea en las notas', () {
      final csv = toCsv([
        ['fecha', 'nota'],
        ['2026-03-01', 'comida "especial", con salto\nde línea'],
      ]);
      expect(csv, contains('"comida ""especial"", con salto\n'));
      expect(csv.startsWith('\uFEFF'), isTrue); // BOM para Excel
    });

    test('convierte números y valores nulos a celdas vacías', () {
      final csv = toCsv([
        [1.5, null],
      ]);
      expect(csv, contains('1.5,'));
    });
  });

  group('redondeo', () {
    test('round1 y round2', () {
      expect(round1(79.71428571428571), 79.7);
      expect(round2(0.8499999999), 0.85);
      expect(round2(81.23456), 81.23);
    });
  });
}