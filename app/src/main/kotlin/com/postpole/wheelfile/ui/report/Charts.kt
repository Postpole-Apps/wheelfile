package com.postpole.wheelfile.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.domain.report.EconomyPoint
import com.postpole.wheelfile.domain.report.MonthlyCost
import com.postpole.wheelfile.ui.common.formatNumber
import com.postpole.wheelfile.ui.theme.LocalWheelfileType
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val ChartHeight = 160.dp

/** A titled card around one chart. [summary] is the one-sentence text that TalkBack reads instead of the drawing. */
@Composable
fun ChartCard(title: String, summary: String, tag: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            // The chart is one element for accessibility: its summary replaces every drawn part.
            Column(
                Modifier.clearAndSetSemantics {
                    contentDescription = summary
                    testTag = tag
                },
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) { content() }
            // The same sentence, shown for everyone; TalkBack already reads it as the chart's description above.
            Text(
                summary,
                Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A line over time with its lowest and highest value and first and last date labelled. */
@Composable
fun LineChart(points: List<EconomyPoint>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val numbers = LocalWheelfileType.current
    val low = points.minOf { it.value }
    val high = points.maxOf { it.value }
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0
    val first = points.first().dateTime
    val last = points.last().dateTime
    val timeSpan = (last - first).takeIf { it > 0L } ?: 1L
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatNumber(high, 2), style = numbers.numberMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(ChartHeight)) {
            val stroke = 2.dp.toPx()
            val pad = 6.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2
            drawLine(outline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            fun x(t: Long) = if (points.size == 1) size.width / 2 else pad + w * ((t - first).toFloat() / timeSpan)
            fun y(v: Double) = if (high == low) size.height / 2 else pad + h * (1f - ((v - low) / span).toFloat())
            val path = Path()
            points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.dateTime), y(p.value)) else path.lineTo(x(p.dateTime), y(p.value)) }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEach { drawCircle(color, radius = 3.dp.toPx(), center = Offset(x(it.dateTime), y(it.value))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatNumber(low, 2), style = numbers.numberMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shortDate(first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(shortDate(last), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Series colours for the stacked bars, shown again in the legend. */
data class BarColors(val energy: Color, val service: Color, val expenses: Color)

/** One stacked bar per month: energy at the bottom, then service, then expenses. */
@Composable
fun StackedBarChart(months: List<MonthlyCost>, colors: BarColors, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val max = months.maxOf { it.totalMinor }.coerceAtLeast(1L)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Canvas(Modifier.fillMaxWidth().height(ChartHeight)) {
            val gap = 4.dp.toPx()
            val barWidth = ((size.width - gap * (months.size - 1)) / months.size).coerceAtLeast(2f)
            months.forEachIndexed { i, m ->
                val left = i * (barWidth + gap)
                var bottom = size.height
                listOf(m.energyMinor to colors.energy, m.serviceMinor to colors.service, m.expenseMinor to colors.expenses).forEach { (value, color) ->
                    if (value > 0) {
                        val height = size.height * (value.toFloat() / max)
                        drawRect(color, topLeft = Offset(left, bottom - height), size = Size(barWidth, height))
                        bottom -= height
                    }
                }
            }
            drawLine(outline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(monthLabel(months.first()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(monthLabel(months.last()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun shortDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))

private fun monthLabel(m: MonthlyCost): String = m.month.format(DateTimeFormatter.ofPattern("MMM yy"))
