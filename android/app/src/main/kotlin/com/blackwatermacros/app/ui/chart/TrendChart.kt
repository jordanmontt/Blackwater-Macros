package com.blackwatermacros.app.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackwatermacros.app.core.DataPoint
import com.blackwatermacros.app.core.formatNumberEs
import com.blackwatermacros.app.core.movingAverageByDays
import com.blackwatermacros.app.core.round1

/**
 * Generic line chart matching the web stats `TrendChart`:
 * solid value line + dashed 7-day moving-average trend, optional peak dot.
 */
@Composable
fun TrendChart(
    points: List<DataPoint>,
    color: Color,
    trendColor: Color,
    unit: String,
    peak: DataPoint?,
    modifier: Modifier = Modifier,
) {
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.background

    val trend = movingAverageByDays(points, 7)
    val trendRows = points.mapIndexedNotNull { i, p ->
        trend[i]?.let { i to round1(it) }
    }

    if (points.isEmpty()) return

    val allValues = points.map { it.value }
    val vMin = 0.0
    val vMax = (allValues.maxOrNull() ?: 1.0) * 1.15

    Box(modifier, contentAlignment = Alignment.TopEnd) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            val chartWidth = size.width
            val plotHeight = size.height
            val leftAxisWidth = 44.dp.toPx()
            val plotLeft = leftAxisWidth
            val plotRight = chartWidth - 6.dp.toPx()
            val plotWidth = plotRight - plotLeft

            fun y(v: Double): Float {
                val t = if (vMax > vMin) (v - vMin) / (vMax - vMin) else 0.5
                return (plotHeight * (1f - t.toFloat()))
            }

            fun x(i: Int): Float {
                val n = points.size
                return if (n <= 1) plotLeft + plotWidth / 2f
                else plotLeft + (plotWidth * i / (n - 1)).toFloat()
            }

            val gridRows = 4
            for (r in 0..gridRows) {
                val yy = plotHeight * r / gridRows
                drawLine(
                    color = axisColor,
                    start = Offset(plotLeft, yy),
                    end = Offset(plotRight, yy),
                    strokeWidth = 1.dp.toPx(),
                )
            }

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
        }
    }
}