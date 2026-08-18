package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OpusDarkColorScheme = darkColorScheme(
    primary = OpusPrimaryViolet,
    onPrimary = OpusTextPrimary,
    primaryContainer = OpusDarkSurfaceHighlight,
    onPrimaryContainer = OpusVioletGlow,
    secondary = OpusElectricCyan,
    onSecondary = OpusDarkCanvas,
    secondaryContainer = OpusDarkSurfaceVariant,
    onSecondaryContainer = OpusElectricCyan,
    tertiary = OpusHotPink,
    background = OpusDarkCanvas,
    onBackground = OpusTextPrimary,
    surface = OpusDarkSurface,
    onSurface = OpusTextPrimary,
    surfaceVariant = OpusDarkSurfaceVariant,
    onSurfaceVariant = OpusTextSecondary,
    outline = OpusBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = OpusDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = OpusDarkCanvas.toArgb()
                window.navigationBarColor = OpusDarkCanvas.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
