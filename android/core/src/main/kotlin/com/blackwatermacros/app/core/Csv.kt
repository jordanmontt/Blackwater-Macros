package com.blackwatermacros.app.core

import kotlin.text.Regex

/**
 * Mirrors `src/lib/core/csv.ts`. RFC-4180 CSV serializer with a UTF-8 BOM so
 * Excel opens it correctly. Cells containing commas, quotes or newlines are
 * quoted and escaped.
 */
fun toCsv(rows: List<List<Any?>>): String {
    val escapedRows = rows.map { row -> row.joinToString(",") { escapeCell(it) } }
    return "\uFEFF" + escapedRows.joinToString("\r\n") + "\r\n"
}

private val ESCAPE_RE = Regex("[\",\\n\\r]")

private fun escapeCell(value: Any?): String {
    val text = if (value == null) "" else value.toString()
    if (ESCAPE_RE.containsMatchIn(text)) {
        return "\"${text.replace("\"", "\"\"")}\""
    }
    return text
}