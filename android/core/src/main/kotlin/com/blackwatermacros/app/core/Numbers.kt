package com.blackwatermacros.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * Mirrors `src/lib/core/numbers.ts`: how every number is written on screen and
 * in inputs, the same in every language and on web and Android (docs/TECHNICAL.md
 * «Numbers on screen and in inputs»): `,` for decimals, a narrow no-break space
 * between thousands from five digits up («1,6», «2000», «12 345»). The wire
 * format, CSV and AI prompts keep `.`.
 */

/** Narrow no-break space (SI / ISO 80000 thousands separator). */
const val THOUSANDS_SEPARATOR = "\u202F"

/** «1,6» / «12 345,5»: at most [maxDecimals] decimals, trailing zeros dropped. */
fun formatDecimal(value: Double, maxDecimals: Int = 0, grouping: Boolean = true): String {
    val factor = 10.0.pow(maxDecimals)
    // JS Math.round: floor(x + 0.5).
    val rounded = floor(abs(value) * factor + 0.5) / factor
    val fixed = BigDecimal(rounded).setScale(maxDecimals, RoundingMode.HALF_UP).toPlainString()
    val integer = fixed.substringBefore('.')
    val decimals = fixed.substringAfter('.', "").trimEnd('0')
    val digits = if (grouping && integer.length >= 5) groupThousands(integer) else integer
    val sign = if (value < 0 && rounded != 0.0) "-" else ""
    return sign + digits + if (decimals.isNotEmpty()) ",$decimals" else ""
}

private fun groupThousands(integer: String): String = buildString {
    integer.forEachIndexed { i, digit ->
        if (i > 0 && (integer.length - i) % 3 == 0) append(THOUSANDS_SEPARATOR)
        append(digit)
    }
}

/** What the user types in a number field: a `.` becomes `,` at once. */
fun normalizeDecimal(value: String): String = value.replace('.', ',')
