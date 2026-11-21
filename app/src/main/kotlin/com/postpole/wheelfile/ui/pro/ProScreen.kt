package com.postpole.wheelfile.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.postpole.wheelfile.R
import com.postpole.wheelfile.billing.BillingMessage
import com.postpole.wheelfile.billing.RestoreResult
import com.postpole.wheelfile.ui.common.findActivity

const val PAYWALL_REMOVE_ADS_TAG = "paywall_remove_ads"
const val PAYWALL_PRO_TAG = "paywall_pro"
const val PAYWALL_RESTORE_TAG = "paywall_restore"

/** Everything the paywall shows. Prices are Play's formatted strings, null while loading or unavailable. */
data class PaywallState(
    val ownsPro: Boolean = false,
    val adsHidden: Boolean = false,
    val proPrice: String? = null,
    val removeAdsPrice: String? = null,
    val pending: Boolean = false,
    val unavailable: Boolean = false,
)

class PaywallActions(
    val onBack: () -> Unit,
    val onBuyPro: () -> Unit,
    val onBuyRemoveAds: () -> Unit,
    val onRestore: () -> Unit,
)

/** S17: Remove ads and Wheelfile Pro side by side, both one-time. No ads, countdowns or discounts on this screen. */
@Composable
fun ProScreen(trigger: PaywallTrigger, onBack: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val ownsPro by viewModel.isPro.collectAsStateWithLifecycle()
    val adsHidden by viewModel.adsHidden.collectAsStateWithLifecycle()
    val proPrice by viewModel.price.collectAsStateWithLifecycle()
    val removeAdsPrice by viewModel.removeAdsPrice.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val unavailable by viewModel.productUnavailable.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val purchaseError = stringResource(R.string.pro_error_purchase)
    val launchError = stringResource(R.string.pro_error_launch)
    val restored = stringResource(R.string.restore_done)
    val nothingFound = stringResource(R.string.restore_none)
    val restoreFailed = stringResource(R.string.restore_failed)
    LaunchedEffect(viewModel, trigger) { viewModel.onViewed(trigger) }
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
    LaunchedEffect(viewModel) {
        viewModel.restoreResults.collect {
            snackbarHostState.showSnackbar(
                when (it) {
                    RestoreResult.RESTORED -> restored
                    RestoreResult.NOTHING_FOUND -> nothingFound
                    RestoreResult.FAILED -> restoreFailed
                },
            )
        }
    }
    PaywallScreen(
        state = PaywallState(ownsPro, adsHidden, proPrice, removeAdsPrice, pending, unavailable),
        actions = PaywallActions(
            onBack = onBack,
            onBuyPro = { activity?.let(viewModel::buyPro) },
            onBuyRemoveAds = { activity?.let(viewModel::buyRemoveAds) },
            onRestore = viewModel::restore,
        ),
        canPurchase = activity != null,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    state: PaywallState,
    actions: PaywallActions,
    canPurchase: Boolean = true,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_pro)) },
                navigationIcon = { TextButton(onClick = actions.onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.paywall_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.paywall_subtitle), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionCard(
                    tag = PAYWALL_REMOVE_ADS_TAG,
                    title = R.string.paywall_remove_ads_title,
                    features = listOf(R.string.paywall_remove_ads_body),
                    owned = state.adsHidden,
                    price = state.removeAdsPrice,
                    state = state,
                    canPurchase = canPurchase,
                    highlighted = false,
                    onBuy = actions.onBuyRemoveAds,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                OptionCard(
                    tag = PAYWALL_PRO_TAG,
                    title = R.string.paywall_pro_title,
                    features = listOf(
                        R.string.paywall_pro_includes,
                        R.string.paywall_pro_feature_vehicles,
                        R.string.paywall_pro_feature_attachments,
                        R.string.paywall_pro_feature_charts,
                        R.string.paywall_pro_feature_pdf,
                    ),
                    owned = state.ownsPro,
                    price = state.proPrice,
                    state = state,
                    canPurchase = canPurchase,
                    highlighted = true,
                    onBuy = actions.onBuyPro,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            if (state.pending) Text(stringResource(R.string.pro_pending), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = actions.onRestore,
                modifier = Modifier.fillMaxWidth().testTag(PAYWALL_RESTORE_TAG),
            ) { Text(stringResource(R.string.pro_restore)) }
            Text(
                stringResource(R.string.paywall_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OptionCard(
    tag: String,
    title: Int,
    features: List<Int>,
    owned: Boolean,
    price: String?,
    state: PaywallState,
    canPurchase: Boolean,
    highlighted: Boolean,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag(tag),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            features.forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.weight(1f))
            when {
                owned -> Text(stringResource(R.string.paywall_owned), style = MaterialTheme.typography.titleSmall)
                price == null && state.unavailable ->
                    Text(stringResource(R.string.pro_unavailable), style = MaterialTheme.typography.bodySmall)
                else -> Button(
                    onClick = onBuy,
                    enabled = price != null && canPurchase && !state.pending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(price?.let { stringResource(R.string.paywall_buy, it) } ?: stringResource(R.string.pro_loading))
                }
            }
        }
    }
}
