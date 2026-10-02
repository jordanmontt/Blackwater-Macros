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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.ui.ChartClay
import com.blackwatermacros.app.ui.ChartForest
import com.blackwatermacros.app.ui.ChartSage
import com.blackwatermacros.app.ui.WeightFatRow
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val Chart1 = ChartForest
private val Chart2 = ChartClay
private val Chart3 = ChartSage

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

/**
 * Dual-Y line chart matching the web `WeightFatChart`:
 * Peso (solid, left Y kg), Tendencia (dashed 6-4, left Y), Grasa (dotted, right Y %).
 */
@Composable
fun WeightFatChart(
    data: List<WeightFatRow>,
    modifier: Modifier = Modifier,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val weightLabel = stringResource(R.string.tab_weight)
    val trendLabel = stringResource(R.string.chart_trend)
    val fatLabel = stringResource(R.string.macro_fat)
    val bodyFatLabel = stringResource(R.string.body_fat)
    val weightAxis = stringResource(R.string.chart_weight_axis)
    val fatAxis = stringResource(R.string.chart_fat_axis)

    val weightRows = data.mapIndexedNotNull { i, r -> r.weight?.let { i to it } }
    val pctRows = data.mapIndexedNotNull { i, r -> r.bodyFatPct?.let { i to it } }
    val trendRows = data.mapIndexedNotNull { i, r -> r.weightTrend?.let { i to it } }
    if (weightRows.isEmpty()) return

    val weights = weightRows.map { it.second }
    val trends = trendRows.map { it.second }
    val weightMinRaw = (weights + trends).min()
    val weightMaxRaw = (weights + trends).max()
    val pctMinRaw = pctRows.minOfOrNull { it.second } ?: 0.0
    val pctMaxRaw = pctRows.maxOfOrNull { it.second } ?: 1.0
    val weightPad = if (weightMaxRaw > weightMinRaw) (weightMaxRaw - weightMinRaw) * 0.12 else 1.0
    val weightMin = weightMinRaw - weightPad
    val weightMax = weightMaxRaw + weightPad
    val pctPad = if (pctMaxRaw > pctMinRaw) (pctMaxRaw - pctMinRaw) * 0.12 else 1.0
    val pctMin = pctMinRaw - pctPad
    val pctMax = pctMaxRaw + pctPad

    Column(modifier) {
        var selectedIndex by remember { mutableStateOf<Int?>(null) }
        // Plot edges from the last draw (they follow the font size), for mapping taps.
        val plotEdges = remember { FloatArray(2) }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(data) {
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
            val weightStep = niceTickStep(weightMin, weightMax, 4)
            val weightTicks = niceTicks(weightMin, weightMax, weightStep)
            val pctStep = niceTickStep(pctMin, pctMax, 4)
            val pctTicks = niceTicks(pctMin, pctMax, pctStep)
            val leftAxisWidth = axisRoom(axisPaint, weightTicks.map(::axisLabel), 44.dp.toPx(), 8.dp.toPx())
            val rightAxisWidth =
                if (pctRows.isNotEmpty()) axisRoom(axisPaint, pctTicks.map(::axisLabel), 40.dp.toPx(), 8.dp.toPx()) else 6.dp.toPx()
            val plotLeft = leftAxisWidth
            val plotRight = chartWidth - rightAxisWidth
            val plotWidth = plotRight - plotLeft
            val plotBottom = plotHeight - 24.dp.toPx()
            plotEdges[0] = plotLeft
            plotEdges[1] = plotRight

            fun yWeight(v: Double): Float {
                val t = if (weightMax > weightMin) (v - weightMin) / (weightMax - weightMin) else 0.5
                return (plotBottom - (t * (plotBottom - plotTop)).toFloat())
            }

            fun yPct(v: Double): Float {
                val t = if (pctMax > pctMin) (v - pctMin) / (pctMax - pctMin) else 0.5
                return (plotBottom - (t * (plotBottom - plotTop)).toFloat())
            }

            fun xIndex(i: Int): Float {
                val n = data.size
                return if (n <= 1) plotLeft + plotWidth / 2f
                else plotLeft + (plotWidth * i / (n - 1)).toFloat()
            }

            fun drawSeries(rows: List<Pair<Int, Double>>, color: Color, dash: FloatArray?, yFn: (Double) -> Float) {
                val path = Path()
                rows.forEachIndexed { idx, (i, v) ->
                    val x = xIndex(i)
                    val y = yFn(v)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (rows.isNotEmpty()) {
                    drawPath(
                        path = path,
                        color = color,
                        style = if (dash != null) {
                            Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(dash))
                        } else {
                            Stroke(width = 2.dp.toPx())
                        },
                    )
                }
            }

            val rightTickPaint = android.graphics.Paint().apply {
                color = fillArgb
                textSize = AxisTextSize.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
            }
            weightTicks.forEach { w ->
                val y = yWeight(w)
                drawLine(
                    color = axisColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.dp.toPx(),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    axisLabel(w),
                    leftAxisWidth - 4.dp.toPx(),
                    y + 5.dp.toPx(),
                    axisPaint,
                )
            }
            if (pctRows.isNotEmpty()) {
                pctTicks.forEach { p ->
                    val y = yPct(p)
                    drawContext.canvas.nativeCanvas.drawText(
                        axisLabel(p),
                        plotRight + 5.dp.toPx(),
                        y + 5.dp.toPx(),
                        rightTickPaint,
                    )
                }
            }
            // From the left edge, so a long title or a large font never runs off the card.
            drawContext.canvas.nativeCanvas.drawText(weightAxis, 0f, 24.dp.toPx(), axisTitlePaint)
            if (pctRows.isNotEmpty()) {
                // Right-aligned to the edge so the title never runs off the card.
                drawContext.canvas.nativeCanvas.drawText(
                    fatAxis,
                    chartWidth,
                    24.dp.toPx(),
                    android.graphics.Paint(rightTickPaint).apply { textAlign = android.graphics.Paint.Align.RIGHT },
                )
            }

            drawSeries(weightRows, Chart1, null, ::yWeight)
            drawSeries(trendRows, Chart3, floatArrayOf(6f, 4f), ::yWeight)
            drawSeries(pctRows, Chart2, floatArrayOf(3f, 3f), ::yPct)

            weightRows.forEach { (i, v) ->
                drawCircle(
                    color = Chart1,
                    radius = 2.dp.toPx(),
                    center = Offset(xIndex(i), yWeight(v)),
                )
            }
            pctRows.forEach { (i, v) ->
                drawCircle(
                    color = Chart2,
                    radius = 2.dp.toPx(),
                    center = Offset(xIndex(i), yPct(v)),
                )
            }
            if (data.isNotEmpty()) {
                val labels = listOf(0, data.size / 2, data.size - 1).distinct().filter { it in data.indices }
                axisPaint.textAlign = android.graphics.Paint.Align.CENTER
                labels.forEach { i ->
                    val text = formatDateShort(data[i].date)
                    drawContext.canvas.nativeCanvas.drawText(
                        text,
                        centredInside(axisPaint, text, xIndex(i), chartWidth),
                        plotHeight - 2.dp.toPx(),
                        axisPaint,
                    )
                }
            }

            selectedIndex?.let { si ->
                val sx = xIndex(si)
                drawLine(
                    color = labelColor.copy(alpha = 0.6f),
                    start = Offset(sx, plotTop),
                    end = Offset(sx, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                val row = data[si]
                val lines = buildList {
                    add(formatDateShort(row.date))
                    row.weight?.let { add("$weightLabel: ${formatNumber(it, 1)} kg") }
                    row.weightTrend?.let { add("$trendLabel: ${formatNumber(it, 1)} kg") }
                    row.bodyFatPct?.let { add("$fatLabel: ${formatNumber(it, 1)}%") }
                }
                val tooltipPaint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = TooltipTextSize.toPx()
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                val lineH = tooltipPaint.textSize * 1.4f
                val pad = 10.dp.toPx()
                val tw = lines.maxOf { tooltipPaint.measureText(it) } + pad * 2
                val th = lineH * lines.size + pad
                val minX = 4.dp.toPx()
                val maxX = chartWidth - tw - 4.dp.toPx()
                val rawTx = sx + 8.dp.toPx()
                val tx = rawTx.coerceIn(minX, maxX)
                val ty = 8.dp.toPx()
                drawRoundRect(
                    color = surfaceColor.copy(alpha = 0.95f),
                    topLeft = Offset(tx, ty),
                    size = androidx.compose.ui.geometry.Size(tw, th),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
                drawRoundRect(
                    color = labelColor.copy(alpha = 0.3f),
                    topLeft = Offset(tx, ty),
                    size = androidx.compose.ui.geometry.Size(tw, th),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
                val baseline = ty + pad + lineH * 0.7f
                lines.forEachIndexed { li, line ->
                    drawContext.canvas.nativeCanvas.drawText(line, tx + pad, baseline + li * lineH, tooltipPaint)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                LegendDot(Chart1, weightLabel)
                Spacer(Modifier.width(10.dp))
                LegendDot(Chart3, trendLabel)
                Spacer(Modifier.width(10.dp))
                LegendDot(Chart2, bodyFatLabel)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}