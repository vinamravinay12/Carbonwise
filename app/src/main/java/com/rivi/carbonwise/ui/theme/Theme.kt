package com.rivi.carbonwise.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE0),
    onPrimaryContainer = EmeraldDark,
    secondary = InkGreen,
    onSecondary = Color.White,
    tertiary = Citron,
    onTertiary = InkGreen,
    background = PaperLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = MutedLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = BandHigh,
)

private val DarkColors = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = InkGreen,
    primaryContainer = Color(0xFF12453A),
    onPrimaryContainer = Color(0xFFB9F2DD),
    secondary = OnSurfaceDark,
    onSecondary = InkGreen,
    tertiary = Citron,
    onTertiary = InkGreen,
    background = PaperDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = MutedDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = BandHigh,
)

@Composable
fun CarbonWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Edge-to-edge (enableEdgeToEdge) already makes the bars transparent; here we
            // only match the status-bar icon contrast to the theme.
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
