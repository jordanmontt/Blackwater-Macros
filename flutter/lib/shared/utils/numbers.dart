/// Utilidades de números para formularios: entrada con coma decimal
/// (convención española) y visualización en es-ES.
library;

import '../../core/domain/dates.dart';

/// Convierte lo tecleado en un número, aceptando tanto "," como "." como
/// separador decimal.
double? parseDecimal(String value) {
  final cleaned = value.trim().replaceAll(',', '.').replaceAll(' ', '');
  if (cleaned.isEmpty) return null;
  return double.tryParse(cleaned);
}

/// Normaliza un número a texto editable con coma decimal.
String toDecimalInput(Object? value) {
  if (value == null) return '';
  final asString = value.toString();
  if (value is num) return formatNumberEs(value.toDouble(), maxDecimals: 1);
  return asString.replaceAll('.', ',');
}