package com.sitticun.orderapp.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val IosLightColorScheme = lightColorScheme(
    primary = IosBlue,
    onPrimary = CardWhite,
    primaryContainer = IosBlueLight,
    onPrimaryContainer = IosBlueDark,
    secondary = IosOrange,
    onSecondary = CardWhite,
    secondaryContainer = IosOrangeLight,
    onSecondaryContainer = IosOrangeDark,
    tertiary = IosIndigo,
    onTertiary = CardWhite,
    tertiaryContainer = IosIndigoLight,
    onTertiaryContainer = IosIndigoDark,
    background = IosGroupedBackground,
    onBackground = TextPrimary,
    surface = CardWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantColor,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    outlineVariant = IosSeparator,
    error = StatusDanger,
    onError = CardWhite
)

val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun OrderAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = IosLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = IosGroupedBackground.toArgb()
            window.navigationBarColor = CardWhite.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = IosShapes,
        content = content
    )
}
