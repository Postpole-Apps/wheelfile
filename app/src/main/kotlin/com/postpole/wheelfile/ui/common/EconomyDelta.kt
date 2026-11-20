package com.postpole.wheelfile.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.postpole.wheelfile.R
import com.postpole.wheelfile.data.db.EconomyUnit
import com.postpole.wheelfile.domain.economy.VehicleStatsCalculator

/** True for units where a bigger number means less energy used (km/L, MPG, mi/kWh). */
fun EconomyUnit.higherIsBetter(): Boolean = when (this) {
    EconomyUnit.KM_PER_L, EconomyUnit.MPG_US, EconomyUnit.MPG_UK, EconomyUnit.MI_PER_KWH -> true
    else -> false
}

/**
 * Economy change pill: arrow plus text. [deltaPercent] is in energy-per-distance terms, so negative is better.
 * The arrow follows the direction the displayed number moved in [unit].
 */
@Composable
fun EconomyDelta(deltaPercent: Double, unit: EconomyUnit, modifier: Modifier = Modifier) {
    if (!VehicleStatsCalculator.isMeaningful(deltaPercent)) {
        DeltaPill("= ${stringResource(R.string.delta_same)}", better = null, modifier = modifier)
        return
    }
    val better = deltaPercent < 0
    val up = better == unit.higherIsBetter()
    val magnitude = formatNumber(kotlin.math.abs(deltaPercent), 1)
    val text = stringResource(if (better) R.string.delta_better else R.string.delta_worse, magnitude)
    DeltaPill("${if (up) "▲" else "▼"} $text", better = better, modifier = modifier)
}
