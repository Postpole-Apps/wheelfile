package com.postpole.wheelfile.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.postpole.wheelfile.ui.theme.LocalWheelfileColors
import com.postpole.wheelfile.ui.theme.Sizes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class AdSlotViewModel @Inject constructor(controller: AdsController) : ViewModel() {
    val bannersAllowed: Flow<Boolean> = controller.bannersAllowed
}

/**
 * Anchored adaptive banner for [screen], sized for the screen width and laid out above the gesture inset.
 *
 * Reserves [Sizes.adSlotTotal] (50 dp + 1 dp hairline) while loading, collapses to 0 on no fill
 * (`onAdFailedToLoad`) and when ads are hidden. [onHeightChange] reports the current slot height
 * (excluding the gesture inset) so a FAB can sit 16 dp above it (DECISIONS.md items 3-5).
 * No ad is requested, and no AdView exists, unless consent, Remote Config and entitlements all allow it.
 */
@Composable
fun AdSlot(
    screen: AdScreen,
    modifier: Modifier = Modifier,
    onHeightChange: (Dp) -> Unit = {},
    viewModel: AdSlotViewModel = hiltViewModel(),
) {
    val allowed by viewModel.bannersAllowed.collectAsStateWithLifecycle(initialValue = false)
    AdSlotContent(screen, allowed, modifier, onHeightChange)
}

@Composable
internal fun AdSlotContent(
    screen: AdScreen,
    allowed: Boolean,
    modifier: Modifier = Modifier,
    onHeightChange: (Dp) -> Unit = {},
) {
    // A fresh request cycle whenever the gate flips (e.g. consent granted, Remove ads bought).
    var load by remember(allowed, screen) { mutableStateOf(BannerLoad.LOADING) }
    var loadedAdHeight by remember(allowed, screen) { mutableStateOf(Sizes.adSlotHeight) }
    val height = AdSlotState.height(allowed, load, loadedAdHeight)

    DisposableEffect(height) {
        onHeightChange(height)
        onDispose {}
    }
    DisposableEffect(Unit) { onDispose { onHeightChange(0.dp) } }

    if (height <= 0.dp) return

    val colors = LocalWheelfileColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.adSlotContainer)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(Sizes.adSlotHairline).background(colors.adSlotOutline))
        BoxWithConstraints(Modifier.fillMaxWidth().height(height - Sizes.adSlotHairline)) {
            val context = LocalContext.current
            val widthDp = maxWidth.value.toInt()
            var adView by remember { mutableStateOf<AdView?>(null) }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            DisposableEffect(lifecycle, adView) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> adView?.pause()
                        Lifecycle.Event.ON_RESUME -> adView?.resume()
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            AndroidView(
                factory = { ctx ->
                    createBanner(ctx, screen, widthDp) { result, adHeightDp ->
                        load = result
                        adHeightDp?.let { loadedAdHeight = it.dp }
                    }.also { adView = it }
                },
                onRelease = { (it as? AdView)?.destroy() },
            )
        }
    }
}

private fun createBanner(
    context: Context,
    screen: AdScreen,
    widthDp: Int,
    onResult: (BannerLoad, Int?) -> Unit,
): AdView {
    val adView = AdView(context)
    val size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    adView.setAdSize(size)
    adView.adUnitId = AdUnits.current.banner(screen)
    adView.adListener = object : AdListener() {
        override fun onAdLoaded() = onResult(BannerLoad.LOADED, size.height)
        override fun onAdFailedToLoad(error: LoadAdError) = onResult(BannerLoad.NO_FILL, null)
    }
    adView.loadAd(AdRequest.Builder().build())
    return adView
}
