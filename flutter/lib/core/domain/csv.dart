/// Port de `src/lib/csv.ts`: serialización RFC-4180 con BOM UTF-8 para Excel.
library;

String toCsv(List<List<Object?>> rows) {
  final lines = rows.map((row) => row.map(_escapeCell).join(',')).join('\r\n');
  return '\uFEFF$lines\r\n';
}

String _escapeCell(Object? value) {
  final text = value == null ? '' : value.toString();
  if (RegExp(r'[",\n\r]').hasMatch(text)) {
    return '"${text.replaceAll('"', '""')}"';
  }
  return text;
}