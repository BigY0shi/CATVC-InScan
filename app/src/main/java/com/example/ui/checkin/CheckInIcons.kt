package com.example.ui.checkin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Symbols "barcode" and "barcode_scanner" aren't in the Compose icon set, so they're drawn
// here on the same 24 grid: plain bars, and bars inside scan corners.
private val BARS = listOf(2f to 1.5f, 4.5f to 1f, 6.5f to 2f, 9.5f to 1f, 11.5f to 1.5f, 14f to 1f, 16f to 2f, 19f to 1f, 21f to 1f)

object CheckInIcons {
    val Barcode: ImageVector by lazy {
        ImageVector.Builder("Barcode", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                BARS.forEach { (x, w) -> moveTo(x, 5f); lineTo(x + w, 5f); lineTo(x + w, 19f); lineTo(x, 19f); close() }
            }
        }.build()
    }

    val BarcodeScanner: ImageVector by lazy {
        ImageVector.Builder("BarcodeScanner", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // Corners.
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    moveTo(x, y); lineTo(x + 5f * dx, y); lineTo(x + 5f * dx, y + 1.6f * dy); lineTo(x + 1.6f * dx, y + 1.6f * dy)
                    lineTo(x + 1.6f * dx, y + 5f * dy); lineTo(x, y + 5f * dy); close()
                }
                corner(1f, 2f, 1f, 1f); corner(23f, 2f, -1f, 1f); corner(1f, 22f, 1f, -1f); corner(23f, 22f, -1f, -1f)
                // Bars.
                listOf(5.5f to 1.5f, 8f to 1f, 10f to 2f, 13f to 1f, 15f to 1.5f, 17.5f to 1f).forEach { (x, w) ->
                    moveTo(x, 7f); lineTo(x + w, 7f); lineTo(x + w, 17f); lineTo(x, 17f); close()
                }
            }
        }.build()
    }
}
