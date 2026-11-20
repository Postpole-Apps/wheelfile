package com.postpole.template.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.template.R
import com.postpole.template.billing.BillingMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(onBack: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val price by viewModel.price.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val unavailable by viewModel.productUnavailable.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val purchaseError = stringResource(R.string.pro_error_purchase)
    val launchError = stringResource(R.string.pro_error_launch)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect {
            snackbarHostState.showSnackbar(
                when (it) {
                    BillingMessage.PURCHASE_ERROR -> purchaseError
                    BillingMessage.LAUNCH_FAILED -> launchError
                },
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_pro)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.pro_features_title), style = MaterialTheme.typography.titleLarge)
            // Placeholder feature list: replace per app.
            listOf(R.string.pro_feature_1, R.string.pro_feature_2, R.string.pro_feature_3).forEach {
                Text("• " + stringResource(it))
            }
            when {
                isPro -> Text(stringResource(R.string.pro_active), style = MaterialTheme.typography.titleMedium)
                pending -> Text(stringResource(R.string.pro_pending))
                unavailable && price == null -> Text(stringResource(R.string.pro_unavailable))
                else -> Button(
                    onClick = { activity?.let(viewModel::buy) },
                    enabled = price != null && activity != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(price?.let { stringResource(R.string.pro_buy, it) } ?: stringResource(R.string.pro_loading))
                }
            }
            OutlinedButton(onClick = viewModel::restore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pro_restore))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
