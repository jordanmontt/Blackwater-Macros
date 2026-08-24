/**
 * Serializes rows of cells into RFC-4180 CSV text with a UTF-8 BOM so Excel
 * opens it correctly. Cells containing commas, quotes or newlines are quoted
 * and escaped.
 */
export function toCsv(rows: (string | number | null | undefined)[][]): string {
  const escapedRows = rows.map((row) => row.map(escapeCell).join(","));
  return "\uFEFF" + escapedRows.join("\r\n") + "\r\n";
}

function escapeCell(value: string | number | null | undefined): string {
  const text = value === null || value === undefined ? "" : String(value);
  if (/[",\n\r]/.test(text)) {
    return `"${text.replaceAll('"', '""')}"`;
  }
  return text;
}
