package com.postpole.wheelfile.ui.garage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.R

/** Placeholder Garage (V1): title and empty state only, no ads. V3 builds the real Garage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    onOpenSettings: () -> Unit,
    onOpenPro: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.garage_title)) },
                actions = {
                    TextButton(onClick = onOpenPro) { Text(stringResource(R.string.nav_pro)) }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.nav_settings)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.garage_empty))
        }
    }
}
