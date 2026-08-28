/// Port de `src/lib/dates.ts`: claves de fecha en hora local (YYYY-MM-DD),
/// formateo en español y utilidades del selector datetime-local.
library;

final RegExp _dateKeyRe = RegExp(r'^\d{4}-\d{2}-\d{2}$');

bool isValidDateKey(String value) {
  if (!_dateKeyRe.hasMatch(value)) return false;
  final DateTime parsed;
  try {
    parsed = DateTime.parse('${value}T00:00:00');
  } on FormatException {
    return false;
  }
  return !parsed.isUtc && toDateKey(parsed) == value;
}

String toDateKey(DateTime date) {
  final y = date.year.toString().padLeft(4, '0');
  final m = date.month.toString().padLeft(2, '0');
  final d = date.day.toString().padLeft(2, '0');
  return '$y-$m-$d';
}

String todayKey() => toDateKey(DateTime.now());

String addDaysToKey(String key, int days) {
  final date = DateTime.parse('${key}T00:00:00').add(Duration(days: days));
  return toDateKey(date);
}

int daysBetweenKeys(String from, String to) {
  final a = DateTime.parse('${from}T00:00:00');
  final b = DateTime.parse('${to}T00:00:00');
  return (b.difference(a).inSeconds / 86400).round();
}

/// Parsea un valor `datetime-local` ("YYYY-MM-DDTHH:mm") como hora local.
DateTime? parseLocalDateTime(String value) {
  if (!RegExp(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}').hasMatch(value)) return null;
  final parsed = DateTime.tryParse(value);
  return parsed == null || parsed.isUtc ? null : parsed;
}

/// Valor para un input `datetime-local` a partir de una fecha local.
String toDateTimeLocalValue(DateTime date) {
  String pad(int n) => n.toString().padLeft(2, '0');
  return '${date.year}-${pad(date.month)}-${pad(date.day)}T'
      '${pad(date.hour)}:${pad(date.minute)}';
}

String nowDateTimeLocalValue() => toDateTimeLocalValue(DateTime.now());

String _capitalize(String value) => value.isEmpty ? value : value[0].toUpperCase() + value.substring(1);

String _dayNameEs(DateTime date) {
  const names = [
    'lunes',
    'martes',
    'miércoles',
    'jueves',
    'viernes',
    'sábado',
    'domingo',
  ];
  return names[date.weekday - 1];
}

String _monthNameEs(int month) {
  const names = [
    'enero',
    'febrero',
    'marzo',
    'abril',
    'mayo',
    'junio',
    'julio',
    'agosto',
    'septiembre',
    'octubre',
    'noviembre',
    'diciembre',
  ];
  return names[month - 1];
}

/// "lunes, 9 de marzo" (capitalizado).
String formatDateKeyLong(String key) {
  final date = DateTime.parse('${key}T00:00:00');
  return _capitalize('${_dayNameEs(date)}, ${date.day} de ${_monthNameEs(date.month)}');
}

/// "9 mar." — etiqueta compacta para días.
String formatDateKeyShort(String key) {
  final date = DateTime.parse('${key}T00:00:00');
  const months = [
    'ene.',
    'feb.',
    'mar.',
    'abr.',
    'may.',
    'jun.',
    'jul.',
    'ago.',
    'sept.',
    'oct.',
    'nov.',
    'dic.',
  ];
  return '${date.day} ${months[date.month - 1]}';
}

/// "HH:mm" en hora local de un instante ISO.
String formatTime(String iso) {
  final date = DateTime.parse(iso).toLocal();
  return '${date.hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')}';
}

/// Formato es-ES: separador de miles y comas decimales (0,85 frente a 0.85).
String formatNumberEs(double value, {int maxDecimals = 0}) {
  final formatted = value.toStringAsFixed(maxDecimals);
  final parts = formatted.split('.');
  final intPart = parts[0];
  final negative = intPart.startsWith('-');
  final digits = negative ? intPart.substring(1) : intPart;
  final buffer = StringBuffer();
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) buffer.write('.');
    buffer.write(digits[i]);
  }
  final result = StringBuffer();
  if (negative) result.write('-');
  result.write(buffer.toString());
  if (parts.length == 2 && maxDecimals > 0) {
    result.write(',');
    result.write(parts[1]);
  }
  return result.toString();
}