package com.postpole.wheelfile.ui.report

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.ui.pro.ProGate

/** The Pro history report: no ads on this screen or in the PDF. Free users see the upgrade prompt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReportRoute(
    onBack: () -> Unit,
    onOpenPro: () -> Unit,
    viewModel: PdfReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.pdf_failed)
    val shareTitle = stringResource(R.string.pdf_share_title)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PdfEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    } catch (_: ActivityNotFoundException) {
                        snackbar.showSnackbar(failed)
                    }
                }
                PdfEvent.Failed -> snackbar.showSnackbar(failed)
            }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pdf_screen_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.vehicleName?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
            Text(stringResource(R.string.pdf_screen_body), style = MaterialTheme.typography.bodyLarge)
            ProGate(isPro = isPro, onUpgrade = onOpenPro) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.pdf_include_attachments), Modifier.weight(1f))
                        Switch(checked = state.includeAttachments, onCheckedChange = viewModel::setIncludeAttachments)
                    }
                    Button(onClick = viewModel::create, enabled = !state.creating, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (state.creating) R.string.pdf_creating else R.string.pdf_create))
                    }
                }
            }
        }
    }
}
