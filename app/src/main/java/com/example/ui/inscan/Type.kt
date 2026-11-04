package com.example.ui.inscan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

// Bundled free fonts (SIL OFL): Barlow Semi Condensed for headings and big numbers,
// Atkinson Hyperlegible Next for UI text, Atkinson Hyperlegible Mono for IDs and VINs.
val Barlow = FontFamily(Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold))
val Atkinson = FontFamily(
    Font(R.font.atkinson_next_regular, FontWeight.Normal),
    Font(R.font.atkinson_next_medium, FontWeight.Medium),
    Font(R.font.atkinson_next_semibold, FontWeight.SemiBold),
    Font(R.font.atkinson_next_bold, FontWeight.Bold),
)
val AtkinsonMono = FontFamily(
    Font(R.font.atkinson_mono_medium, FontWeight.Medium),
    Font(R.font.atkinson_mono_bold, FontWeight.Bold),
)

private fun style(family: FontFamily, weight: FontWeight, size: Int, line: Int) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

// Type scale (landing-nav-ui-prompt.md section 6). Nothing is smaller than 14 sp.
object InType {
    val screenTitle = style(Barlow, FontWeight.Bold, 28, 34)
    val appTitle = style(Barlow, FontWeight.Bold, 22, 28)
    val section = style(Barlow, FontWeight.Bold, 21, 28)
    val bigNumber = style(Barlow, FontWeight.Bold, 28, 32)
    val noticeTitle = style(Barlow, FontWeight.Bold, 20, 26)
    val key = style(Barlow, FontWeight.Bold, 44, 48)
    val cardTitle = style(Atkinson, FontWeight.SemiBold, 17, 24)
    val tileLabel = style(Atkinson, FontWeight.SemiBold, 17, 22)
    val body = style(Atkinson, FontWeight.Normal, 17, 24)
    val label = style(Atkinson, FontWeight.Medium, 15, 20)
    val tag = style(Atkinson, FontWeight.Medium, 14, 20)
    val meta = style(Atkinson, FontWeight.Normal, 14, 20)
    val id = style(AtkinsonMono, FontWeight.Medium, 15, 20)
    val idPlate = style(AtkinsonMono, FontWeight.Bold, 15, 20)
    val fieldMono = style(AtkinsonMono, FontWeight.Medium, 22, 28)
}

// Nav labels grow with the system font size up to 18 sp, then stop. The cap is expressed in dp
// so it holds under Android 14+ nonlinear font scaling.
@Composable
@ReadOnlyComposable
fun navLabelSize(): TextUnit {
    val density = LocalDensity.current
    val scaled = 14f * density.fontScale
    return with(density) { (minOf(scaled, 18f)).dp.toSp() }
}
