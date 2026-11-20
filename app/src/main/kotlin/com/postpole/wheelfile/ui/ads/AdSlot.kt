package com.postpole.wheelfile.ui.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.postpole.wheelfile.ads.AdLoadState
import com.postpole.wheelfile.ads.AdScreen
import com.postpole.wheelfile.ads.AdSlotState
import com.postpole.wheelfile.ads.AdSlotViewModel
import com.postpole.wheelfile.ads.AdUnits
import com.postpole.wheelfile.ui.theme.LocalWheelfileColors
import com.postpole.wheelfile.ui.theme.Sizes
import com.postpole.wheelfile.ui.theme.WheelfileTheme

/**
 * Anchored adaptive banner for [screen], sized for the screen width and laid out above the gesture inset.
 * Reserves [Sizes.adSlotTotal] while loading, collapses to 0 when there is no fill, and takes 0 height
 * (and never requests an ad) when ads are hidden, consent disallows requests or Remote Config disables banners.
 *
 * [onHeightChange] reports the slot's current height (without the gesture inset) so a FAB can sit
 * [Sizes.fabGap] above it: see [AdSlotState.fabBottomOffset]. It reports 0 when the slot leaves composition.
 */
@Composable
fun AdSlot(
    screen: AdScreen,
    modifier: Modifier = Modifier,
    onHeightChange: (Dp) -> Unit = {},
    viewModel: AdSlotViewModel = hiltViewModel(),
) {
    val eligible by viewModel.eligible.collectAsStateWithLifecycle()
    AdSlotContent(screen, eligible, modifier, onHeightChange)
}

@Composable
private fun AdSlotContent(
    screen: AdScreen,
    eligible: Boolean,
    modifier: Modifier,
    onHeightChange: (Dp) -> Unit,
) {
    // A change of eligibility or screen starts a fresh load (and so a fresh reserved height).
    var loadState by remember(screen, eligible) { mutableStateOf(AdLoadState.LOADING) }
    var loadedAdHeight by remember(screen, eligible) { mutableStateOf(Sizes.adSlotHeight) }
    val height = AdSlotState.height(eligible, loadState, loadedAdHeight)

    LaunchedEffect(height) { onHeightChange(height) }
    DisposableEffect(Unit) { onDispose { onHeightChange(0.dp) } }

    if (height <= 0.dp) return

    BoxWithConstraints(modifier.fillMaxWidth().navigationBarsPadding()) {
        val widthDp = maxWidth.value.toInt()
        val context = LocalContext.current
        val adView = if (LocalInspectionMode.current) {
            null
        } else {
            remember(screen, widthDp) {
                AdView(context).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                    adUnitId = AdUnits.banner(screen)
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            loadedAdHeight = (adSize?.height ?: Sizes.adSlotHeight.value.toInt()).dp
                            loadState = AdLoadState.LOADED
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            loadState = AdLoadState.NO_FILL
                        }
                    }
                }
            }
        }
        if (adView != null) {
            LaunchedEffect(adView) { adView.loadAd(AdRequest.Builder().build()) }
            DisposableEffect(adView) { onDispose { adView.destroy() } }
            LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { adView.pause() }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { adView.resume() }
        }
        AdSlotFrame(height = height) {
            if (adView != null) AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The slot chrome: a 1 dp hairline over the ad area, on the `adSlotContainer` surface. */
@Composable
private fun AdSlotFrame(height: Dp, content: @Composable () -> Unit) {
    val colors = LocalWheelfileColors.current
    Column(Modifier.fillMaxWidth().height(height)) {
        HorizontalDivider(thickness = Sizes.adSlotHairline, color = colors.adSlotOutline)
        Column(
            Modifier.fillMaxWidth().height(height - Sizes.adSlotHairline).background(colors.adSlotContainer),
        ) { content() }
    }
}

@Preview(name = "Banner slot: loading (reserved)", widthDp = 360)
@Composable
private fun AdSlotLoadingPreview() {
    WheelfileTheme(darkTheme = false) { AdSlotFrame(height = Sizes.adSlotTotal) {} }
}

@Preview(name = "Banner slot: loading, dark", widthDp = 360)
@Composable
private fun AdSlotLoadingDarkPreview() {
    WheelfileTheme(darkTheme = true) { AdSlotFrame(height = Sizes.adSlotTotal) {} }
}
