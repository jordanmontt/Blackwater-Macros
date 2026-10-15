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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackwatermacros.app.core.DataPoint
import com.blackwatermacros.app.core.movingAverageByDays
import com.blackwatermacros.app.core.round1
import kotlin.math.roundToInt

/**
 * Generic line chart matching the web stats `TrendChart`:
 * solid value line + dashed 7-day moving-average trend, optional peak dot.
 * Tap anywhere to toggle a tooltip showing the value and trend at that date.
 */
@Composable
fun TrendChart(
    points: List<DataPoint>,
    color: Color,
    trendColor: Color,
    unit: String,
    peak: DataPoint?,
    modifier: Modifier = Modifier,
    /**
     * Target range of each point (aligned with [points]), drawn as a shaded band behind the
     * lines (e.g. the calorie target). One range for all: a flat band; ranges that changed
     * (profile or weight): a band that steps at each change.
     */
    bands: List<Pair<Double, Double>?>? = null,
    bandColor: Color = color.copy(alpha = 0.14f),
) {
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.background
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val valueLabel = stringResource(R.string.chart_value)
    val averageLabel = stringResource(R.string.chart_average)
    val trendLabel = stringResource(R.string.chart_trend)

    val trend = movingAverageByDays(points, 7)
    val trendRows = points.mapIndexedNotNull { i, p ->
        trend[i]?.let { i to round1(it) }
    }

    if (points.isEmpty()) return

    val allValues = points.map { it.value }
    val trendVals = trendRows.map { it.second }
    val vMin = 0.0
    val bandTops = bands.orEmpty().mapNotNull { it?.second }
    val vMax = (allValues + trendVals + bandTops).maxOrNull()?.let { it * 1.15 } ?: 1.0

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    // Plot edges from the last draw (they follow the font size), for mapping taps.
    val plotEdges = remember { FloatArray(2) }

    Column {
        Box(modifier, contentAlignment = androidx.compose.ui.Alignment.TopEnd) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val n = points.size
                        if (n > 0) {
                            val (left, right) = plotEdges
                            val plotWidth = right - left
                            val frac = ((offset.x - left) / plotWidth).coerceIn(0f, 1f)
                            val idx = (frac * (n - 1)).roundToInt()
                            selectedIndex = if (selectedIndex == idx) null else idx
                        }
                    }
                },
        ) {
            val chartWidth = size.width
            val plotHeight = size.height
            val fillArgb = labelColor.toArgb()
            val axisPaint = android.graphics.Paint().apply {
                setColor(fillArgb)
                textSize = AxisTextSize.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            val axisTitlePaint = android.graphics.Paint().apply {
                setColor(fillArgb)
                textSize = AxisTextSize.toPx()
                textAlign = android.graphics.Paint.Align.LEFT
            }
            val gridRows = 4
            val tickLabels = (0..gridRows).map { r -> formatNumber(vMin + (vMax - vMin) * r / gridRows, 0) }
            val leftAxisWidth = axisRoom(axisPaint, tickLabels, 44.dp.toPx(), 8.dp.toPx())
            val plotTop = 44.dp.toPx()
            val plotLeft = leftAxisWidth
            val plotRight = chartWidth - 6.dp.toPx()
            val plotWidth = plotRight - plotLeft
            val plotBottom = plotHeight - 24.dp.toPx()
            plotEdges[0] = plotLeft
            plotEdges[1] = plotRight

            fun y(v: Double): Float {
                val t = if (vMax > vMin) (v - vMin) / (vMax - vMin) else 0.5
                return (plotBottom - (t * (plotBottom - plotTop)).toFloat())
            }

            fun x(i: Int): Float {
                val n = points.size
                return if (n <= 1) plotLeft + plotWidth / 2f
                else plotLeft + (plotWidth * i / (n - 1)).toFloat()
            }

            for (r in 0..gridRows) {
                val t = r.toDouble() / gridRows
                val yy = plotBottom - (t * (plotBottom - plotTop)).toFloat()
                drawLine(
                    color = axisColor,
                    start = Offset(plotLeft, yy),
                    end = Offset(plotRight, yy),
                    strokeWidth = 1.dp.toPx(),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    tickLabels[r],
                    leftAxisWidth - 4.dp.toPx(),
                    yy + 5.dp.toPx(),
                    axisPaint,
                )
            }
            // From the left edge, so a large font never runs off the card.
            drawContext.canvas.nativeCanvas.drawText(unit, 0f, 24.dp.toPx(), axisTitlePaint)

            fun drawPath(rows: List<Pair<Int, Double>>, strokeColor: Color, dash: FloatArray?, width: Float) {
                if (rows.isEmpty()) return
                val path = Path()
                rows.forEachIndexed { idx, (i, v) ->
                    val xx = x(i)
                    val yy = y(v)
                    if (idx == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = if (dash != null) {
                        Stroke(width = width, pathEffect = PathEffect.dashPathEffect(dash))
                    } else {
                        Stroke(width = width)
                    },
                )
            }

            fun drawBand(band: Pair<Double, Double>, from: Float, to: Float) {
                val top = y(band.second)
                drawRect(
                    color = bandColor,
                    topLeft = Offset(from, top),
                    size = androidx.compose.ui.geometry.Size(to - from, y(band.first) - top),
                )
            }
            val known = bands.orEmpty().filterNotNull()
            if (known.isNotEmpty() && known.all { it == known.first() }) {
                drawBand(known.first(), plotLeft, plotRight)
            } else {
                // Each point's range, from halfway to the previous point to halfway to the next.
                bands.orEmpty().forEachIndexed { i, band ->
                    if (band == null || i >= points.size) return@forEachIndexed
                    val from = if (i == 0) plotLeft else (x(i - 1) + x(i)) / 2f
                    val to = if (i == points.size - 1) plotRight else (x(i) + x(i + 1)) / 2f
                    drawBand(band, from, to)
                }
            }

            // Trend line first (behind), then value line.
            drawPath(trendRows, trendColor, floatArrayOf(2f, 2f), 3f)
            drawPath(points.mapIndexed { i, p -> i to p.value }, color, null, 1.5f)

            // Peak dot.
            if (peak != null) {
                val peakIndex = points.indexOfFirst { it.date == peak.date }.takeIf { it >= 0 } ?: return@Canvas
                val peakY = y(peak.value)
                drawCircle(
                    color = ringColor,
                    radius = 5.dp.toPx(),
                    center = Offset(x(peakIndex), peakY),
                )
                drawCircle(
                    color = color,
                    radius = 4.dp.toPx(),
                    center = Offset(x(peakIndex), peakY),
                )
            }

            drawDateLabels(points.map { it.date }, ::x, axisPaint, chartWidth, plotHeight)

            selectedIndex?.let { si ->
                val row = points[si]
                val lines = buildList {
                    add(formatDateShort(row.date))
                    add("$valueLabel: ${formatNumber(row.value, 1)} $unit")
                    trendRows.getOrNull(si)?.let { add("$averageLabel: ${formatNumber(it.second, 1)} $unit") }
                }
                drawSelection(x(si), plotTop, plotBottom, chartWidth, lines, labelColor, surfaceColor)
            }
        }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            LegendDot(color, valueLabel)
            Spacer(Modifier.width(12.dp))
            LegendDot(trendColor, trendLabel)
        }
    }
}

/** A legend entry under a chart: a dot of the series colour and its name. */
@Composable
internal fun LegendDot(color: Color, label: String) {
    Row {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
/** Room beside the plot for tick labels: at least [min], wider when a large font needs it. */
internal fun axisRoom(paint: android.graphics.Paint, labels: List<String>, min: Float, gap: Float): Float =
    maxOf(min, (labels.maxOfOrNull { paint.measureText(it) } ?: 0f) + gap)

/** X for a centred label at [x] that stays inside a chart [width] wide (first and last dates). */
internal fun centredInside(paint: android.graphics.Paint, text: String, x: Float, width: Float): Float {
    val half = paint.measureText(text) / 2
    return if (width > 2 * half) x.coerceIn(half, width - half) else width / 2
}

/** Chart text sizes in sp, so labels follow the phone's font size (they were raw pixels, tiny on dense screens). */
internal val AxisTextSize = 12.sp
internal val TooltipTextSize = 13.sp

/** The first, middle and last dates under the plot ([x] = the x of point i). */
internal fun DrawScope.drawDateLabels(
    dates: List<String>,
    x: (Int) -> Float,
    paint: android.graphics.Paint,
    chartWidth: Float,
    plotHeight: Float,
) {
    if (dates.isEmpty()) return
    paint.textAlign = android.graphics.Paint.Align.CENTER
    listOf(0, dates.size / 2, dates.size - 1).distinct().filter { it in dates.indices }.forEach { i ->
        val text = formatDateShort(dates[i])
        drawContext.canvas.nativeCanvas.drawText(text, centredInside(paint, text, x(i), chartWidth), plotHeight - 2.dp.toPx(), paint)
    }
}

/** The tapped point: a vertical line at [x] and a box with [lines] beside it, kept inside the chart. */
internal fun DrawScope.drawSelection(
    x: Float,
    plotTop: Float,
    plotBottom: Float,
    chartWidth: Float,
    lines: List<String>,
    labelColor: Color,
    surfaceColor: Color,
) {
    drawLine(
        color = labelColor.copy(alpha = 0.6f),
        start = Offset(x, plotTop),
        end = Offset(x, plotBottom),
        strokeWidth = 1.dp.toPx(),
    )
    val tooltipPaint = android.graphics.Paint().apply {
        color = labelColor.toArgb()
        textSize = TooltipTextSize.toPx()
        textAlign = android.graphics.Paint.Align.LEFT
    }
    val lineH = tooltipPaint.textSize * 1.4f
    val pad = 10.dp.toPx()
    val tw = lines.maxOf { tooltipPaint.measureText(it) } + pad * 2
    val th = lineH * lines.size + pad
    val tx = (x + 8.dp.toPx()).coerceIn(4.dp.toPx(), chartWidth - tw - 4.dp.toPx())
    val ty = 8.dp.toPx()
    val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())
    drawRoundRect(color = surfaceColor.copy(alpha = 0.95f), topLeft = Offset(tx, ty), size = Size(tw, th), cornerRadius = corner)
    drawRoundRect(
        color = labelColor.copy(alpha = 0.3f),
        topLeft = Offset(tx, ty),
        size = Size(tw, th),
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx()),
    )
    val baseline = ty + pad + lineH * 0.7f
    lines.forEachIndexed { li, line ->
        drawContext.canvas.nativeCanvas.drawText(line, tx + pad, baseline + li * lineH, tooltipPaint)
    }
}
