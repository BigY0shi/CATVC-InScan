package com.example.label

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.countryatv.core.Label
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelSpec
import com.countryatv.core.MonoImage

// Draws a whole tag at the printer's resolution: the QR code on the left, the details in words on
// the right. The result is pure black and white, ready for a thermal printer or for sharing.
object LabelRenderer {
    // Smallest text worth printing; below this the words are dropped from the end rather than
    // printed unreadably small. The QR code always carries everything.
    private const val MIN_TEXT_MM = 1.6

    fun render(content: LabelContent, spec: LabelSpec): Result<Bitmap> {
        val (qrImage, qr) = Label.drawQr(content, spec).getOrElse { return Result.failure(it) }
        val bitmap = Bitmap.createBitmap(spec.widthDots, spec.heightDots, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 0 until qrImage.height) for (x in 0 until qrImage.width) {
            if (qrImage[x, y]) bitmap.setPixel(x, y, Color.BLACK)
        }

        val textLeft = qr.left + qr.sizeDots + Label.QUIET_ZONE_MODULES * qr.dotsPerModule
        val textRight = spec.widthDots - spec.mmToDots(1.0)
        val textWidth = textRight - textLeft
        val lines = Label.lines(content)
        if (textWidth > 0) {
            val layout = layoutText(lines, textWidth.toFloat(), spec)
            val canvas = Canvas(bitmap)
            var baseline = layout.firstBaseline
            for (row in layout.rows) {
                canvas.drawText(row, textLeft.toFloat(), baseline, layout.paint)
                baseline += layout.textSize * LINE
            }
        }
        return Result.success(threshold(bitmap))
    }

    class TextLayout(val rows: List<String>, val textSize: Float, val firstBaseline: Float, val paint: Paint)

    // Where the words go: the rows as printed and their size. Separate from drawing so tests can check it.
    fun layoutText(lines: List<String>, width: Float, spec: LabelSpec): TextLayout {
        val top = spec.mmToDots(1.5).toFloat()
        val bottom = spec.heightDots - spec.mmToDots(1.5).toFloat()
        val height = bottom - top
        val minSize = spec.mmToDots(MIN_TEXT_MM).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        // VIN and ID are only useful whole, so they never wrap or get cut; the text shrinks until
        // they fit. Other long lines wrap onto a second row, then get cut with "…".
        val exact = lines.filter { it.startsWith("VIN ") || it.startsWith("ID ") }.toSet()
        var size = height / lines.size / LINE
        var rows: List<String>
        while (true) {
            paint.textSize = size
            rows = lines.flatMap { if (it in exact) listOf(it) else wrap(it, paint, width) }
            val exactFits = exact.all { paint.measureText(it) <= width }
            if ((exactFits && rows.size * size * LINE <= height) || size <= minSize) break
            size = maxOf(minSize, size * 0.94f)
        }
        if (exact.any { paint.measureText(it) > width }) {
            // Never print a cut-off VIN or ID: leave it off; the QR code still carries it.
            rows = rows.filter { it !in exact || paint.measureText(it) <= width }
        }
        // Still too tall at the smallest size: drop detail rows from the end, but always keep the ID.
        while (rows.size > 2 && rows.size * size * LINE > height) rows = rows.dropLast(2) + rows.last()
        // Centre the block of text beside the QR code.
        return TextLayout(rows, size, top + (height - rows.size * size * LINE) / 2 + size, paint)
    }

    // Splits a line at spaces onto at most two rows; whatever still doesn't fit is cut with "…".
    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        if (paint.measureText(text) <= width) return listOf(text)
        val words = text.split(' ')
        if (words.size == 1) return listOf(ellipsize(text, paint, width))
        var firstEnd = words.size - 1
        while (firstEnd > 1 && paint.measureText(words.take(firstEnd).joinToString(" ")) > width) firstEnd--
        val first = words.take(firstEnd).joinToString(" ")
        val rest = words.drop(firstEnd).joinToString(" ")
        return if (rest.isEmpty()) listOf(ellipsize(first, paint, width)) else listOf(ellipsize(first, paint, width), ellipsize(rest, paint, width))
    }

    private const val LINE = 1.18f

    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > width) end--
        return text.substring(0, end) + "…"
    }

    // Thermal printers print dots, not greys: anything darker than mid-grey becomes a dot.
    private fun threshold(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val pixels = IntArray(w * bitmap.height)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, bitmap.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            pixels[i] = if (lum < 128) Color.BLACK else Color.WHITE
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, bitmap.height)
        return bitmap
    }

    // The same tag as printer dots (true = black), for printer drivers.
    fun toMono(bitmap: Bitmap): MonoImage {
        val image = MonoImage(bitmap.width, bitmap.height)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) image[i % bitmap.width, i / bitmap.width] = pixels[i] == Color.BLACK
        return image
    }
}
