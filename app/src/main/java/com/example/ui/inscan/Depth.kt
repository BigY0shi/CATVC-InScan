package com.example.ui.inscan

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Depth B (locked): depth means "you can press this", and nothing else gets it.
// Inner edges are 1 dp lines (the mockup's unblurred inset shadows); inner shadows are short
// vertical gradients clipped to the shape; the outer drop is a few stacked offset fills, which
// stays crisp on charcoal and renders the same in screenshot tests and on a phone.

private fun DrawScope.rounded(r: Dp): Path = Path().apply {
    addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(r.toPx())))
}

private fun DrawScope.drop(r: Dp, y: Dp, blur: Dp, color: Color) {
    val steps = 4
    for (i in 1..steps) {
        val spread = blur.toPx() * i / steps
        drawRoundRect(
            color = color.copy(alpha = color.alpha / steps),
            topLeft = Offset(-spread / 2, y.toPx() - spread / 4),
            size = Size(size.width + spread, size.height + spread / 2),
            cornerRadius = CornerRadius(r.toPx() + spread / 2),
        )
    }
}

private fun DrawScope.innerShadow(r: Dp, depth: Dp, color: Color) {
    clipPath(rounded(r)) {
        drawRect(Brush.verticalGradient(listOf(color, Color.Transparent), endY = depth.toPx() * 2), size = Size(size.width, depth.toPx() * 2))
    }
}

private fun DrawScope.innerEdges(r: Dp, top: Color, bottom: Color, topW: Dp = 1.dp, bottomW: Dp = 1.dp) {
    clipPath(rounded(r)) {
        drawRect(top, size = Size(size.width, topW.toPx()))
        drawRect(bottom, topLeft = Offset(0f, size.height - bottomW.toPx()), size = Size(size.width, bottomW.toPx()))
    }
}

private fun DrawScope.border(r: Dp, color: Color) {
    val w = 1.dp.toPx()
    drawRoundRect(color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(r.toPx()), style = Stroke(w))
}

// Raised tactile control (tiles, count cards, notice-card buttons, Find, signed-in chip).
fun Modifier.raised(pressed: Boolean, radius: Dp = Shapes.radiusCard, enabled: Boolean = true): Modifier =
    drawBehind {
        val r = CornerRadius(radius.toPx())
        when {
            !enabled -> {
                drawRoundRect(Palette.surface, cornerRadius = r)
                border(radius, Palette.divider)
            }
            pressed -> {
                drawRoundRect(DepthTokens.pressedFill, cornerRadius = r)
                innerShadow(radius, 2.dp, DepthTokens.pressedShadow)
                border(radius, Palette.borderStrong)
            }
            else -> {
                drop(radius, 2.dp, 3.dp, DepthTokens.drop)
                drawRoundRect(Brush.verticalGradient(listOf(DepthTokens.raisedTop, DepthTokens.raisedBottom)), cornerRadius = r)
                innerEdges(radius, DepthTokens.highlight, DepthTokens.shade)
                border(radius, Palette.borderStrong)
            }
        }
    }

// The one filled emerald control besides Scan: primary buttons in flows (Review, Try again).
fun Modifier.primaryFill(pressed: Boolean, radius: Dp = Shapes.radiusCard, enabled: Boolean = true): Modifier =
    drawBehind {
        val r = CornerRadius(radius.toPx())
        if (!enabled) {
            drawRoundRect(Palette.surfaceRaised, cornerRadius = r)
            border(radius, Palette.divider)
        } else if (pressed) {
            drawRoundRect(Roles.accentPressed, cornerRadius = r)
            innerShadow(radius, 2.dp, DepthTokens.pressedShadow)
        } else {
            drop(radius, 2.dp, 3.dp, DepthTokens.drop)
            drawRoundRect(Roles.accent, cornerRadius = r)
            innerEdges(radius, DepthTokens.primaryHighlight, DepthTokens.primaryShade, bottomW = 2.dp)
        }
    }

// Inset well: the search field and typed-entry fields.
fun Modifier.well(radius: Dp = Shapes.radiusCard, focused: Boolean = false): Modifier =
    drawBehind {
        drawRoundRect(DepthTokens.wellFill, cornerRadius = CornerRadius(radius.toPx()))
        innerShadow(radius, 2.dp, DepthTokens.wellShadow)
        innerEdges(radius, Color.Transparent, DepthTokens.wellHighlight)
        border(radius, if (focused) Roles.accentInk else Palette.borderStrong)
    }

// Interactive chips and pills: their tint plus a 1 dp highlight and shade, no outer shadow.
fun Modifier.chipDepth(fill: Color, radius: Dp, bordered: Boolean = false): Modifier =
    drawBehind {
        drawRoundRect(fill, cornerRadius = CornerRadius(radius.toPx()))
        innerEdges(radius, DepthTokens.chipHighlight, DepthTokens.chipShade)
        if (bordered) border(radius, Palette.borderStrong)
    }

// The raised Scan button: radial emerald, 2 dp top highlight, bottom shade, outer drop, 4 dp bg ring.
fun Modifier.scanButton(pressed: Boolean): Modifier = drawWithContent {
    val ring = 4.dp.toPx()
    val r = size.minDimension / 2
    val c = center
    drawCircle(Palette.bg, radius = r + ring, center = c)
    if (pressed) {
        drawCircle(Roles.accentPressed, radius = r, center = c)
    } else {
        for (i in 1..4) drawCircle(Color.Black.copy(alpha = 0.5f / 4), radius = r + 6.dp.toPx() * i / 8, center = c + Offset(0f, 3.dp.toPx()))
        drawCircle(Brush.radialGradient(listOf(Roles.accentHi, Roles.accent, Roles.accentLo), center = Offset(c.x, c.y - r * 0.6f), radius = r * 1.7f), radius = r, center = c)
        drawArc(DepthTokens.primaryHighlight.copy(alpha = 0.35f), 200f, 140f, false, topLeft = Offset(c.x - r + 1.dp.toPx(), c.y - r + 1.dp.toPx()),
            size = Size(2 * r - 2.dp.toPx(), 2 * r - 2.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawArc(Color.Black.copy(alpha = 0.25f), 20f, 140f, false, topLeft = Offset(c.x - r + 2.dp.toPx(), c.y - r + 2.dp.toPx()),
            size = Size(2 * r - 4.dp.toPx(), 2 * r - 4.dp.toPx()), style = Stroke(3.dp.toPx()))
    }
    drawContent()
}
