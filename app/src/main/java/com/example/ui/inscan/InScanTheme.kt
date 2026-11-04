package com.example.ui.inscan

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Material 3 components (dialogs, sheets, snackbars) drawn in the InScan palette, so anything
// built on M3 lands on the same charcoal and emerald as the custom components.
private val scheme = darkColorScheme(
    primary = Roles.accent,
    onPrimary = Roles.onAccent,
    primaryContainer = Roles.accentTint,
    onPrimaryContainer = Roles.accentInk,
    secondary = Roles.accentInk,
    onSecondary = Palette.bg,
    background = Palette.bg,
    onBackground = Palette.text,
    surface = Palette.surface,
    onSurface = Palette.text,
    surfaceVariant = Palette.surfaceRaised,
    onSurfaceVariant = Palette.text2,
    surfaceContainer = Palette.surface,
    surfaceContainerHigh = Palette.surfaceRaised,
    surfaceContainerHighest = Palette.surfaceRaised,
    surfaceContainerLow = Palette.surface,
    inverseSurface = Palette.text,
    inverseOnSurface = Palette.bg,
    outline = Palette.borderStrong,
    outlineVariant = Palette.divider,
    error = Roles.danger.ink,
    onError = Palette.bg,
    errorContainer = Roles.danger.tint,
    onErrorContainer = Palette.text,
)

private val typography = Typography(
    headlineMedium = InType.screenTitle,
    titleLarge = InType.appTitle,
    titleMedium = InType.cardTitle,
    bodyLarge = InType.body,
    bodyMedium = InType.meta,
    labelLarge = InType.label,
    labelMedium = InType.tag,
)

@Composable
fun InScanTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
