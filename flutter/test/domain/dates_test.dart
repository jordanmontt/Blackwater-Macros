import 'package:blackwater_macros/core/domain/dates.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('claves de fecha (YYYY-MM-DD en hora local)', () {
    test('convierte fechas locales a clave sin desplazamientos de zona horaria', () {
      expect(toDateKey(DateTime(2026, 3, 9)), '2026-03-09');
      expect(toDateKey(DateTime(2026, 12, 31)), '2026-12-31');
    });

    test('valida claves reales y rechaza las imposibles', () {
      expect(isValidDateKey('2026-02-28'), isTrue);
      expect(isValidDateKey('2026-13-01'), isFalse);
      expect(isValidDateKey('2026-02-30'), isFalse);
      expect(isValidDateKey('09-03-2026'), isFalse);
      expect(isValidDateKey('no es fecha'), isFalse);
    });

    test('suma y resta días cruzando meses y años', () {
      expect(addDaysToKey('2026-03-01', -1), '2026-02-28');
      expect(addDaysToKey('2026-12-31', 1), '2027-01-01');
      expect(addDaysToKey('2026-05-15', 0), '2026-05-15');
    });

    test('calcula la distancia en días entre dos claves', () {
      expect(daysBetweenKeys('2026-03-01', '2026-03-08'), 7);
      expect(daysBetweenKeys('2026-03-08', '2026-03-01'), -7);
    });

    test('"hoy" devuelve una clave válida para el día actual', () {
      expect(isValidDateKey(todayKey()), isTrue);
    });
  });

  group('selector de fecha y hora (datetime-local)', () {
    test('parsea el valor del input como hora local, no como UTC', () {
      final parsed = parseLocalDateTime('2026-07-16T08:30');
      expect(parsed, isNotNull);
      expect(parsed!.year, 2026);
      expect(parsed.hour, 8);
    });

    test('rechaza valores malformados', () {
      expect(parseLocalDateTime('ayer por la mañana'), isNull);
      expect(parseLocalDateTime('2026-07-16'), isNull);
    });

    test('autocompleta con la hora actual al pulsar "Ahora"', () {
      final value = nowDateTimeLocalValue();
      expect(RegExp(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$').hasMatch(value), isTrue);
      final parsed = parseLocalDateTime(value)!;
      expect(DateTime.now().difference(parsed).inSeconds.abs(), lessThan(60));
    });

    test('el viaje de ida y vuelta fecha → input → fecha mantiene la misma hora local', () {
      final original = DateTime(2026, 7, 16, 8, 45);
      final roundTrip = parseLocalDateTime(toDateTimeLocalValue(original));
      expect(roundTrip, isNotNull);
      expect(roundTrip!.millisecondsSinceEpoch, original.millisecondsSinceEpoch);
    });
  });

  group('formato de fechas en español', () {
    test('muestra la fecha larga capitalizada para el encabezado del día', () {
      final formatted = formatDateKeyLong('2026-03-09'); // lunes
      expect(formatted.toLowerCase(), contains('lunes'));
      expect(formatted[0], formatted[0].toUpperCase());
    });

    test('formato es-ES con comas decimales y separador de miles', () {
      expect(formatNumberEs(1234.5678, maxDecimals: 1), '1.234,6');
      expect(formatNumberEs(2.5, maxDecimals: 1), '2,5');
      expect(formatNumberEs(81.2, maxDecimals: 1), '81,2');
    });
  });
}