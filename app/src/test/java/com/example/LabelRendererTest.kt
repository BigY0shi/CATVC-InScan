package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import com.countryatv.core.LabelSpec
import com.example.label.LabelRenderer
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Real drawing (Robolectric's native graphics), then read back from the pixels like a scanner would.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LabelRendererTest {
    private fun decode(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun `the printed tag is pure black and white, reads back exactly, and has the words beside the code`() {
        val content = LabelContent(
            id = ItemId.new(), make = "Can-Am", model = "Outlander MAX XT-P 850", subModel = "DPS", year = "2025",
            color = "Granite Gray", unitType = "Side-by-Side (UTV)", vin = "3JBLGAT48SJ000123"
        )
        val spec = LabelSpec.DEFAULT
        val bitmap = LabelRenderer.render(content, spec).getOrThrow()
        assertEquals(spec.widthDots, bitmap.width)
        assertEquals(spec.heightDots, bitmap.height)

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.all { it == Color.BLACK || it == Color.WHITE })

        assertEquals(LabelPayload.encode(content), decode(bitmap))

        // Words were drawn in the right half (where the QR code never goes).
        val mono = LabelRenderer.toMono(bitmap)
        var textDots = 0
        for (y in 0 until mono.height) for (x in mono.width * 6 / 10 until mono.width) if (mono[x, y]) textDots++
        assertTrue("expected text beside the QR code", textDots > 200)
    }

    @Test
    fun `the VIN and ID are printed whole or not at all, never cut off`() {
        val id = ItemId.new()
        val lines = com.countryatv.core.Label.lines(
            LabelContent(id = id, make = "Can-Am", model = "Outlander MAX XT-P 850", subModel = "DPS", year = "2025",
                color = "Granite Gray", unitType = "Side-by-Side (UTV)", vin = "3JBLGAT48SJ000123")
        )
        val roomy = LabelRenderer.layoutText(lines, 170f, LabelSpec.DEFAULT)
        assertTrue("VIN 3JBLGAT48SJ000123" in roomy.rows)
        assertTrue("ID ${id.takeLast(8)}" in roomy.rows)
        // Far too narrow: the VIN can't fit whole, so it is left off rather than cut.
        val cramped = LabelRenderer.layoutText(lines, 60f, LabelSpec.DEFAULT)
        assertTrue(cramped.rows.none { it.startsWith("VIN") || it.startsWith("ID") && it != "ID ${id.takeLast(8)}" })
        assertTrue(cramped.rows.none { it.startsWith("VIN") && it.endsWith("…") })
    }

    @Test
    fun `a label too small is refused with a plain reason`() {
        val result = LabelRenderer.render(LabelContent(id = ItemId.new(), make = "Honda", model = "CRF450R"), LabelSpec(15.0, 6.0, 203))
        assertTrue(result.exceptionOrNull()!!.message!!.contains("label at least"))
    }
}
