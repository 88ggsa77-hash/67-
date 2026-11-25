package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ViaDarkColorScheme = darkColorScheme(
    primary = ViaDarkPrimary,
    onPrimary = ViaDarkOnPrimary,
    primaryContainer = ViaDarkPrimaryContainer,
    onPrimaryContainer = ViaDarkOnPrimaryContainer,
    secondary = ViaDarkSecondary,
    onSecondary = ViaDarkOnSecondary,
    background = ViaDarkBackground,
    onBackground = ViaDarkOnBackground,
    surface = ViaDarkSurface,
    onSurface = ViaDarkOnSurface,
    surfaceVariant = ViaDarkSurfaceVariant,
    onSurfaceVariant = ViaDarkOnSurfaceVariant,
    outline = ViaDarkOutline,
    error = ViaDarkError
)

private val ViaLightColorScheme = lightColorScheme(
    primary = ViaLightPrimary,
    onPrimary = ViaLightOnPrimary,
    primaryContainer = ViaLightPrimaryContainer,
    onPrimaryContainer = ViaLightOnPrimaryContainer,
    secondary = ViaLightSecondary,
    onSecondary = ViaLightOnSecondary,
    background = ViaLightBackground,
    onBackground = ViaLightOnBackground,
    surface = ViaLightSurface,
    onSurface = ViaLightOnSurface,
    surfaceVariant = ViaLightSurfaceVariant,
    onSurfaceVariant = ViaLightOnSurfaceVariant,
    outline = ViaLightOutline,
    error = ViaLightError
)

@Composable
fun ViaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ViaDarkColorScheme else ViaLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ViaTypography,
        content = content
    )
}
