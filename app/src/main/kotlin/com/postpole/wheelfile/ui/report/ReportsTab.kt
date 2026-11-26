package com.postpole.wheelfile.ui.report

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.R
import com.postpole.wheelfile.ads.ProPromptTrigger
import com.postpole.wheelfile.ui.pro.ProPromptCard
import com.postpole.wheelfile.data.db.EnergyStream
import com.postpole.wheelfile.data.db.Vehicle
import com.postpole.wheelfile.domain.report.CurrencyCosts
import com.postpole.wheelfile.domain.report.EconomySeries
import com.postpole.wheelfile.domain.report.PriceSeries
import com.postpole.wheelfile.domain.report.ReportBuilder
import com.postpole.wheelfile.domain.report.ReportRange
import com.postpole.wheelfile.ui.common.DateField
import com.postpole.wheelfile.ui.common.PriceFormat
import com.postpole.wheelfile.ui.common.formatMoney
import com.postpole.wheelfile.ui.common.formatNumber
import com.postpole.wheelfile.ui.common.label
import com.postpole.wheelfile.ui.theme.LocalWheelfileType
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

/** What the Reports tab shows for one vehicle. Built by the Vehicle detail view model. */
data class ReportsUiState(
    /** The chip the user picked. A Pro range chosen on the free plan shows the locked card and keeps the 12-month charts. */
    val range: ReportRange = ReportRange.LAST_12_MONTHS,
    val isPro: Boolean = false,
    val customFrom: Long? = null,
    val customTo: Long? = null,
    val costs: List<CurrencyCosts> = emptyList(),
    val economy: List<EconomySeries> = emptyList(),
    val prices: List<PriceSeries> = emptyList(),
    val comparisons: List<ReportBuilder.Comparison> = emptyList(),
) {
    val locked: Boolean get() = range.isPro && !isPro
}

/** The `reports_3_months` card to show on the 12-month view, with its price and actions. */
class ReportsProCard(val price: String?, val onCta: () -> Unit, val onClose: () -> Unit)

const val REPORTS_TAB_TAG = "ReportsTab"
const val REPORTS_LOCKED_TAG = "ReportsLockedCard"
const val CHART_ECONOMY_TAG = "ChartEconomy"
const val CHART_COST_TAG = "ChartCost"
const val CHART_PRICE_TAG = "ChartPrice"

/**
 * s13-vehicle-reports and st-pro-upsell: range chips, economy line, monthly cost stacked bars and price per unit. Free
 * shows the last 12 months; the Pro ranges stay visible with a lock and open an inline card, never a full-screen
 * interrupt. The banner is placed by Vehicle detail.
 */
@Composable
fun ReportsTab(
    vehicle: Vehicle,
    state: ReportsUiState,
    onRange: (ReportRange) -> Unit,
    onCustom: (from: Long?, to: Long?) -> Unit,
    onSeePro: () -> Unit,
    modifier: Modifier = Modifier,
    onSharePdf: (() -> Unit)? = null,
    proCard: ReportsProCard? = null,
) {
    Column(modifier.fillMaxSize().testTag(REPORTS_TAB_TAG)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Sizes.screenMargin, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ReportRange.entries.forEach { r ->
                val text = stringResource(r.label())
                val locked = r.isPro && !state.isPro
                val description = if (locked) stringResource(R.string.reports_range_locked, text) else text
                FilterChip(
                    selected = r == state.range,
                    onClick = { onRange(r) },
                    label = { Text(text) },
                    trailingIcon = if (locked) {
                        { Icon(Icons.Default.Lock, contentDescription = null, Modifier.height(18.dp)) }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Sizes.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (state.locked) LockedCard(state.range, onSeePro)
            // After the range chips, before the charts, in the scrolling column so it never sits under the banner.
            // Never together with the locked chart card: the automatic card is for the 12-month view only.
            if (proCard != null && !state.locked && state.range == ReportRange.LAST_12_MONTHS) {
                ProPromptCard(
                    trigger = ProPromptTrigger.REPORTS_3_MONTHS,
                    count = 0,
                    price = proCard.price,
                    onCta = proCard.onCta,
                    onClose = proCard.onClose,
                )
            }
            if (state.range == ReportRange.CUSTOM && state.isPro) CustomRange(state, onCustom)
            if (state.range == ReportRange.COMPARE && state.isPro) CompareCard(state.comparisons)
            if (!state.isPro && !state.locked) {
                Text(stringResource(R.string.reports_free_window), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Charts(vehicle, state)
            if (onSharePdf != null) {
                androidx.compose.material3.OutlinedButton(onClick = onSharePdf, modifier = Modifier.fillMaxWidth()) {
                    if (!state.isPro) {
                        Icon(Icons.Default.Lock, contentDescription = null, Modifier.height(18.dp))
                        Spacer(Modifier.padding(start = Spacing.s))
                    }
                    Text(stringResource(if (state.isPro) R.string.pdf_share else R.string.pdf_share_locked))
                }
            }
            Spacer(Modifier.height(Spacing.l))
        }
    }
}

@Composable
private fun CustomRange(state: ReportsUiState, onCustom: (Long?, Long?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Column(Modifier.weight(1f)) {
            DateField(stringResource(R.string.reports_custom_from), state.customFrom, { onCustom(it, state.customTo) })
        }
        Column(Modifier.weight(1f)) {
            DateField(stringResource(R.string.reports_custom_to), state.customTo, { onCustom(state.customFrom, it) })
        }
    }
}

@androidx.annotation.StringRes
private fun ReportRange.label(): Int = when (this) {
    ReportRange.LAST_12_MONTHS -> R.string.reports_range_12
    ReportRange.ALL_TIME -> R.string.reports_range_all
    ReportRange.COMPARE -> R.string.reports_range_compare
    ReportRange.CUSTOM -> R.string.reports_range_custom
}

/** st-pro-upsell: an inline card where the locked chart would be. The price is never shown here; Play shows it on the paywall. */
@Composable
private fun LockedCard(range: ReportRange, onSeePro: () -> Unit) {
    val title = stringResource(
        when (range) {
            ReportRange.COMPARE -> R.string.reports_locked_title_compare
            ReportRange.CUSTOM -> R.string.reports_locked_title_custom
            else -> R.string.reports_locked_title_all
        },
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().testTag(REPORTS_LOCKED_TAG),
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.reports_locked_body), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onSeePro,
                modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
                shape = PillShape,
            ) { Text(stringResource(R.string.reports_locked_cta)) }
        }
    }
}

@Composable
private fun CompareCard(rows: List<ReportBuilder.Comparison>) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.reports_compare_title), style = MaterialTheme.typography.titleMedium)
            if (rows.size < 2) Text(stringResource(R.string.reports_compare_empty), style = MaterialTheme.typography.bodyMedium)
            rows.forEach { c ->
                val v = c.vehicle
                Column(Modifier.heightIn(min = Sizes.listRowMin)) {
                    Text(
                        stringResource(R.string.reports_compare_row, v.name, formatMoney(c.totalMinor, v.currency)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val details = buildList {
                        c.costPerDistanceMinor?.let {
                            add(stringResource(R.string.reports_compare_cost_per, formatMoney(Math.round(it), v.currency), stringResource(v.distanceUnit.label())))
                        }
                        if (c.averageEconomy != null && c.economyUnit != null) {
                            add("${formatNumber(c.averageEconomy, 1)} ${stringResource(c.economyUnit.label())}")
                        }
                    }
                    if (details.isNotEmpty()) {
                        Text(details.joinToString(" · "), style = LocalWheelfileType.current.numberMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(stringResource(R.string.reports_compare_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Charts(vehicle: Vehicle, state: ReportsUiState) {
    // Economy: one chart per stream, because litres and kWh share no scale.
    if (state.economy.isEmpty()) {
        EmptyChart(R.string.reports_economy_title, R.string.reports_economy_empty)
    }
    state.economy.forEach { series ->
        val low = series.points.minOf { it.value }
        val high = series.points.maxOf { it.value }
        val unit = stringResource(series.unit.label())
        val latest = formatNumber(series.points.last().value, 1)
        val summary = if (series.points.size == 1) {
            stringResource(R.string.reports_economy_summary_single, latest, unit)
        } else {
            stringResource(R.string.reports_economy_summary, formatNumber(low, 1), formatNumber(high, 1), unit, latest)
        }
        val streamName = if (state.economy.size > 1) " · " + stringResource(streamLabel(series.stream)) else ""
        ChartCard(stringResource(R.string.reports_economy_title) + streamName, summary, CHART_ECONOMY_TAG) { LineChart(series.points) }
    }

    // Monthly cost: one chart per currency; amounts in different currencies are never added together.
    val costs = state.costs.filter { c -> c.months.any { it.totalMinor != 0L } }
    if (costs.isEmpty()) EmptyChart(R.string.reports_cost_title, R.string.reports_cost_empty)
    val barColors = BarColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    costs.forEach { c ->
        val highest = c.months.maxBy { it.totalMinor }
        val monthName = highest.month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy"))
        val summary = stringResource(
            R.string.reports_cost_summary,
            formatMoney(c.totalMinor, c.currency), c.months.size, monthName, formatMoney(highest.totalMinor, c.currency),
        )
        ChartCard(stringResource(R.string.reports_cost_title), summary, CHART_COST_TAG) {
            StackedBarChart(c.months, barColors)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                LegendDot(barColors.energy, stringResource(R.string.reports_legend_energy))
                LegendDot(barColors.service, stringResource(R.string.reports_legend_service))
                LegendDot(barColors.expenses, stringResource(R.string.reports_legend_expenses))
            }
        }
    }

    // Price per unit.
    if (state.prices.isEmpty()) EmptyChart(R.string.reports_price_title, R.string.reports_price_empty, priceUnit(vehicle, EnergyStream.LIQUID))
    state.prices.forEach { series ->
        val unit = priceUnit(vehicle, series.stream)
        val points = series.points.map { com.postpole.wheelfile.domain.report.EconomyPoint(it.dateTime, it.price) }
        val low = points.minOf { it.value }
        val high = points.maxOf { it.value }
        val fmt = { v: Double -> PriceFormat.unitPrice(v, vehicle.currency) }
        val summary = if (points.size == 1) {
            stringResource(R.string.reports_price_summary_single, unit, fmt(points.first().value))
        } else {
            stringResource(R.string.reports_price_summary, unit, fmt(low), fmt(high), fmt(points.last().value))
        }
        ChartCard(stringResource(R.string.reports_price_title, unit), summary, CHART_PRICE_TAG) { LineChart(points, color = MaterialTheme.colorScheme.tertiary) }
    }
}

@Composable
private fun priceUnit(vehicle: Vehicle, stream: EnergyStream): String =
    if (stream == EnergyStream.ELECTRIC) stringResource(R.string.unit_kwh) else stringResource(vehicle.volumeUnit.label())

@Composable
private fun EmptyChart(title: Int, message: Int, vararg args: Any) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(title, *args), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun streamLabel(s: EnergyStream) = when (s) {
    EnergyStream.LIQUID -> R.string.stream_liquid
    EnergyStream.ELECTRIC -> R.string.stream_electric
    EnergyStream.GAS_KG -> R.string.stream_gas
}
