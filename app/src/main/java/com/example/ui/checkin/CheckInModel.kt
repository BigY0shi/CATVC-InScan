package com.example.ui.checkin

import com.countryatv.core.Money
import com.countryatv.core.MoneyResult
import com.countryatv.core.UnitType
import com.countryatv.core.Vin
import com.countryatv.core.VinCheck
import com.example.shop.NewUnit
import com.example.ui.inscan.shopWord
import java.io.Serializable
import java.time.LocalDate

// Check in a unit (checkin-ui-spec.md). Everything here is plain logic, so the rules can be
// tested without a screen: the draft, VIN cleaning and checks, what Next needs on each step, and
// how a VIN lookup fills the draft.

enum class CheckInStep(val number: Int) { VIN(1), TYPE(2), MAKE(3), YEAR(4), PRICE(5), REVIEW(6), PRINT(7) }

// The explicit VIN | Serial number switch (D-C3). Nothing is ever a serial because of its length.
enum class VinKind { VIN, SERIAL }

// The chips under the VIN field: the VIN is in the crate (D-C8), or the unit has none.
enum class VinAbsent { LATER, NONE }

enum class Field { VIN, FRAME_MATCH, TYPE, MAKE, MODEL, YEAR, COLOUR, PRICE }

enum class CheckInSheet { LEAVE, PRINT_LATER, WHERE_VIN }

// Print the tag, with no printer built in yet: the tag goes to the printer's own app, and only the
// person can say it printed.
enum class PrintPhase { SEND, ASK_DID_IT_PRINT, PRINTED }

// What the VIN lookup came back with, for the "From the VIN lookup" box. Only what came back.
enum class LookupOutcome { RUNNING, FOUND, NOTHING, OFFLINE, FAILED }

data class LookupResult(
    val vin: String,
    val outcome: LookupOutcome,
    val make: String? = null,
    val model: String? = null,
    val year: String? = null,
    val type: UnitType? = null,
) : Serializable

// The unit this VIN already belongs to on this phone.
data class ExistingUnit(val unitId: String, val sold: Boolean) : Serializable

// The whole draft. Serializable so it survives rotation, the OS killing the app, and the trip to
// the printer app (rememberSaveable). The unit ID is made once, when step 1 opens; saving is
// idempotent on it.
data class CheckInDraft(
    val unitId: String,
    val step: CheckInStep = CheckInStep.VIN,
    val fromReview: Boolean = false,
    val vinKind: VinKind = VinKind.VIN,
    val vinText: String = "",
    val vinAbsent: VinAbsent? = null,
    // The VIN result shows once it's been scanned, reaches 17 characters, or Next was tapped.
    val vinShown: Boolean = false,
    val frameMatch: Boolean = false,
    val type: UnitType? = null,
    val make: String = "",
    val model: String = "",
    val subModel: String = "",
    val year: String = "",
    val colour: String = "",
    val price: String = "",
    val notes: String = "",
    val fromLookup: Set<Field> = emptySet(),
    val lookup: LookupResult? = null,
    val existing: ExistingUnit? = null,
    // The field Next marked, and why.
    val missing: Field? = null,
    val missingWhy: String? = null,
    val sheet: CheckInSheet? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val copies: Int = 1,
    val phase: PrintPhase = PrintPhase.SEND,
    val recorded: Boolean = false,
    // The tag is being sent again after a print ("Print again"): recorded as a reprint.
    val again: Boolean = false,
) : Serializable

// The type cards, in the shop's order, "Other" last. Until the editable type list ships
// (unit-types-spec.md), these are the types the shop system already stores.
val TYPE_ORDER = listOf(UnitType.DIRT_BIKE, UnitType.ATV, UnitType.UTV, UnitType.SNOWMOBILE, UnitType.PWC, UnitType.OTHER)

// Spaces and dashes dropped, capitals. For a VIN: O to 0, I to 1, Q to 0 (a VIN never has them),
// and an 18-character read starting with "I" whose last 17 pass is taken as those 17 (some Code 39
// VIN labels add a leading I).
fun cleanVin(raw: String, kind: VinKind): String {
    val plain = raw.uppercase().filter { !it.isWhitespace() && it != '-' }
    if (kind == VinKind.SERIAL) return plain
    fun map(s: String) = s.replace('O', '0').replace('I', '1').replace('Q', '0')
    if (plain.length == 18 && plain.startsWith("I")) {
        val last = map(plain.drop(1))
        if (Vin.check(last) == VinCheck.Ok) return last
    }
    return map(plain)
}

sealed interface VinResult {
    data object Empty : VinResult
    data class WrongLength(val length: Int) : VinResult
    data object CheckFails : VinResult
    data object Passes : VinResult
    data object Serial : VinResult
}

fun vinResult(d: CheckInDraft): VinResult {
    val v = cleanVin(d.vinText, d.vinKind)
    return when {
        v.isEmpty() -> VinResult.Empty
        d.vinKind == VinKind.SERIAL -> VinResult.Serial
        v.length != 17 -> VinResult.WrongLength(v.length)
        Vin.check(v) == VinCheck.Ok -> VinResult.Passes
        else -> VinResult.CheckFails
    }
}

// The VIN this draft would save, or null for "Add it later" and "It has none".
fun savedVin(d: CheckInDraft): String? = cleanVin(d.vinText, d.vinKind).ifEmpty { null }

// The VIN that may be looked up and checked for duplicates: 17 characters that pass, or that fail
// and were ticked as matching the frame.
fun checkableVin(d: CheckInDraft): String? = when (vinResult(d)) {
    VinResult.Passes -> cleanVin(d.vinText, d.vinKind)
    VinResult.CheckFails -> if (d.frameMatch) cleanVin(d.vinText, d.vinKind) else null
    else -> null
}

// Typing in the VIN field. The result shows on its own once the field reaches 17 characters.
fun CheckInDraft.withVinText(raw: String): CheckInDraft {
    val v = cleanVin(raw, vinKind)
    val changed = v != cleanVin(vinText, vinKind)
    return copy(
        vinText = v,
        vinAbsent = if (v.isNotEmpty()) null else vinAbsent,
        vinShown = if (changed) (vinKind == VinKind.VIN && v.length >= 17) else vinShown,
        frameMatch = if (changed) false else frameMatch,
        existing = if (changed) null else existing,
        missing = if (missing == Field.VIN || missing == Field.FRAME_MATCH) null else missing,
        missingWhy = if (missing == Field.VIN || missing == Field.FRAME_MATCH) null else missingWhy,
    )
}

// A barcode read from the VIN label: cleaned and checked at once, as if typed and Next tapped.
fun CheckInDraft.withScannedVin(raw: String): CheckInDraft = withVinText(raw).copy(vinShown = true)

// What stops Next on a step, if anything: the field to mark and the reason to give under it.
fun nextProblem(d: CheckInDraft): Pair<Field, String>? = when (d.step) {
    CheckInStep.VIN -> when (val r = vinResult(d)) {
        VinResult.Empty -> if (d.vinAbsent != null) null else Field.VIN to
            (if (d.vinKind == VinKind.VIN) "Scan or type the VIN to go on." else "Type the serial number to go on.")
        is VinResult.WrongLength -> Field.VIN to "VINs have 17 characters. This has ${r.length}."
        VinResult.CheckFails -> if (d.frameMatch) null else Field.FRAME_MATCH to "Fix the VIN, or tick the box if it matches the frame exactly."
        VinResult.Passes, VinResult.Serial -> null
    }
    CheckInStep.TYPE -> if (d.type == null) Field.TYPE to "Pick the type to go on." else null
    CheckInStep.MAKE -> when {
        d.make.isBlank() -> Field.MAKE to "Type the make to go on."
        d.model.isBlank() -> Field.MODEL to "Type the model to go on."
        else -> null
    }
    CheckInStep.YEAR -> {
        val y = d.year.trim()
        if (y.isEmpty() || y.toIntOrNull()?.let { it in 1950..2100 } == true) null
        else Field.YEAR to "Type a model year from 1950 to 2100, or leave it empty."
    }
    CheckInStep.PRICE -> when {
        d.price.isBlank() -> Field.PRICE to "Type the asking price to go on."
        else -> (Money.parseDollars(d.price) as? MoneyResult.Invalid)?.let { Field.PRICE to it.message }
    }
    CheckInStep.REVIEW, CheckInStep.PRINT -> null
}

private val ORDER = listOf(CheckInStep.VIN, CheckInStep.TYPE, CheckInStep.MAKE, CheckInStep.YEAR, CheckInStep.PRICE, CheckInStep.REVIEW)

// Next: marks the field and says why, or moves on (back to Review when editing from there).
fun CheckInDraft.next(): CheckInDraft {
    val problem = nextProblem(this)
    if (problem != null) return copy(missing = problem.first, missingWhy = problem.second, vinShown = vinShown || step == CheckInStep.VIN)
    if (existing != null && step == CheckInStep.VIN) return this
    val to = if (fromReview) CheckInStep.REVIEW else ORDER[(ORDER.indexOf(step) + 1).coerceAtMost(ORDER.lastIndex)]
    return copy(step = to, fromReview = false, missing = null, missingWhy = null)
}

// Back on steps 2 to 6: the step before (or Review, when editing from there).
fun CheckInDraft.back(): CheckInDraft = when {
    fromReview -> copy(step = CheckInStep.REVIEW, fromReview = false, missing = null, missingWhy = null)
    step == CheckInStep.VIN -> copy(sheet = CheckInSheet.LEAVE)
    else -> copy(step = ORDER[(ORDER.indexOf(step) - 1).coerceAtLeast(0)], missing = null, missingWhy = null)
}

fun CheckInDraft.edit(step: CheckInStep): CheckInDraft = copy(step = step, fromReview = true, missing = null, missingWhy = null)

// A field the person changed is theirs now: it loses its "From the VIN" marker.
fun CheckInDraft.typed(field: Field, change: CheckInDraft.() -> CheckInDraft): CheckInDraft =
    change().copy(
        fromLookup = fromLookup - field,
        missing = if (missing == field) null else missing,
        missingWhy = if (missing == field) null else missingWhy,
    )

// The lookup fills empty fields only. It never writes the VIN or the notes, and a result for a
// VIN that has since changed is dropped.
fun CheckInDraft.withLookup(r: LookupResult): CheckInDraft {
    if (checkableVin(this) != r.vin) return this
    if (r.outcome != LookupOutcome.FOUND) return copy(lookup = r)
    var d = copy(lookup = r)
    var from = fromLookup
    if (d.make.isBlank() && !r.make.isNullOrBlank()) { d = d.copy(make = r.make); from = from + Field.MAKE }
    if (d.model.isBlank() && !r.model.isNullOrBlank()) { d = d.copy(model = r.model); from = from + Field.MODEL }
    if (d.year.isBlank() && !r.year.isNullOrBlank()) { d = d.copy(year = r.year); from = from + Field.YEAR }
    if (d.type == null && r.type != null) { d = d.copy(type = r.type); from = from + Field.TYPE }
    return d.copy(fromLookup = from)
}

// vPIC's BodyClass, mapped only when it clearly names one of the shop's types. vPIC calls ATVs and
// side-by-sides MOTORCYCLE in VehicleType, so that field alone is never used.
fun typeFromBodyClass(bodyClass: String?): UnitType? {
    val b = bodyClass?.lowercase() ?: return null
    return when {
        "snowmobile" in b -> UnitType.SNOWMOBILE
        "personal watercraft" in b || "jet ski" in b -> UnitType.PWC
        "recreational off-highway" in b || "(rov)" in b || "side-by-side" in b || "utv" in b -> UnitType.UTV
        "all terrain" in b || "(atv)" in b -> UnitType.ATV
        "dirt bike" in b || "motocross" in b || "enduro" in b -> UnitType.DIRT_BIKE
        else -> null
    }
}

fun lookupFoundLabels(r: LookupResult): List<Pair<String, String>> = buildList {
    r.make?.takeIf { it.isNotBlank() }?.let { add("Make" to it) }
    r.model?.takeIf { it.isNotBlank() }?.let { add("Model" to it) }
    r.year?.takeIf { it.isNotBlank() }?.let { add("Year" to it) }
    r.type?.let { add("Type" to it.shopWord()) }
}

// "Not found: model and type. You'll fill those in next." Null when everything came back.
fun lookupNotFoundLine(r: LookupResult): String? {
    val missing = buildList {
        if (r.make.isNullOrBlank()) add("make")
        if (r.model.isNullOrBlank()) add("model")
        if (r.year.isNullOrBlank()) add("year")
        if (r.type == null) add("type")
    }
    if (missing.isEmpty()) return null
    val words = if (missing.size == 1) missing[0] else missing.dropLast(1).joinToString(", ") + " and " + missing.last()
    return "Not found: $words. You'll fill ${if (missing.size == 1) "that" else "those"} in next."
}

// The help line under each step's heading says what the VIN lookup did there.
fun stepHelp(d: CheckInDraft): String? = when (d.step) {
    CheckInStep.VIN -> "Scan the VIN barcode, or type it."
    CheckInStep.TYPE -> when {
        Field.TYPE in d.fromLookup && d.type != null -> "From the VIN: ${d.type.shopWord()}. Change it if that's wrong."
        d.lookup?.outcome == LookupOutcome.FOUND -> "The VIN lookup didn't say. Pick the type."
        else -> "Pick the type."
    }
    CheckInStep.MAKE -> when {
        Field.MAKE in d.fromLookup && Field.MODEL in d.fromLookup -> "The VIN found the make and model. Check them against the unit."
        Field.MAKE in d.fromLookup -> "The VIN found the make. Type the model."
        else -> null
    }
    CheckInStep.YEAR -> null
    CheckInStep.PRICE -> "The asking price. It isn't printed on the tag."
    CheckInStep.REVIEW -> "Check it against the unit. Everything except the price prints on the tag."
    CheckInStep.PRINT -> if (d.phase == PrintPhase.PRINTED) "Stick it on the box, scan it, then tap Move to In stock." else null
}

fun stepHeading(d: CheckInDraft): String = when (d.step) {
    CheckInStep.VIN -> "VIN or serial number"
    CheckInStep.TYPE -> "What is it?"
    CheckInStep.MAKE -> "Make and model"
    CheckInStep.YEAR -> "Year and colour"
    CheckInStep.PRICE -> "Price"
    CheckInStep.REVIEW -> "Check it over"
    CheckInStep.PRINT -> if (d.phase == PrintPhase.PRINTED) "Tag printed" else "Print the tag"
}

// Model years run ahead of the calendar: from July, next year's models are on the truck.
fun currentModelYear(today: LocalDate): Int = if (today.monthValue >= 7) today.year + 1 else today.year

fun yearChips(today: LocalDate): List<String> = currentModelYear(today).let { y -> listOf(y, y - 1, y - 2).map { it.toString() } }

// The shop's most-used values on this phone (makes, colours), most used first.
fun mostUsed(values: List<String>, limit: Int = 6): List<String> =
    values.map { it.trim() }.filter { it.isNotEmpty() }
        .groupBy { it.lowercase() }
        .entries.sortedWith(compareByDescending<Map.Entry<String, List<String>>> { it.value.size }.thenBy { it.key })
        .take(limit)
        .map { e -> e.value.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key }

// The unit to save. Null price never happens here: Price requires one (the shop system refuses an
// unpriced unit until "Add the price later" is supported end to end).
fun toNewUnit(d: CheckInDraft): NewUnit {
    val vin = savedVin(d)
    val cents = (Money.parseDollars(d.price) as MoneyResult.Valid).cents
    return NewUnit(
        id = d.unitId, make = d.make.trim(), model = d.model.trim(), subModel = d.subModel.trim(),
        year = d.year.trim().toIntOrNull(), color = d.colour.trim(), unitType = (d.type ?: UnitType.OTHER).label,
        vin = vin, vinFrameVerified = d.vinKind == VinKind.VIN && vinResult(d) == VinResult.CheckFails && d.frameMatch,
        priceCents = cents, notes = d.notes.trim(),
    )
}

fun priceShown(d: CheckInDraft): String =
    (Money.parseDollars(d.price) as? MoneyResult.Valid)?.let { Money.format(it.cents).removeSuffix(".00") } ?: d.price
