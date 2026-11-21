package com.postpole.wheelfile.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.domain.report.MonthlyCost
import java.time.format.TextStyle
import java.util.Locale

/** One point of a line chart: x is any increasing number (epoch millis), y the value. */
data class ChartPoint(val x: Long, val y: Double)

private val ChartHeight = 132.dp

/**
 * A line chart drawn on a Canvas (no chart library). [summary] is one sentence describing it; it is the TalkBack
 * description of the whole chart, so the Canvas itself is a single image node.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    color: Color,
    summary: String,
    topLabel: String,
    bottomLabel: String,
    modifier: Modifier = Modifier,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier.fillMaxWidth().height(ChartHeight).semantics {
            contentDescription = summary
            role = Role.Image
        },
    ) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 8.dp)) {
            val strokeWidth = 2.5.dp.toPx()
            repeat(3) { i ->
                val y = size.height * i / 2f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            if (points.isEmpty()) return@Canvas
            val minX = points.first().x
            val spanX = (points.last().x - minX).coerceAtLeast(1L).toFloat()
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            val spanY = (maxY - minY).takeIf { it > 0.0 } ?: 1.0
            fun at(p: ChartPoint): Offset {
                val x = if (points.size == 1) size.width / 2f else (p.x - minX) / spanX * size.width
                val y = if (maxY == minY) size.height / 2f else size.height - ((p.y - minY) / spanY).toFloat() * size.height
                return Offset(x, y)
            }
            if (points.size > 1) {
                val path = Path()
                points.forEachIndexed { i, p -> at(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            points.forEach { drawCircle(color, radius = 3.5.dp.toPx(), center = at(it)) }
        }
        Text(
            topLabel,
            Modifier.align(Alignment.TopStart),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            bottomLabel,
            Modifier.align(Alignment.BottomStart),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Colors of the three stacked layers: fuel and charging, service, expenses (design: primary, tertiary, secondary). */
data class CostColors(val fuel: Color, val service: Color, val expenses: Color)

/** Monthly cost as stacked bars, one bar per calendar month. [summary] is the TalkBack description. */
@Composable
fun StackedBarChart(
    months: List<MonthlyCost>,
    colors: CostColors,
    summary: String,
    topLabel: String,
    modifier: Modifier = Modifier,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val locale = Locale.getDefault()
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(ChartHeight).semantics {
                contentDescription = summary
                role = Role.Image
            },
        ) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight().padding(top = 8.dp)) {
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                if (months.isEmpty()) return@Canvas
                val gap = 4.dp.toPx()
                val barWidth = ((size.width - gap * (months.size - 1)) / months.size).coerceAtLeast(1f)
                val max = months.maxOf { it.totalMinor }.coerceAtLeast(1L).toFloat()
                months.forEachIndexed { i, m ->
                    val x = i * (barWidth + gap)
                    var bottom = size.height
                    listOf(m.fuelMinor to colors.fuel, m.serviceMinor to colors.service, m.expenseMinor to colors.expenses).forEach { (value, color) ->
                        if (value > 0L) {
                            val h = value / max * size.height
                            drawRect(color, Offset(x, bottom - h), Size(barWidth, h))
                            bottom -= h
                        }
                    }
                }
            }
            Text(
                topLabel,
                Modifier.align(Alignment.TopStart),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (months.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (months.size <= 12) {
                    months.forEach {
                        Text(
                            it.month.month.getDisplayName(TextStyle.NARROW, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(shortMonth(months.first(), locale), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(shortMonth(months.last(), locale), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Mar 2026" in the device language. */
fun shortMonth(m: MonthlyCost, locale: Locale): String =
    m.month.month.getDisplayName(TextStyle.SHORT, locale) + " " + m.month.year

/** A small colored square with a label, for chart legends. */
@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A horizontal bar for the Compare range: [fraction] of the widest bar in its currency group. */
@Composable
fun CompareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(6.dp)))
    }
}
