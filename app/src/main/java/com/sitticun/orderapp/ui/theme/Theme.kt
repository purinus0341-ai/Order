package com.sitticun.orderapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PastelMint,
    onPrimary = CardWhite,
    primaryContainer = PastelMintLight,
    onPrimaryContainer = PastelMintDark,
    secondary = PastelPeach,
    onSecondary = CardWhite,
    secondaryContainer = PastelPeachLight,
    onSecondaryContainer = PastelPeachDark,
    tertiary = PastelLavender,
    onTertiary = CardWhite,
    tertiaryContainer = PastelLavenderLight,
    onTertiaryContainer = PastelLavenderDark,
    background = PastelBackground,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantColor,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    error = StatusDanger,
    onError = CardWhite
)

@Composable
fun OrderAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // We enforce the light pastel design system as requested for the POS experience
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PastelBackground.toArgb()
            window.navigationBarColor = PastelBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
