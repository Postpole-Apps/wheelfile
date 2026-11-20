package com.postpole.wheelfile.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.postpole.wheelfile.ads.AdScreen
import com.postpole.wheelfile.ads.AdSlot

/**
 * Debug-only preview of [AdSlot] (reached from Settings in debug builds). Real screens get the slot in
 * later issues; this shows the slot reserving, loading and collapsing, and the height a FAB would use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdPreviewScreen(onBack: () -> Unit) {
    var slotHeight by remember { mutableStateOf(0.dp) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ad slot preview") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
        bottomBar = { AdSlot(AdScreen.GARAGE, onHeightChange = { slotHeight = it }) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column {
                Text("Slot height: ${format(slotHeight)}")
                Text("FAB would sit 16 dp above the slot.")
            }
        }
    }
}

private fun format(height: Dp) = "${height.value.toInt()} dp"
