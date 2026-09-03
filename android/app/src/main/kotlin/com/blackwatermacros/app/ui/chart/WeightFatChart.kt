package com.blackwatermacros.app.ui.chart

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.blackwatermacros.app.ui.WeightFatRow

private val Chart1 = Color(0xFF4A8C5A)
private val Chart2 = Color(0xFFB5605A)
private val Chart3 = Color(0xFFC79A3C)

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

    val weightRows = data.mapIndexedNotNull { i, r -> r.weight?.let { i to it } }
    val pctRows = data.mapIndexedNotNull { i, r -> r.bodyFatPct?.let { i to it } }
    val trendRows = data.mapIndexedNotNull { i, r -> r.weightTrend?.let { i to it } }
    if (weightRows.isEmpty()) return

    val weights = weightRows.map { it.second }
    val weightMin = weights.min()
    val weightMax = weights.max()
    val pctMin = pctRows.minOfOrNull { it.second } ?: 0.0
    val pctMax = pctRows.maxOfOrNull { it.second } ?: 1.0

    Column(modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            LegendDot(Chart1, "Peso")
            Spacer(Modifier.width(10.dp))
            LegendDot(Chart3, "Tendencia")
            Spacer(Modifier.width(10.dp))
            LegendDot(Chart2, "Grasa corporal")
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            val chartWidth = size.width
            val plotHeight = size.height
            val leftAxisWidth = 44.dp.toPx()
            val rightAxisWidth = 40.dp.toPx()
            val plotLeft = leftAxisWidth
            val plotRight = chartWidth - rightAxisWidth
            val plotWidth = plotRight - plotLeft

            fun yWeight(v: Double): Float {
                val t = if (weightMax > weightMin) (v - weightMin) / (weightMax - weightMin) else 0.5
                return (plotHeight - (t * plotHeight).toFloat())
            }

            fun yPct(v: Double): Float {
                val t = if (pctMax > pctMin) (v - pctMin) / (pctMax - pctMin) else 0.5
                return (plotHeight - (t * plotHeight).toFloat())
            }

            fun xIndex(i: Int): Float {
                val n = data.size
                return if (n <= 1) plotLeft + plotWidth / 2f
                else plotLeft + (plotWidth * i / (n - 1)).toFloat()
            }

            val gridRows = 4
            for (r in 0..gridRows) {
                val y = plotHeight * r / gridRows
                drawLine(
                    color = axisColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.dp.toPx(),
                )
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
                            Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(dash))
                        } else {
                            Stroke(width = 2f)
                        },
                    )
                }
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