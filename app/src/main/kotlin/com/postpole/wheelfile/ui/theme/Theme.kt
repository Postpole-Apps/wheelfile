// Tokens from design/tokens/Theme.kt — Wheelfile Direction 4 "Service-Book Ochre" (locked 20 Nov 2026).
// Dynamic colour is intentionally not offered: the ochre brand stays consistent with the icon and store art.
package com.postpole.wheelfile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun WheelfileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) WheelfileDarkColors else WheelfileLightColors
    val extended = if (darkTheme) WheelfileDarkExtended else WheelfileLightExtended
    CompositionLocalProvider(LocalWheelfileColors provides extended, LocalWheelfileType provides WheelfileTypeExtras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WheelfileTypography,
            shapes = WheelfileShapes,
            content = content,
        )
    }
}

/** Access: WheelfileTheme.colors.warningContainer, WheelfileTheme.type.numberHero */
object WheelfileTheme {
    val colors: WheelfileExtendedColors
        @Composable @ReadOnlyComposable get() = LocalWheelfileColors.current
    val type: WheelfileTypeExtrasSet
        @Composable @ReadOnlyComposable get() = LocalWheelfileType.current
}
