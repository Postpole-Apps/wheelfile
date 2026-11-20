package com.postpole.template.ui.pro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.template.R

/** Shows [content] for Pro users, otherwise [locked] (default: an upgrade prompt). */
@Composable
fun ProGate(
    isPro: Boolean,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    locked: @Composable () -> Unit = { DefaultLocked(onUpgrade, modifier) },
    content: @Composable () -> Unit,
) {
    if (isPro) content() else locked()
}

/** Convenience overload that reads the entitlement from [ProViewModel]. */
@Composable
fun ProGate(
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val isPro = viewModel.isPro.collectAsStateWithLifecycle().value
    ProGate(isPro = isPro, onUpgrade = onUpgrade, modifier = modifier, content = content)
}

@Composable
private fun DefaultLocked(onUpgrade: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(16.dp)) {
        Text(stringResource(R.string.pro_locked))
        Button(onClick = onUpgrade) { Text(stringResource(R.string.upgrade_to_pro)) }
    }
}
