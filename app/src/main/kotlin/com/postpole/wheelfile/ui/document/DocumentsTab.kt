package com.postpole.wheelfile.ui.document

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.DocumentItem
import com.postpole.wheelfile.data.attachments.AttachmentLimit
import com.postpole.wheelfile.domain.document.DocumentPreset
import com.postpole.wheelfile.domain.document.RegionPresets
import com.postpole.wheelfile.ui.theme.FolderTabShape
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing
import java.time.LocalDate
import java.util.Locale

/** Everything the Documents tab shows. Built by the Vehicle detail view model. */
data class DocumentsUiState(
    val documents: List<DocumentItem> = emptyList(),
    val receiptCount: Int = 0,
    /** Every attachment on the device: the number the free cap of 30 counts. */
    val attachmentTotal: Int = 0,
    val isPro: Boolean = false,
    val vehicleYear: Int? = null,
    val readOnly: Boolean = false,
)

const val DOCUMENTS_TAB_TAG = "DocumentsTab"
const val DOCUMENT_DETAIL_TAG = "DocumentDetail"

/**
 * s11-vehicle-documents: folder-tab cards ("Vehicle papers" and "Receipts"), expiry pills, the free attachment counter
 * and the regional presets. The banner is placed by Vehicle detail (it belongs to this list, never to the detail screen).
 */
@Composable
fun DocumentsTab(
    state: DocumentsUiState,
    now: Long,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onApplyPresets: (List<DocumentPreset>) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    var showPresets by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().testTag(DOCUMENTS_TAB_TAG).verticalScroll(rememberScrollState())
            .padding(horizontal = Sizes.screenMargin, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        FolderCard(
            label = stringResource(R.string.documents_vehicle_papers),
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            if (state.documents.isEmpty()) {
                Text(stringResource(R.string.documents_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Spacing.s))
            }
            state.documents.forEach { item -> DocumentRow(item, now, onOpen) }
            if (!state.readOnly) {
                TextButton(onClick = { showPresets = true }, Modifier.heightIn(min = Sizes.touchTargetMin)) {
                    Text(stringResource(R.string.documents_presets))
                }
            }
        }
        FolderCard(
            label = stringResource(R.string.documents_receipts),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Text(
                if (state.receiptCount == 0) {
                    stringResource(R.string.documents_receipts_none)
                } else {
                    pluralStringResource(R.plurals.documents_receipts_count, state.receiptCount, state.receiptCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = Spacing.s),
            )
        }
        Text(
            if (state.isPro) {
                stringResource(R.string.documents_attachment_count_pro, state.attachmentTotal)
            } else {
                stringResource(R.string.documents_attachment_counter, state.attachmentTotal, AttachmentLimit.FREE_ATTACHMENTS)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Room for the FAB above the banner.
        Spacer(Modifier.height(Sizes.fab + Sizes.fabGap * 2))
    }
    if (showPresets) {
        PresetsSheet(state.vehicleYear, today, onDismiss = { showPresets = false }) {
            showPresets = false
            onApplyPresets(it)
        }
    }
}

@Composable
private fun FolderCard(label: String, container: Color, content: Color, body: @Composable ColumnScope.() -> Unit) {
    var tabWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Surface(
        shape = FolderTabShape(tabWidth = tabWidth),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // The tab: the folder shape is drawn around this label, so its measured width is the tab width.
            Box(
                Modifier.height(26.dp).onSizeChanged { tabWidth = with(density) { it.width.toDp() } }.padding(horizontal = Spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            Column(Modifier.padding(horizontal = Sizes.cardPadding).padding(bottom = Spacing.s), content = body)
        }
    }
}

@Composable
private fun DocumentRow(item: DocumentItem, now: Long, onOpen: (Long) -> Unit) {
    val doc = item.document
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin).clickable { onOpen(doc.id) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
            Text(doc.title, style = MaterialTheme.typography.titleSmall)
            val meta = buildList {
                add(stringResource(doc.type.label()))
                doc.number?.let { add(it) }
                if (item.attachmentCount > 0) add(pluralStringResource(R.plurals.document_attachments, item.attachmentCount, item.attachmentCount))
            }.joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.bodySmall)
        }
        ExpiryPill(doc.expiryDate, doc.alertDays, now)
    }
}

/** The preset picker in a bottom sheet. The region starts from the device locale and can be changed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetsSheet(vehicleYear: Int?, today: LocalDate, onDismiss: () -> Unit, onAdd: (List<DocumentPreset>) -> Unit) {
    var region by rememberSaveable { mutableStateOf(RegionPresets.regionFor(Locale.getDefault())) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(stringResource(R.string.presets_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.presets_body), style = MaterialTheme.typography.bodyMedium)
            PresetPicker(
                region = region,
                onRegion = { region = it },
                selected = selected,
                onToggle = { code -> selected = if (code in selected) selected - code else selected + code },
                vehicleYear = vehicleYear,
                today = today,
            )
            Button(
                onClick = { onAdd(selected.mapNotNull(RegionPresets::find)) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.saveButtonHeight),
                shape = PillShape,
            ) { Text(stringResource(R.string.presets_add_selected)) }
        }
    }
}
