package com.postpole.wheelfile.ui.document

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.domain.document.PresetRegion
import com.postpole.wheelfile.domain.document.PresetRule
import com.postpole.wheelfile.ui.common.ChoiceChips
import com.postpole.wheelfile.ui.common.DateField
import com.postpole.wheelfile.ui.common.SectionLabel
import com.postpole.wheelfile.ui.common.formatDate
import com.postpole.wheelfile.ui.theme.PillShape
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.Spacing

/**
 * s01d-region-presets: the region presets offer. It is onboarding step 3 right after the first vehicle is saved
 * ([onboarding] = true) and is also reachable from the Documents tab. 0 ads. Nothing is added until the user confirms.
 */
@Composable
fun PresetsRoute(
    onboarding: Boolean,
    onDone: () -> Unit,
    viewModel: PresetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { if (it == PresetsEvent.Done) onDone() }
    }
    val titles = com.postpole.wheelfile.domain.document.RegionPresets.all.associate { it.code to stringResource(it.titleRes()) }
    PresetsScreen(
        state = state,
        onboarding = onboarding,
        onRegion = viewModel::setRegion,
        onToggle = viewModel::toggle,
        onFirstRegistration = viewModel::setFirstRegistration,
        onClearFirstRegistration = viewModel::clearFirstRegistration,
        onAdd = { viewModel.apply(titles) },
        onSkip = viewModel::skip,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    state: PresetsUiState,
    onboarding: Boolean,
    onRegion: (PresetRegion) -> Unit,
    onToggle: (String) -> Unit,
    onFirstRegistration: (Long) -> Unit,
    onClearFirstRegistration: () -> Unit,
    onAdd: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.presets_title), style = MaterialTheme.typography.titleLarge)
                        if (onboarding) {
                            Text(
                                stringResource(R.string.add_vehicle_step, 3, 3),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onSkip) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
                },
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Sizes.screenMargin),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    stringResource(R.string.presets_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SectionLabel(stringResource(R.string.presets_region))
                ChoiceChips(PresetRegion.entries, state.region, { stringResource(it.label()) }, onRegion)
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    state.rows.forEach { row ->
                        val title = stringResource(row.preset.titleRes())
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Sizes.listRowMin)
                                .toggleable(value = row.selected, role = Role.Checkbox, onValueChange = { onToggle(row.preset.code) })
                                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = row.selected, onCheckedChange = null, modifier = Modifier.padding(Spacing.m))
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(row.preset.ruleRes()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val due = row.due.dueDate
                                val dueText = when {
                                    due == null && row.preset.rule is PresetRule.UserSet -> stringResource(R.string.presets_due_user_set)
                                    due == null -> null
                                    row.due.windowStart != null -> stringResource(
                                        R.string.presets_due_window,
                                        formatDate(epoch(due)), formatDate(epoch(row.due.windowStart)),
                                    )
                                    else -> stringResource(R.string.presets_due, formatDate(epoch(due)))
                                }
                                if (dueText != null) Text(dueText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (state.rows.any { it.preset.rule is PresetRule.AgeBased }) {
                    DateField(
                        label = stringResource(R.string.presets_first_registration),
                        epochMillis = state.firstRegistration,
                        onPick = onFirstRegistration,
                    )
                    if (state.firstRegistration == null) {
                        Text(
                            stringResource(R.string.presets_first_registration_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TextButton(onClick = onClearFirstRegistration, Modifier.heightIn(min = Sizes.touchTargetMin)) {
                            Text(stringResource(R.string.presets_first_registration_clear))
                        }
                    }
                }
                // Dashed outline in the design: a plain outlined card keeps the same weight and contrast.
                OutlinedCard(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    PresetDisclaimer(Modifier.padding(Spacing.m))
                }
                Spacer(Modifier.height(Spacing.s))
            }
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.navigationBarsPadding().padding(horizontal = Sizes.screenMargin, vertical = Spacing.m).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSkip, Modifier.heightIn(min = Sizes.touchTargetMin)) { Text(stringResource(R.string.presets_skip)) }
                    Button(
                        onClick = onAdd,
                        enabled = state.vehicle != null && !state.saving && state.selectedCount > 0,
                        modifier = Modifier.heightIn(min = Sizes.saveButtonHeight),
                        shape = PillShape,
                    ) { Text(stringResource(R.string.presets_add), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

private fun epoch(date: java.time.LocalDate): Long =
    date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
