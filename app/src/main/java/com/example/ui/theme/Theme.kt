package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val UnifiedCinematicColorScheme = darkColorScheme(
    primary = CountryOrange,
    secondary = CountryAmber,
    tertiary = MutedBone,
    background = CinematicBlack,
    surface = CinematicCharcoalDark,
    surfaceVariant = CinematicCharcoalMedium,
    onPrimary = BoneWhite,
    onSecondary = CinematicBlack,
    onTertiary = CinematicBlack,
    onBackground = BoneWhite,
    onSurface = BoneWhite,
    onSurfaceVariant = MutedBone,
    error = SmoothRed,
    onError = BoneWhite
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Always enforce our premium branding
    content: @Composable () -> Unit,
) {
    // We enforce the UnifiedCinematicColorScheme for all themes to deliver an uncompromising premium dark aesthetic.
    val colorScheme = UnifiedCinematicColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CinematicBlack.toArgb()
            window.navigationBarColor = CinematicBlack.toArgb()
            
            // Ensure status bar and navigation bar have light icons (white) on dark backgrounds
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
