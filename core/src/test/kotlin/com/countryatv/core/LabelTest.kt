package com.countryatv.core

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LabelTest {
    private val id = "01JABCDEFGHJKMNPQRSTVWXYZ0"

    // Reads a drawn label the way a scanner would: from pixels, not from our own encoder.
    private fun decode(image: MonoImage): String {
        val source = object : LuminanceSource(image.width, image.height) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray {
                val out = if (row != null && row.size >= width) row else ByteArray(width)
                for (x in 0 until width) out[x] = if (image[x, y]) 0 else 255.toByte()
                return out
            }
            override fun getMatrix(): ByteArray = ByteArray(width * height) { i -> if (image[i % width, i / width]) 0 else 255.toByte() }
        }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.PURE_BARCODE to false)).text
    }

    @Test
    fun `a drawn tag reads back as exactly the ID and details, with awkward characters intact`() {
        val content = LabelContent(
            id = id, make = "Can-Am", model = "Outlander; 850=XT", subModel = "Max 100% DPS", year = "2025",
            color = "Granite Gray", unitType = "ATV", vin = "3JBLGAT48SJ000123"
        )
        val (image, placement) = Label.drawQr(content, LabelSpec.DEFAULT).getOrThrow()
        assertTrue(placement.dotsPerModule >= Label.MIN_DOTS_PER_MODULE)
        val read = decode(image)
        assertEquals(LabelPayload.encode(content), read)
        assertEquals(content, LabelPayload.decode(read))
    }

    @Test
    fun `the words on the tag never include price or status`() {
        val lines = Label.lines(LabelContent(id = id, make = "Honda", model = "CRF450R", year = "2025", color = "Red", unitType = "Dirt Bike", vin = "1M8GDM9AXKP042788"))
        assertEquals(listOf("2025 Honda CRF450R", "Red", "Dirt Bike", "VIN 1M8GDM9AXKP042788", "ID ${id.takeLast(8)}"), lines)
        assertTrue(lines.none { it.contains("$") })
    }

    @Test
    fun `a label too small for a readable QR code is refused, not printed tiny`() {
        val tiny = LabelSpec(widthMm = 15.0, heightMm = 6.0, dpi = 203)
        val result = Label.drawQr(LabelContent(id = id, make = "Honda", model = "CRF450R"), tiny)
        val error = result.exceptionOrNull()
        assertIs<LabelTooSmall>(error)
        assertTrue(error.message!!.contains("15 x 6 mm"))
    }

    // Sizes that decide which printer and stock work. A fully filled-in tag is about 146 characters
    // (a 49 x 49 QR code): it needs about 21.4 mm of label height at 203 dpi, 24.1 mm at 180 dpi.
    @Test
    fun `a fully filled-in tag fits 50 x 30 mm at 203 dpi and 36 mm tape at 180 dpi, not 24 mm tape`() {
        val full = LabelContent(
            id = id, make = "Can-Am", model = "Outlander MAX XT-P 850", subModel = "DPS", year = "2025",
            color = "Granite Gray", unitType = "Side-by-Side (UTV)", vin = "3JBLGAT48SJ000123"
        )
        for (spec in listOf(LabelSpec.DEFAULT, LabelSpec(widthMm = 70.0, heightMm = 36.0, dpi = 180))) {
            val (image, _) = Label.drawQr(full, spec).getOrThrow()
            assertEquals(LabelPayload.encode(full), decode(image))
        }
        assertIs<LabelTooSmall>(Label.drawQr(full, LabelSpec(widthMm = 70.0, heightMm = 24.0, dpi = 180)).exceptionOrNull())
    }
}
