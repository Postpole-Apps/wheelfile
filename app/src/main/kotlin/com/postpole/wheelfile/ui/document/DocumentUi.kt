package com.postpole.wheelfile.ui.document

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.domain.document.DocumentExpiry
import com.postpole.wheelfile.domain.document.DocumentPreset
import com.postpole.wheelfile.domain.document.ExpiryStatus
import com.postpole.wheelfile.domain.document.PresetSchedule
import com.postpole.wheelfile.domain.document.Region
import com.postpole.wheelfile.domain.document.RegionPresets
import com.postpole.wheelfile.ui.common.ChoiceChips
import com.postpole.wheelfile.ui.common.formatDate
import com.postpole.wheelfile.ui.theme.HeroShape
import com.postpole.wheelfile.ui.theme.LocalWheelfileColors
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId

@StringRes
fun DocumentType.label(): Int = when (this) {
    DocumentType.INSURANCE -> R.string.doc_type_insurance
    DocumentType.REGISTRATION -> R.string.doc_type_registration
    DocumentType.INSPECTION -> R.string.doc_type_inspection
    DocumentType.LICENCE -> R.string.doc_type_licence
    DocumentType.WARRANTY -> R.string.doc_type_warranty
    DocumentType.ROADSIDE_COVER -> R.string.doc_type_roadside
    DocumentType.LEASE -> R.string.doc_type_lease
    DocumentType.CUSTOM -> R.string.doc_type_custom
}

@StringRes
fun Region.label(): Int = when (this) {
    Region.GB -> R.string.region_gb
    Region.DE -> R.string.region_de
    Region.FR -> R.string.region_fr
    Region.ES -> R.string.region_es
    Region.US_CA -> R.string.region_us_ca
    Region.AU_NSW -> R.string.region_au_nsw
    Region.GENERIC -> R.string.region_generic
}

/** Expiry tag: warning when inside the earliest alert, error colours once expired. The words carry the meaning, not the colour. */
@Composable
fun ExpiryPill(expiry: Long?, alertDays: List<Int>, now: Long, modifier: Modifier = Modifier) {
    val colors = LocalWheelfileColors.current
    val zone = ZoneId.systemDefault()
    val status = DocumentExpiry.status(expiry, alertDays, now, zone)
    val days = DocumentExpiry.daysLeft(expiry, now, zone)
    val text = when {
        expiry == null || days == null -> stringResource(R.string.document_pill_none)
        status == ExpiryStatus.EXPIRED -> stringResource(R.string.document_pill_expired)
        days == 0L -> stringResource(R.string.document_pill_today)
        status == ExpiryStatus.EXPIRING -> pluralStringResource(R.plurals.document_pill_expiring, days.toInt(), days.toInt())
        else -> stringResource(R.string.document_pill_valid, formatDate(expiry, zone))
    }
    val container = when (status) {
        ExpiryStatus.EXPIRED -> MaterialTheme.colorScheme.errorContainer
        ExpiryStatus.EXPIRING -> colors.warningContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when (status) {
        ExpiryStatus.EXPIRED -> MaterialTheme.colorScheme.onErrorContainer
        ExpiryStatus.EXPIRING -> colors.onWarningContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = container, contentColor = content) {
        Text(text, Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs), style = MaterialTheme.typography.labelMedium)
    }
}

/** The dashed-outline note of s01d: presets are suggestions, never legal advice (spec §2.6). */
@Composable
fun PresetsDisclaimer(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.outline
    val density = androidx.compose.ui.platform.LocalDensity.current
    Text(
        stringResource(R.string.presets_disclaimer),
        modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = with(density) { 1.dp.toPx() }
                val dash = with(density) { 4.dp.toPx() }
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(with(density) { 12.dp.toPx() }),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                )
            }
            .padding(Spacing.m),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Region chips, the presets of that region as checkboxes (rows at least 56 dp, 48 dp targets) and the disclaimer.
 * Used by onboarding step 3 (`s01d-region-presets`) and by the Documents tab.
 */
@Composable
fun PresetPicker(
    region: Region,
    onRegion: (Region) -> Unit,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    vehicleYear: Int?,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(stringResource(R.string.presets_region), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceChips(
            options = Region.entries,
            selected = region,
            label = { stringResource(it.label()) },
            onSelect = onRegion,
        )
        Surface(shape = HeroShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = Spacing.xs)) {
                RegionPresets.presetsFor(region).forEach { preset ->
                    PresetRow(preset, preset.code in selected, { onToggle(preset.code) }, vehicleYear, today)
                }
            }
        }
        PresetsDisclaimer()
    }
}

@Composable
private fun PresetRow(preset: DocumentPreset, checked: Boolean, onToggle: () -> Unit, vehicleYear: Int?, today: LocalDate) {
    val plan = RegionPresets.plan(preset, RegionPresets.registrationFromYear(vehicleYear), null, today)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRowMin)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = Spacing.l, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(preset.title, style = MaterialTheme.typography.titleSmall)
            Text(ruleText(preset.schedule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                plan.expiry?.let { stringResource(R.string.presets_due_estimate, formatDate(RegionPresetsDates.millis(it))) }
                    ?: stringResource(R.string.presets_needs_date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ruleText(schedule: PresetSchedule): String = when (schedule) {
    is PresetSchedule.AgeBased -> buildString {
        val intervals = schedule.intervals.sortedBy { it.fromAgeMonths }
        append(stringResource(R.string.presets_rule_age, schedule.firstAfterMonths, intervals.first().everyMonths))
        intervals.drop(1).forEach {
            append(' ').append(stringResource(R.string.presets_rule_age_later, it.everyMonths, it.fromAgeMonths / 12))
        }
        if (schedule.windowMonthsBefore > 0) append(' ').append(stringResource(R.string.presets_rule_age_window, schedule.windowMonthsBefore))
    }
    is PresetSchedule.Renewal -> stringResource(R.string.presets_rule_renewal, schedule.everyMonths)
    PresetSchedule.UserSet -> stringResource(R.string.presets_rule_user)
}

internal object RegionPresetsDates {
    fun millis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}
