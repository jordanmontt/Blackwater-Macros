import 'package:blackwater_macros/core/domain/models.dart';
import 'package:blackwater_macros/core/domain/stats.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('tendencia de peso (media móvil de 7 días)', () {
    test('suaviza el ruido diario mostrando la media de la última semana', () {
      final points = days('2026-03-01', [80, 81, 80.5, 80, 79.5, 79, 78]);
      final result = movingAverageByDays(points, 7);
      expect(result.last, closeTo(79.714, 3));
    });

    test('no se rompe cuando faltan días: usa solo los registros disponibles', () {
      const points = [
        DataPoint(date: '2026-03-02', value: 80),
        DataPoint(date: '2026-03-06', value: 79),
      ];
      final result = movingAverageByDays(points, 7);
      expect(result, [80, (80 + 79) / 2]);
    });

    test('excluye los puntos que quedan fuera de la ventana', () {
      const points = [
        DataPoint(date: '2026-03-01', value: 90), // fuera de la ventana del día 8
        DataPoint(date: '2026-03-08', value: 80),
      ];
      final result = movingAverageByDays(points, 7);
      expect(result[1], 80);
    });

    test('devuelve una lista vacía si no hay datos', () {
      expect(movingAverageByDays(const [], 7), isEmpty);
    });
  });

  group('ritmo de cambio (kg/semana)', () {
    test('detecta una pérdida constante de 0,5 kg por semana', () {
      final points = [
        for (var week = 0; week < 5; week++)
          DataPoint(
            date: addDays('2026-01-05', week * 7),
            value: 85 - week * 0.5,
          ),
      ];
      expect(linearRatePerWeek(points), closeTo(-0.5, 6));
    });

    test('es insensible al orden temporal de los registros (regresión, no diferencia)', () {
      const points = [
        DataPoint(date: '2026-01-12', value: 84.5),
        DataPoint(date: '2026-01-05', value: 85),
        DataPoint(date: '2026-01-19', value: 84),
      ];
      expect(linearRatePerWeek(points), closeTo(-0.5, 6));
    });

    test('devuelve null con menos de dos fechas distintas', () {
      expect(linearSlopePerDay(const [DataPoint(date: '2026-01-05', value: 80)]), isNull);
      expect(linearSlopePerDay(const []), isNull);
    });

    test('devuelve 0 cuando el peso se mantiene estable', () {
      const points = [
        DataPoint(date: '2026-01-05', value: 80),
        DataPoint(date: '2026-01-19', value: 80),
      ];
      expect(linearSlopePerDay(points), 0);
    });
  });

  group('medias semanales del peso', () {
    test('agrupa por semanas que empiezan en lunes aunque el mes cambie', () {
      const points = [
        DataPoint(date: '2026-02-28', value: 82), // sábado
        DataPoint(date: '2026-03-01', value: 81.4), // domingo
        DataPoint(date: '2026-03-02', value: 81), // lunes
        DataPoint(date: '2026-03-03', value: 80.8), // martes
      ];
      final weeks = weeklyAverages(points);
      expect(weeks, hasLength(2));
      expect(weeks[0].weekStart, '2026-02-23');
      expect(weeks[0].avg, closeTo((82 + 81.4) / 2, 6));
      expect(weeks[1].weekStart, '2026-03-02');
      expect(weeks[1].avg, closeTo((81 + 80.8) / 2, 6));
    });

    test('ignora las semanas sin datos en lugar de rellenarlas', () {
      const points = [
        DataPoint(date: '2026-03-02', value: 80),
        DataPoint(date: '2026-03-16', value: 79), // dos semanas después
      ];
      expect(
        weeklyAverages(points).map((week) => week.weekStart).toList(),
        ['2026-03-02', '2026-03-16'],
      );
    });
  });

  group('serie diaria de calorías y proteína', () {
    test('rellena con ceros los días sin comidas para mostrar huecos honestos', () {
      final totals = <String, DailyNutritionPoint>{
        '2026-03-01': const DailyNutritionPoint(date: '2026-03-01', calories: 2000, protein: 120, carbs: 250, fat: 70),
        '2026-03-03': const DailyNutritionPoint(date: '2026-03-03', calories: 1800, protein: 100, carbs: 200, fat: 60),
      };
      final series = buildDailyNutritionSeries(totals, '2026-03-01', '2026-03-04');
      expect(series.map((point) => point.calories).toList(), [2000, 0, 1800, 0]);
      expect(series.map((point) => point.protein).toList(), [120, 0, 100, 0]);
      expect(series.map((point) => point.carbs).toList(), [250, 0, 200, 0]);
      expect(series.map((point) => point.fat).toList(), [70, 0, 60, 0]);
    });

    test('cubre rangos de un único día', () {
      final series = buildDailyNutritionSeries(<String, DailyNutritionPoint>{}, '2026-03-01', '2026-03-01');
      final point = series.single;
      expect(point.date, '2026-03-01');
      expect(point.calories, 0);
      expect(point.protein, 0);
    });
  });

  group('rangos de estadísticas', () {
    test("convierte los nombres de rango en número de días; 'todo' no limita", () {
      expect(rangeToDays(StatsRange.d7), 7);
      expect(rangeToDays(StatsRange.d30), 30);
      expect(rangeToDays(StatsRange.d90), 90);
      expect(rangeToDays(StatsRange.all), isNull);
    });
  });
}

List<DataPoint> days(String startKey, List<double> values) =>
    List.generate(values.length, (index) => DataPoint(date: addDays(startKey, index), value: values[index]));

String addDays(String key, int amount) {
  final date = DateTime.parse('${key}T00:00:00').add(Duration(days: amount));
  String pad(int n) => n.toString().padLeft(2, '0');
  return '${date.year}-${pad(date.month)}-${pad(date.day)}';
}