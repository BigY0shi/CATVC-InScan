package com.countryatv.core

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

// The size of the label stock and the printer's resolution. Everything is laid out in printer
// dots so the QR modules land on whole dots and print crisp.
data class LabelSpec(val widthMm: Double, val heightMm: Double, val dpi: Int) {
    val widthDots: Int get() = mmToDots(widthMm)
    val heightDots: Int get() = mmToDots(heightMm)
    fun mmToDots(mm: Double): Int = Math.round(mm / 25.4 * dpi).toInt()

    companion object {
        // A common small thermal label: 50 x 30 mm at 203 dpi.
        val DEFAULT = LabelSpec(widthMm = 50.0, heightMm = 30.0, dpi = 203)
    }
}

// A black-and-white image, one bit per printer dot. This is what a label printer receives.
class MonoImage(val width: Int, val height: Int, private val bits: BooleanArray = BooleanArray(width * height)) {
    init {
        require(bits.size == width * height)
    }
    operator fun get(x: Int, y: Int): Boolean = bits[y * width + x]
    operator fun set(x: Int, y: Int, black: Boolean) { bits[y * width + x] = black }
    fun blackCount(): Int = bits.count { it }
}

// The square QR code for one tag, as modules (true = black), before scaling.
class QrModules(val size: Int, private val dark: Array<BooleanArray>) {
    operator fun get(x: Int, y: Int): Boolean = dark[y][x]
}

sealed class LabelProblem(val message: String) {
    class TooSmall(neededMm: Double, spec: LabelSpec) : LabelProblem(
        "This tag needs a label at least %.0f mm tall to print a QR code phones can read. The printer is set to %.0f x %.0f mm."
            .format(neededMm, spec.widthMm, spec.heightMm)
    )
}

// What goes on a printed tag: a QR code (item ID plus the entered details, never price, status or
// customer data) and the same details in words, so a person can read it without a phone.
object Label {
    // Each QR module is at least this many dots; smaller modules blur on thermal labels and phone cameras miss them.
    const val MIN_DOTS_PER_MODULE = 3
    // Blank border around the QR code, in modules (the QR standard asks for 4).
    const val QUIET_ZONE_MODULES = 4

    fun qr(content: LabelContent): QrModules {
        val code = Encoder.encode(
            LabelPayload.encode(content),
            ErrorCorrectionLevel.M,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
        )
        val m = code.matrix
        return QrModules(m.width, Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y).toInt() == 1 } })
    }

    // The human-readable lines, most important first. A short form of the ID is last, so a tag can
    // be looked up by typing it if the QR code gets damaged.
    fun lines(content: LabelContent): List<String> = buildList {
        add(listOf(content.year, content.make, content.model).filter { it.isNotBlank() }.joinToString(" "))
        listOf(content.subModel, content.color).filter { it.isNotBlank() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let(::add)
        content.unitType.takeIf { it.isNotBlank() }?.let(::add)
        content.vin.takeIf { it.isNotBlank() }?.let { add("VIN $it") }
        add("ID ${ItemId.shortForm(content.id)}")
    }

    // Where the QR code goes on the label: a square on the left, as tall as the label allows. The
    // quiet zone is on the label itself, since the box under a label edge may not be white.
    data class QrPlacement(val left: Int, val top: Int, val dotsPerModule: Int, val sizeDots: Int)

    fun placeQr(qr: QrModules, spec: LabelSpec): Result<QrPlacement> {
        val modulesWithQuiet = qr.size + 2 * QUIET_ZONE_MODULES
        // The QR code takes at most a little over half the width, leaving room for the words.
        val perModule = minOf(spec.heightDots, spec.widthDots * 11 / 20) / modulesWithQuiet
        if (perModule < MIN_DOTS_PER_MODULE) {
            val neededDots = modulesWithQuiet * MIN_DOTS_PER_MODULE
            return Result.failure(LabelTooSmall(LabelProblem.TooSmall(neededDots * 25.4 / spec.dpi, spec)))
        }
        val size = qr.size * perModule
        return Result.success(
            QrPlacement(left = QUIET_ZONE_MODULES * perModule, top = (spec.heightDots - size) / 2, dotsPerModule = perModule, sizeDots = size)
        )
    }

    // The QR code alone, drawn into a label-sized image. The app draws the text beside it.
    fun drawQr(content: LabelContent, spec: LabelSpec): Result<Pair<MonoImage, QrPlacement>> {
        val qr = qr(content)
        val placement = placeQr(qr, spec).getOrElse { return Result.failure(it) }
        val image = MonoImage(spec.widthDots, spec.heightDots)
        for (my in 0 until qr.size) for (mx in 0 until qr.size) {
            if (!qr[mx, my]) continue
            for (dy in 0 until placement.dotsPerModule) for (dx in 0 until placement.dotsPerModule) {
                image[placement.left + mx * placement.dotsPerModule + dx, placement.top + my * placement.dotsPerModule + dy] = true
            }
        }
        return Result.success(image to placement)
    }
}

class LabelTooSmall(val problem: LabelProblem) : Exception(problem.message)
