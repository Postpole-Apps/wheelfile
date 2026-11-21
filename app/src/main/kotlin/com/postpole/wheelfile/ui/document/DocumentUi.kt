package com.postpole.wheelfile.ui.document

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.DocumentItem
import com.postpole.wheelfile.data.attachments.AttachmentLimit
import com.postpole.wheelfile.data.db.DocumentType
import com.postpole.wheelfile.domain.document.DocumentAlerts
import com.postpole.wheelfile.domain.document.DocumentPreset
import com.postpole.wheelfile.domain.document.ExpiryStatus
import com.postpole.wheelfile.domain.document.PresetRegion
import com.postpole.wheelfile.ui.common.formatDate
import com.postpole.wheelfile.ui.theme.FolderTabShape
import com.postpole.wheelfile.ui.theme.LocalWheelfileColors
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

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
fun DocumentPreset.titleRes(): Int = when (code) {
    "gb_mot" -> R.string.preset_gb_mot
    "gb_vehicle_tax" -> R.string.preset_gb_vehicle_tax
    "de_hu" -> R.string.preset_de_hu
    "fr_ct" -> R.string.preset_fr_ct
    "es_itv" -> R.string.preset_es_itv
    "us_ca_smog" -> R.string.preset_us_ca_smog
    "au_nsw_rego" -> R.string.preset_au_nsw_rego
    "au_safety_inspection" -> R.string.preset_au_safety_inspection
    "insurance_renewal" -> R.string.preset_insurance_renewal
    "licence_expiry" -> R.string.preset_licence_expiry
    else -> R.string.preset_generic_inspection
}

@StringRes
fun DocumentPreset.ruleRes(): Int = when (code) {
    "gb_mot" -> R.string.preset_rule_gb_mot
    "gb_vehicle_tax" -> R.string.preset_rule_gb_vehicle_tax
    "de_hu" -> R.string.preset_rule_de_hu
    "fr_ct" -> R.string.preset_rule_fr_ct
    "es_itv" -> R.string.preset_rule_es_itv
    "au_nsw_rego" -> R.string.preset_rule_au_nsw_rego
    else -> R.string.preset_rule_user_set
}

@StringRes
fun PresetRegion.label(): Int = when (this) {
    PresetRegion.GB -> R.string.region_gb
    PresetRegion.DE -> R.string.region_de
    PresetRegion.FR -> R.string.region_fr
    PresetRegion.ES -> R.string.region_es
    PresetRegion.US_CA -> R.string.region_us_ca
    PresetRegion.AU_NSW -> R.string.region_au_nsw
    PresetRegion.GENERIC -> R.string.region_generic
}

/** The "Suggested schedule; check your local authority" line. It appears wherever presets are offered (docs/spec.md §6.4). */
@Composable
fun PresetDisclaimer(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.presets_disclaimer),
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Expiry pill: warning inside the alert window, expired after the date, quiet when valid. */
@Composable
fun ExpiryPill(expiry: Long?, alertDays: List<Int>, now: Long, modifier: Modifier = Modifier) {
    val colors = LocalWheelfileColors.current
    val status = DocumentAlerts.status(expiry, alertDays, now)
    val left = DocumentAlerts.daysLeft(expiry, now)
    val (container, content) = when (status) {
        ExpiryStatus.EXPIRED -> colors.negativeContainer to colors.onNegativeContainer
        ExpiryStatus.EXPIRING -> colors.warningContainer to colors.onWarningContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = when {
        expiry == null || left == null -> stringResource(R.string.documents_no_expiry)
        status == ExpiryStatus.EXPIRED -> stringResource(R.string.expiry_pill_expired)
        status == ExpiryStatus.EXPIRING && left == 0L -> stringResource(R.string.expiry_pill_expires_today)
        status == ExpiryStatus.EXPIRING -> pluralStringResource(R.plurals.expiry_pill_expires_in, left.toInt(), left.toInt())
        else -> stringResource(R.string.expiry_pill_valid_until, formatDate(expiry))
    }
    Surface(shape = RoundedCornerShape(10.dp), color = container, contentColor = content, modifier = modifier) {
        Text(text, Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/**
 * A folder-tab card (design: FolderTabShape, 12 dp tab radius, 20 dp body). The tab is sized from its own label so the
 * outline stays a single shape at any font scale.
 */
@Composable
fun FolderCard(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var tabWidth by remember { mutableStateOf(96.dp) }
    val tabHeight = 26.dp
    val shape = remember(tabWidth) { FolderTabShape(tabWidth = tabWidth, tabHeight = tabHeight) }
    Box(modifier.fillMaxWidth().background(container, shape)) {
        Box(
            Modifier
                .onSizeChanged { tabWidth = with(density) { it.width.toDp() } }
                .height(tabHeight)
                .padding(horizontal = Spacing.m),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
        }
        Column(Modifier.padding(top = tabHeight)) { body() }
    }
}

/**
 * s11-documents: the Documents tab of Vehicle detail. Folder-tab cards, expiry pills, the free attachment counter
 * (hidden for Pro) and the preset offer with its disclaimer. The banner belongs to the list only and is laid out by the
 * caller, so Document detail and the attachment viewer can never show it.
 */
@Composable
fun DocumentsTab(
    items: List<DocumentItem>,
    attachmentCount: Int,
    receiptCount: Int,
    isPro: Boolean,
    readOnly: Boolean,
    now: Long,
    onOpen: (Long) -> Unit,
    onPresets: () -> Unit,
    bottomPadding: Dp = 96.dp,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = Sizes.screenMargin, end = Sizes.screenMargin, top = Spacing.m, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(stringResource(R.string.documents_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.documents_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            FolderCard(
                label = stringResource(R.string.documents_folder_papers),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                items.forEach { DocumentRow(it, now, onOpen) }
            }
        }
        if (receiptCount > 0) {
            FolderCard(
                label = stringResource(R.string.documents_folder_receipts),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text(
                    pluralStringResource(R.plurals.documents_receipts_linked, receiptCount, receiptCount),
                    Modifier.fillMaxWidth().padding(Sizes.cardPadding),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        if (!isPro) {
            Text(
                stringResource(R.string.documents_attachment_counter, attachmentCount, AttachmentLimit.FREE_ATTACHMENTS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!readOnly) {
            val modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.touchTargetMin)
            if (items.isEmpty()) {
                Button(onClick = onPresets, modifier = modifier, shape = PillShape) { Text(stringResource(R.string.documents_presets_cta)) }
            } else {
                OutlinedButton(onClick = onPresets, modifier = modifier, shape = PillShape) { Text(stringResource(R.string.documents_presets_cta)) }
            }
            PresetDisclaimer()
        }
    }
}

@Composable
private fun DocumentRow(item: DocumentItem, now: Long, onOpen: (Long) -> Unit) {
    val doc = item.document
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin).clickable { onOpen(doc.id) }
            .padding(horizontal = Sizes.cardPadding, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            val files = if (item.attachmentCount > 0) {
                " · " + pluralStringResource(R.plurals.documents_file_count, item.attachmentCount, item.attachmentCount)
            } else {
                ""
            }
            Text(
                stringResource(doc.type.label()) + files,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
        ExpiryPill(doc.expiryDate, doc.alertDays, now)
    }
}
