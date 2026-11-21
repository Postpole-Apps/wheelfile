package com.postpole.wheelfile.ui.report

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.VehicleData
import com.postpole.wheelfile.domain.report.CurrencyComparison
import com.postpole.wheelfile.domain.report.CurrencyMonthly
import com.postpole.wheelfile.domain.report.EconomySeries
import com.postpole.wheelfile.domain.report.PriceSeries
import com.postpole.wheelfile.domain.report.ReportBuilder
import com.postpole.wheelfile.domain.report.ReportRange
import com.postpole.wheelfile.domain.report.ReportSource
import com.postpole.wheelfile.domain.report.ReportWindow
import com.postpole.wheelfile.ui.common.DateField
import com.postpole.wheelfile.ui.common.formatMoney
import com.postpole.wheelfile.ui.common.formatNumber
import com.postpole.wheelfile.ui.common.label
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Currency
import java.util.Locale

/** What the Reports tab needs besides the vehicle's rows. */
data class ReportsInput(
    val range: ReportRange,
    val customFrom: Long?,
    val customTo: Long?,
    val isPro: Boolean,
    val compareSources: List<ReportSource>,
    val now: Long,
)

@StringRes
private fun ReportRange.label(): Int = when (this) {
    ReportRange.LAST_12_MONTHS -> R.string.report_range_12m
    ReportRange.ALL_TIME -> R.string.report_range_all
    ReportRange.COMPARE -> R.string.report_range_compare
    ReportRange.CUSTOM -> R.string.report_range_custom
}

/**
 * s13-reports and st-pro-upsell: the Reports tab of Vehicle detail. Free users get the 12-month window; All time,
 * Compare and Custom stay visible with a lock and show an inline locked card with "See Pro" instead of an interrupt.
 * Money is never converted between currencies. The banner is laid out by the caller.
 */
@Composable
fun ReportsTab(
    data: VehicleData,
    input: ReportsInput,
    onRange: (ReportRange) -> Unit,
    onCustom: (from: Long?, to: Long?) -> Unit,
    onSeePro: () -> Unit,
    bottomPadding: Dp = 16.dp,
) {
    val locked = input.range.isPro && !input.isPro
    // A locked range keeps showing the free 12-month charts underneath the inline card.
    val effective = if (locked) ReportRange.LAST_12_MONTHS else input.range
    val zone = ZoneId.systemDefault()
    val customWindow = remember(input.customFrom, input.customTo) {
        val from = input.customFrom
        val to = input.customTo
        if (from != null && to != null) {
            ReportBuilder.custom(
                Instant.ofEpochMilli(from).atZone(zone).toLocalDate(),
                Instant.ofEpochMilli(to).atZone(zone).toLocalDate(),
                zone,
            )
        } else {
            null
        }
    }
    val needsDates = effective == ReportRange.CUSTOM && customWindow == null
    val window = ReportBuilder.window(effective, input.now, customWindow, zone)
    val source = ReportSource(data.vehicle, data.logs, data.services, data.expenses)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Spacing.s, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        RangeChips(input.range, input.isPro, onRange)
        Column(Modifier.padding(horizontal = Sizes.screenMargin), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (locked) ProLockedCard(onSeePro)
            if (!input.isPro && !locked) {
                Text(
                    stringResource(R.string.report_free_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (effective == ReportRange.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    DateField(
                        stringResource(R.string.report_custom_from), input.customFrom, { onCustom(it, input.customTo) },
                        Modifier.weight(1f),
                    )
                    DateField(
                        stringResource(R.string.report_custom_to), input.customTo, { onCustom(input.customFrom, it) },
                        Modifier.weight(1f),
                    )
                }
                if (needsDates) {
                    Text(
                        stringResource(R.string.report_custom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!needsDates) {
                if (effective == ReportRange.COMPARE) {
                    CompareCards(ReportBuilder.compare(input.compareSources.ifEmpty { listOf(source) }, window))
                } else {
                    ReportCharts(source, window, zone)
                }
            }
        }
    }
}

@Composable
private fun ReportCharts(source: ReportSource, window: ReportWindow, zone: ZoneId) {
    ReportBuilder.economySeries(source, window).let { series ->
        if (series.isEmpty()) {
            ChartCard(stringResource(R.string.report_economy_title)) { EmptyNote(R.string.report_empty_economy) }
        } else {
            series.forEach { EconomyCard(it) }
        }
    }
    val monthly = ReportBuilder.monthlyCosts(listOf(source), window, zone)
    if (monthly.isEmpty()) {
        ChartCard(stringResource(R.string.report_cost_title)) { EmptyNote(R.string.report_empty_cost) }
    } else {
        monthly.forEach { CostCard(it) }
    }
    val prices = ReportBuilder.priceSeries(source, window)
    if (prices.isEmpty()) {
        ChartCard(stringResource(R.string.report_price_title)) { EmptyNote(R.string.report_empty_price) }
    } else {
        prices.forEach { PriceCard(it) }
    }
}

@Composable
private fun RangeChips(selected: ReportRange, isPro: Boolean, onRange: (ReportRange) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Sizes.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReportRange.entries.forEach { range ->
            val lockedChip = range.isPro && !isPro
            val name = stringResource(range.label())
            val description = if (lockedChip) stringResource(R.string.report_range_locked, name) else name
            FilterChip(
                selected = range == selected,
                onClick = { onRange(range) },
                label = { Text(name) },
                trailingIcon = if (lockedChip) {
                    { Icon(Icons.Default.Lock, contentDescription = null, Modifier.padding(0.dp)) }
                } else {
                    null
                },
                modifier = Modifier.heightIn(min = Sizes.touchTargetMin).semantics { contentDescription = description },
            )
        }
    }
}

/** The inline locked card (st-pro-upsell). It never covers the screen and never blocks the charts below it. */
@Composable
fun ProLockedCard(onSeePro: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    stringResource(R.string.report_locked_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                stringResource(R.string.report_locked_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(onClick = onSeePro, Modifier.heightIn(min = Sizes.touchTargetMin)) { Text(stringResource(R.string.report_see_pro)) }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun EmptyNote(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The visible one-sentence summary. The chart itself carries the same text as its TalkBack description. */
@Composable
private fun Summary(text: String) {
    Text(
        text,
        Modifier.clearAndSetSemantics { },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EconomyCard(series: EconomySeries) {
    val unit = stringResource(series.unit.label())
    val values = series.points.map { it.value }
    val summary = pluralStringResource(
        R.plurals.report_summary_economy, series.points.size,
        formatNumber(values.last(), 2), formatNumber(values.min(), 2), formatNumber(values.max(), 2), unit, series.points.size,
    )
    ChartCard("${stringResource(R.string.report_economy_title)} ($unit)") {
        LineChart(
            points = series.points.map { ChartPoint(it.dateTime, it.value) },
            color = MaterialTheme.colorScheme.primary,
            summary = summary,
            topLabel = "${formatNumber(values.max(), 2)} $unit",
            bottomLabel = formatNumber(values.min(), 2),
        )
        Summary(summary)
    }
}

@Composable
private fun CostCard(monthly: CurrencyMonthly) {
    val currency = monthly.currency
    val months = monthly.months
    val total = monthly.totalMinor
    val peak = months.maxByOrNull { it.totalMinor }
    val summary = if (total == 0L || peak == null) {
        stringResource(R.string.report_summary_cost_empty, currency)
    } else {
        pluralStringResource(
            R.plurals.report_summary_cost, months.size,
            currency, formatMoney(total, currency), months.size, shortMonth(peak, Locale.getDefault()), formatMoney(peak.totalMinor, currency),
        )
    }
    ChartCard("${stringResource(R.string.report_cost_title)} ($currency)") {
        val colors = CostColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
        StackedBarChart(
            months = months,
            colors = colors,
            summary = summary,
            topLabel = formatMoney(peak?.totalMinor ?: 0L, currency),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            LegendDot(colors.fuel, stringResource(R.string.report_legend_fuel))
            LegendDot(colors.service, stringResource(R.string.report_legend_service))
            LegendDot(colors.expenses, stringResource(R.string.report_legend_expenses))
        }
        Summary(summary)
    }
}

@Composable
private fun PriceCard(series: PriceSeries) {
    val currency = series.currency
    val values = series.points.map { it.pricePerUnit }
    val summary = pluralStringResource(
        R.plurals.report_summary_price, series.points.size,
        currency, formatPrice(values.last(), currency), formatPrice(values.min(), currency), formatPrice(values.max(), currency), series.points.size,
    )
    ChartCard("${stringResource(R.string.report_price_title)} ($currency)") {
        LineChart(
            points = series.points.map { ChartPoint(it.dateTime, it.pricePerUnit) },
            color = MaterialTheme.colorScheme.secondary,
            summary = summary,
            topLabel = formatPrice(values.max(), currency),
            bottomLabel = formatPrice(values.min(), currency),
        )
        Summary(summary)
    }
}

@Composable
private fun CompareCards(groups: List<CurrencyComparison>) {
    if (groups.all { it.vehicles.size < 2 }) {
        ChartCard(stringResource(R.string.report_range_compare)) { EmptyNote(R.string.report_empty_compare) }
    }
    groups.forEach { group ->
        val widest = group.vehicles.maxOfOrNull { it.totalMinor }?.coerceAtLeast(1L) ?: 1L
        val summary = stringResource(
            R.string.report_summary_compare, group.currency,
            group.vehicles.joinToString("; ") { "${it.name} ${formatMoney(it.totalMinor, group.currency)}" },
        )
        ChartCard(stringResource(R.string.report_compare_title, group.currency)) {
            Column(
                Modifier.semantics { contentDescription = summary },
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                group.vehicles.forEach { v ->
                    Row(Modifier.clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(v.name, style = MaterialTheme.typography.bodyMedium)
                        Text(formatMoney(v.totalMinor, group.currency), style = MaterialTheme.typography.bodyMedium)
                    }
                    CompareBar(v.totalMinor.toFloat() / widest, MaterialTheme.colorScheme.primary, Modifier.clearAndSetSemantics { })
                }
            }
            Summary(summary)
        }
    }
}

/** A unit price such as 1.789 per litre: up to three decimals, in the vehicle's currency, in the device format. */
fun formatPrice(value: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
    val currency = try {
        Currency.getInstance(currencyCode)
    } catch (_: IllegalArgumentException) {
        Currency.getInstance("USD")
    }
    return NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
        minimumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
        maximumFractionDigits = maxOf(3, minimumFractionDigits)
    }.format(value)
}
