package com.blackwatermacros.app.ui.chart

import com.blackwatermacros.app.R
import androidx.compose.ui.res.stringResource
import com.blackwatermacros.app.ui.formatDateShort
import com.blackwatermacros.app.ui.formatNumber
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.ui.ChartClay
import com.blackwatermacros.app.ui.ChartBlue
import com.blackwatermacros.app.ui.WeightFatRow
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val Chart2 = ChartClay

/** Nice round step covering [min, max] with roughly [targetTicks] divisions. */
internal fun niceTickStep(min: Double, max: Double, targetTicks: Int): Double {
    if (max <= min) return 1.0
    val span = max - min
    val rawStep = span / targetTicks
    val mag = 10.0.pow(floor(log10(rawStep)))
    val norm = rawStep / mag
    val nice = when {
        norm < 1.5 -> 1.0
        norm < 3.0 -> 2.0
        norm < 7.0 -> 5.0
        else -> 10.0
    }
    return nice * mag
}

/** Round tick values from [min] to [max] stepping by [step], starting at a multiple of [step]. */
internal fun niceTicks(min: Double, max: Double, step: Double): List<Double> {
    val start = ceil(min / step) * step
    val result = mutableListOf<Double>()
    var v = start
    var guard = 0
    while (v <= max + step / 2 && guard < 64) {
        result.add(v)
        v += step
        guard++
    }
    return result
}

/** Whole-number-ish label in the app's number format: «72», «72,5». */
private fun axisLabel(v: Double): String = formatNumber(v, 1)

/** Which measurement the weight chart draws: one axis at a time (web `WeightChartSeries`). */
enum class WeightChartSeries { WEIGHT, BODY_FAT }

/**
 * Progreso's weight chart, like the web `WeightFatChart`. [WeightChartSeries.WEIGHT]:
 * the 7-day trend, the line that says where the weight is going, bold; each weigh-in
 * a light dot with no line between them (day-to-day water and food make it zig-zag).
 * [WeightChartSeries.BODY_FAT]: the body fat measurements on their own axis. One axis
 * at a time: kg and % on two axes with overlapping ranges were hard to read.
 */
@Composable
fun WeightFatChart(
    data: List<WeightFatRow>,
    modifier: Modifier = Modifier,
    series: WeightChartSeries = WeightChartSeries.WEIGHT,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val weighInColor = labelColor.copy(alpha = 0.7f)
    val weightLabel = stringResource(R.string.chart_weigh_ins)
    val trendLabel = stringResource(R.string.chart_trend)
    val bodyFatLabel = stringResource(R.string.chart_body_fat)
    val isWeight = series == WeightChartSeries.WEIGHT
    // «Label: value» in the language's own punctuation, for the tooltip.
    val labelValue = stringResource(R.string.label_value)
    val axisTitle = stringResource(if (isWeight) R.string.chart_weight_axis else R.string.chart_fat_axis)

    // The points drawn as dots, and the line (the trend for weight; the measurements themselves for fat).
    val dots = if (isWeight) data.mapIndexed { i, r -> i to r.weight } else data.mapIndexedNotNull { i, r -> r.bodyFatPct?.let { i to it } }
    val line = if (isWeight) data.mapIndexedNotNull { i, r -> r.weightTrend?.let { i to it } } else dots
    if (dots.isEmpty()) return
    val values = dots.map { it.second } + line.map { it.second }
    val minRaw = values.min()
    val maxRaw = values.max()
    val pad = if (maxRaw > minRaw) (maxRaw - minRaw) * 0.12 else 1.0
    val vMin = minRaw - pad
    val vMax = maxRaw + pad

    Column(modifier) {
        var selectedIndex by remember { mutableStateOf<Int?>(null) }
        // Plot edges from the last draw (they follow the font size), for mapping taps.
        val plotEdges = remember { FloatArray(2) }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(data, series) {
                    detectTapGestures { offset ->
                        val n = data.size
                        if (n > 0) {
                            val (plotLeftPx, plotRightPx) = plotEdges
                            val plotWidthPx = plotRightPx - plotLeftPx
                            val frac = ((offset.x - plotLeftPx) / plotWidthPx).coerceIn(0f, 1f)
                            val idx = (frac * (n - 1)).roundToInt()
                            selectedIndex = if (selectedIndex == idx) null else idx
                        }
                    }
                },
        ) {
            val chartWidth = size.width
            val plotHeight = size.height
            val plotTop = 48.dp.toPx()

            val fillArgb = labelColor.toArgb()
            val axisPaint = android.graphics.Paint().apply {
                color = fillArgb
                textSize = AxisTextSize.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            val axisTitlePaint = android.graphics.Paint().apply {
                color = fillArgb
                textSize = AxisTextSize.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
            }
            val step = niceTickStep(vMin, vMax, 4)
            val ticks = niceTicks(vMin, vMax, step)
            val leftAxisWidth = axisRoom(axisPaint, ticks.map(::axisLabel), 44.dp.toPx(), 8.dp.toPx())
            val plotLeft = leftAxisWidth
            val plotRight = chartWidth - 8.dp.toPx()
            val plotWidth = plotRight - plotLeft
            val plotBottom = plotHeight - 24.dp.toPx()
            plotEdges[0] = plotLeft
            plotEdges[1] = plotRight

            fun y(v: Double): Float {
                val t = if (vMax > vMin) (v - vMin) / (vMax - vMin) else 0.5
                return (plotBottom - (t * (plotBottom - plotTop)).toFloat())
            }

            fun xIndex(i: Int): Float {
                val n = data.size
                return if (n <= 1) plotLeft + plotWidth / 2f
                else plotLeft + (plotWidth * i / (n - 1)).toFloat()
            }

            ticks.forEach { v ->
                val ty = y(v)
                drawLine(color = axisColor, start = Offset(plotLeft, ty), end = Offset(plotRight, ty), strokeWidth = 1.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(axisLabel(v), leftAxisWidth - 4.dp.toPx(), ty + 5.dp.toPx(), axisPaint)
            }
            // From the left edge, so a long title or a large font never runs off the card.
            drawContext.canvas.nativeCanvas.drawText(axisTitle, 0f, 24.dp.toPx(), axisTitlePaint)

            if (line.isNotEmpty()) {
                val path = Path()
                line.forEachIndexed { idx, (i, v) -> if (idx == 0) path.moveTo(xIndex(i), y(v)) else path.lineTo(xIndex(i), y(v)) }
                drawPath(
                    path = path,
                    color = if (isWeight) ChartBlue else Chart2,
                    style = Stroke(width = (if (isWeight) 3 else 2).dp.toPx()),
                )
            }
            dots.forEach { (i, v) ->
                drawCircle(
                    color = if (isWeight) weighInColor else Chart2,
                    radius = 2.5.dp.toPx(),
                    center = Offset(xIndex(i), y(v)),
                )
            }
            drawDateLabels(data.map { it.date }, ::xIndex, axisPaint, chartWidth, plotHeight)

            selectedIndex?.let { si ->
                val row = data[si]
                val lines = buildList {
                    add(formatDateShort(row.date))
                    if (isWeight) {
                        add(labelValue.format(weightLabel, "${formatNumber(row.weight, 1)} kg"))
                        row.weightTrend?.let { add(labelValue.format(trendLabel, "${formatNumber(it, 1)} kg")) }
                    } else {
                        row.bodyFatPct?.let { add(labelValue.format(bodyFatLabel, "${formatNumber(it, 1)}%")) }
                    }
                }
                drawSelection(xIndex(si), plotTop, plotBottom, chartWidth, lines, labelColor, surfaceColor)
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row {
                if (isWeight) {
                    LegendDot(ChartBlue, trendLabel)
                    Spacer(Modifier.width(12.dp))
                    LegendDot(weighInColor, weightLabel)
                } else {
                    LegendDot(Chart2, bodyFatLabel)
                }
            }
        }
    }
}
