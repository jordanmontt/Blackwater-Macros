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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
    /** Target range drawn as a shaded band behind the lines (e.g. the calorie target). */
    band: Pair<Double, Double>? = null,
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
    val vMax = (allValues + trendVals + listOfNotNull(band?.second)).maxOrNull()?.let { it * 1.15 } ?: 1.0

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

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
                            val left = 44.dp.toPx()
                            val plotWidth = size.width - 6.dp.toPx() - left
                            val frac = ((offset.x - left) / plotWidth).coerceIn(0f, 1f)
                            val idx = (frac * (n - 1)).roundToInt()
                            selectedIndex = if (selectedIndex == idx) null else idx
                        }
                    }
                },
        ) {
            val chartWidth = size.width
            val plotHeight = size.height
            val leftAxisWidth = 44.dp.toPx()
            val plotTop = 44.dp.toPx()
            val plotLeft = leftAxisWidth
            val plotRight = chartWidth - 6.dp.toPx()
            val plotWidth = plotRight - plotLeft
            val plotBottom = plotHeight - 24.dp.toPx()

            val fillArgb = labelColor.toArgb()
            val axisPaint = android.graphics.Paint().apply {
                setColor(fillArgb)
                textSize = AxisTextSize.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            val axisTitlePaint = android.graphics.Paint().apply {
                setColor(fillArgb)
                textSize = AxisTextSize.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            fun y(v: Double): Float {
                val t = if (vMax > vMin) (v - vMin) / (vMax - vMin) else 0.5
                return (plotBottom - (t * (plotBottom - plotTop)).toFloat())
            }

            fun x(i: Int): Float {
                val n = points.size
                return if (n <= 1) plotLeft + plotWidth / 2f
                else plotLeft + (plotWidth * i / (n - 1)).toFloat()
            }

            val gridRows = 4
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
                    formatNumber(vMin + (vMax - vMin) * t, 0),
                    leftAxisWidth - 4.dp.toPx(),
                    yy + 5.dp.toPx(),
                    axisPaint,
                )
            }
            drawContext.canvas.nativeCanvas.drawText(
                unit.ifBlank { "" },
                leftAxisWidth - 4.dp.toPx(),
                24.dp.toPx(),
                axisTitlePaint,
            )

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

            if (band != null) {
                val top = y(band.second)
                drawRect(
                    color = bandColor,
                    topLeft = Offset(plotLeft, top),
                    size = androidx.compose.ui.geometry.Size(plotWidth, y(band.first) - top),
                )
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

            if (points.isNotEmpty()) {
                val labels = listOf(0, points.size / 2, points.size - 1).distinct().filter { it in points.indices }
                labels.forEach { i ->
                    drawContext.canvas.nativeCanvas.drawText(
                        formatDateShort(points[i].date),
                        x(i),
                        plotHeight - 2.dp.toPx(),
                        axisPaint.apply { textAlign = android.graphics.Paint.Align.CENTER },
                    )
                }
            }

            selectedIndex?.let { si ->
                val sx = x(si)
                drawLine(
                    color = labelColor.copy(alpha = 0.6f),
                    start = Offset(sx, plotTop),
                    end = Offset(sx, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                val row = points[si]
                val lines = buildList {
                    add(formatDateShort(row.date))
                    add("$valueLabel: ${formatNumber(row.value, 1)} $unit")
                    trendRows.getOrNull(si)?.let { add("$averageLabel: ${formatNumber(it.second, 1)} $unit") }
                }
                val tooltipPaint = android.graphics.Paint().apply {
                    setColor(fillArgb)
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
/** Chart text sizes in sp, so labels follow the phone's font size (they were raw pixels, tiny on dense screens). */
internal val AxisTextSize = 12.sp
internal val TooltipTextSize = 13.sp
