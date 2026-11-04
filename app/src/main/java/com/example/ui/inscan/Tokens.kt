package com.example.ui.inscan

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Palette v4 "true charcoal" (locked 2026-10-30). Values copied from
// docs/ui-rebuild/landing-nav-ui-prompt.md section 6 and prototypes/inscan-base.css, with the same
// names, so the mockups and the app can be compared token by token.
object Palette {
    val bg = Color(0xFF1E1F21)
    val surface = Color(0xFF28292C)
    val surfaceRaised = Color(0xFF323337)
    val borderStrong = Color(0xFF8C8F95)
    val divider = Color(0xFF3C3D41)
    val text = Color(0xFFF2F3F4)
    val text2 = Color(0xFFC6C9CD)
    val emerald = Color(0xFF10B981)
    val onEmerald = Color(0xFF06140E)
    val emeraldPressed = Color(0xFF0C9A6C)
    val emeraldInk = Color(0xFF45D9A1)
    val emeraldTint = Color(0xFF16493A)
    val emeraldHi = Color(0xFF12D091)
    val emeraldLo = Color(0xFF0EA171)
    val oceanInk = Color(0xFF79BEEC)
    val oceanTint = Color(0xFF203C50)
    val amberInk = Color(0xFFF4BC50)
    val amberTint = Color(0xFF4D3B18)
    val rustInk = Color(0xFFF9907A)
    val rustTint = Color(0xFF4E2721)
    val stoneInk = Color(0xFFDADDE1)
    val stoneTint = Color(0xFF4A4D53)
    // Thermal tag stock, for tag previews only.
    val paper = Color(0xFFFAFAF7)
}

// Role tokens: components use these, never a raw hue, so a re-brand is one block of values.
object Roles {
    val accent = Palette.emerald
    val onAccent = Palette.onEmerald
    val accentPressed = Palette.emeraldPressed
    val accentInk = Palette.emeraldInk
    val accentTint = Palette.emeraldTint
    val accentHi = Palette.emeraldHi
    val accentLo = Palette.emeraldLo
    val good = Tone(Palette.emeraldInk, Palette.emeraldTint)
    val info = Tone(Palette.oceanInk, Palette.oceanTint)
    val warn = Tone(Palette.amberInk, Palette.amberTint)
    val danger = Tone(Palette.rustInk, Palette.rustTint)
    val neutral = Tone(Palette.stoneInk, Palette.stoneTint)
}

// An ink (text and icons on charcoal) and its tint (badge and banner fills).
data class Tone(val ink: Color, val tint: Color)

// Depth B: tactile controls only (locked). See Depth.kt for how these are drawn.
object DepthTokens {
    val raisedTop = Color(0xFF3A3B40)
    val raisedBottom = Color(0xFF2E2F33)
    val pressedFill = Color(0xFF2A2B2F)
    val wellFill = Color(0xFF18191B)
    val highlight = Color.White.copy(alpha = 0.10f)
    val shade = Color.Black.copy(alpha = 0.35f)
    val drop = Color.Black.copy(alpha = 0.45f)
    val pressedShadow = Color.Black.copy(alpha = 0.45f)
    val wellShadow = Color.Black.copy(alpha = 0.55f)
    val wellHighlight = Color.White.copy(alpha = 0.06f)
    val chipHighlight = Color.White.copy(alpha = 0.08f)
    val chipShade = Color.Black.copy(alpha = 0.30f)
    val primaryHighlight = Color.White.copy(alpha = 0.28f)
    val primaryShade = Color.Black.copy(alpha = 0.18f)
    // Over-camera plates and the sign-in scrim: solid charcoal at 80%, no blur.
    val overCamera = Color(0xCC1E1F21)
    val scrimModal = Color.Black.copy(alpha = 0.62f)
}

object Shapes {
    val radiusCard = 12.dp
    val radiusImg = 10.dp
    val radiusPlate = 4.dp
    val radiusSheet = 16.dp
    val radiusNotice = 16.dp
}

object Space {
    val gutter = 16.dp
    val gutterTablet = 24.dp
    val section = 24.dp
    val item = 12.dp
    val card = 16.dp
    val touch = 48.dp
    val touchPrimary = 56.dp
}

object Motion {
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val out = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    const val pressMs = 80
    const val scaleMs = 120
    const val sheetMs = 200
}
