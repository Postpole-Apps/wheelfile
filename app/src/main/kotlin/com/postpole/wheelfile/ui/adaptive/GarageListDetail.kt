package com.postpole.wheelfile.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.window.core.layout.WindowSizeClass
import com.postpole.wheelfile.R

/** Medium windows (600 dp and wider), which is where Garage | Vehicle show side by side (spec §3.2). */
@Composable
fun isListDetailWidth(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

/**
 * Garage | Vehicle list-detail for medium and expanded widths and for foldables (spec §3.2). The panes come from the
 * adaptive library, so an unfolded device with a vertical hinge splits the panes at the hinge instead of across it.
 *
 * Medium windows get two panes too (the library defaults to one there), so a 7" tablet shows both.
 * [selectedVehicleId] is owned by the caller; null shows [placeholder] in the detail pane.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun GarageListDetail(
    selectedVehicleId: Long?,
    listPane: @Composable () -> Unit,
    detailPane: @Composable (vehicleId: Long) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = { SelectVehiclePlaceholder() },
) {
    val info = currentWindowAdaptiveInfo()
    val base = calculatePaneScaffoldDirective(info)
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>(
        scaffoldDirective = base.copy(maxHorizontalPartitions = maxOf(2, base.maxHorizontalPartitions)),
    )
    LaunchedEffect(selectedVehicleId) {
        if (selectedVehicleId != null) navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, selectedVehicleId)
    }
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        listPane = { AnimatedPane { listPane() } },
        detailPane = {
            AnimatedPane {
                if (selectedVehicleId != null) detailPane(selectedVehicleId) else placeholder()
            }
        },
    )
}

@Composable
fun SelectVehiclePlaceholder() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.adaptive_select_vehicle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
