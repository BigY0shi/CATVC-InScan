package com.example.ui.scan

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import com.countryatv.core.Money
import com.countryatv.core.UnitStatus
import com.countryatv.core.Vin
import com.countryatv.core.VinCheck
import com.example.data.MoveResult
import com.example.data.ScanLookup
import com.example.data.ShopUnitEntity
import com.example.ui.home.recordCard
import com.example.ui.inscan.Palette
import com.example.ui.inscan.RecordCardData
import com.example.ui.inscan.Roles
import com.example.ui.inscan.Tone
import com.example.ui.inscan.look
import com.example.ui.inscan.statusOf
import com.example.ui.inscan.unitTypeWord
import java.time.LocalDate
import java.time.ZoneId

// Everything the Scan screen draws (scan-pin-ui-spec.md section 2, prototypes/scan.html; LOCKED
// 2026-10-30), worked out from a lookup so every row of the result-card table is a plain function
// that tests can call.

enum class ScanMode { CAMERA, TYPE }

enum class CameraIssue { NOT_ALLOWED, NOT_ALLOWED_SETTINGS, COULDNT_START }

enum class Torch { NONE, OFF, ON }

// What was read: the header icon says it (qr_code_2, barcode, keyboard), in text-2.
enum class ReadKind { TAG, BARCODE, TYPED }

enum class CardAction { SCAN_AGAIN, TYPE_AGAIN, MOVE, REPRINT, CHECK_IN, CHECK_IN_ANYWAY, SCAN_QR, SCAN_NEXT, SIGN_IN }

data class CardButton(val label: String, val action: CardAction, val primary: Boolean)

// Plain notes are text with an optional coloured icon; warnings use the status-banner build.
data class ScanNote(val text: String, val icon: ImageVector? = null, val iconTint: Color? = null, val warn: Boolean = false)

data class Fact(val label: String, val value: String, val mono: Boolean = false)

// "Details from the tag": what the printed tag says, when the unit isn't on this phone.
data class TagSummary(val id: String, val name: String, val meta: String, val typeWord: String)

data class ScanCard(
    val read: ReadKind,
    val title: String,
    // The brackets take this colour; the header stays in text colours.
    val tone: Tone,
    val unitId: String? = null,
    val record: RecordCardData? = null,
    val tag: TagSummary? = null,
    val vin: String? = null,
    val facts: List<Fact> = emptyList(),
    val note: ScanNote? = null,
    // Left is the way back to reading; right is the primary when there is one. One spans the width.
    val buttons: List<CardButton>,
    // Set only when our own tag was read on an Incoming unit: what Move to In stock sends.
    val tagPayload: String? = null,
)

data class ScanUi(
    val firstName: String,
    val mode: ScanMode = ScanMode.CAMERA,
    val camera: CameraIssue? = null,
    val hint: Boolean = false,
    val torch: Torch = Torch.NONE,
    val typed: String = "",
    // Set after Look up with a length that's neither 8 nor 17: the length typed.
    val typedWrongLength: Int? = null,
    val card: ScanCard? = null,
    // Move to In stock is disabled from the first tap until the answer is in.
    val moving: Boolean = false,
) {
    val typedView: Boolean get() = mode == ScanMode.TYPE || card?.read == ReadKind.TYPED
}

// ---------- Typing ----------

// What's typed, cleaned before it counts: spaces dropped, a leading "ID" dropped (the tag prints
// "ID 3HZW8RTB"), and ItemId.normalizeShort at 8 characters (O to 0, I and L to 1). A string that
// starts with CATV1; is a whole tag typed by a Bluetooth scanner and goes through untouched.
fun cleanTyped(input: String): String {
    val raw = input.trim()
    if (raw.startsWith("CATV1;")) return raw
    var s = raw.uppercase().filter { !it.isWhitespace() && it != '-' }
    // Neither tag IDs (Crockford) nor VINs ever contain an I, so a leading "ID" is always the printed prefix.
    if (s.startsWith("ID") && s.length > 2) s = s.removePrefix("ID")
    return if (s.length == ItemId.SHORT_LENGTH) ItemId.normalizeShort(s) ?: s else s
}

fun typedCount(input: String): String {
    val n = cleanTyped(input).length
    return if (n <= 8) "$n of 8" else "$n of 17"
}

fun lookUpLabel(input: String): String = when (cleanTyped(input).length) {
    8 -> "Look up tag ID"
    17 -> "Look up VIN"
    else -> "Look up"
}

// Null when the typed text can go to the lookup, otherwise the length to show in the helper.
fun typedLengthProblem(input: String): Int? {
    val c = cleanTyped(input)
    if (c.startsWith("CATV1;")) return null
    return c.length.takeIf { it != 8 && it != 17 }
}

private fun isVin(code: String): Boolean {
    val v = Vin.normalize(code)
    return v.length == 17 && v.all { it.isLetterOrDigit() }
}

// ---------- Result cards (2c table) ----------

private fun again(read: ReadKind) =
    if (read == ReadKind.TYPED) CardButton("Type another", CardAction.TYPE_AGAIN, primary = false)
    else CardButton("Scan another", CardAction.SCAN_AGAIN, primary = false)

private fun vinEnding(vin: String?) = vin?.takeIf { it.length >= 6 }?.takeLast(6)

private fun price(cents: Long): String? =
    cents.takeIf { it > 0 }?.let { Money.format(it).removeSuffix(".00") }

fun factsFor(u: ShopUnitEntity): List<Fact> = listOfNotNull(
    u.color.takeIf { it.isNotBlank() }?.let { Fact("Colour", it) },
    vinEnding(u.vin)?.let { Fact("VIN ending", it, mono = true) },
    price(u.priceCents)?.let { Fact("Price", it) },
)

// "Polaris RZR XP 1000, Gray, VIN ending 104217": what to check the box against.
private fun boxCheck(u: ShopUnitEntity): String = listOfNotNull(
    listOf(u.make, u.model, u.subModel).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null },
    u.color.takeIf { it.isNotBlank() },
    vinEnding(u.vin)?.let { "VIN ending $it" },
).joinToString(", ")

fun readKindOf(code: String, typed: Boolean): ReadKind = when {
    typed -> ReadKind.TYPED
    LabelPayload.decode(code.trim()) != null -> ReadKind.TAG
    else -> ReadKind.BARCODE
}

// [code] is the cleaned value that was looked up; [expectId] is set when Scan was opened from
// check-in's Print, to check the tag just printed.
fun scanCard(lookup: ScanLookup, code: String, read: ReadKind, today: LocalDate, zone: ZoneId, expectId: String? = null): ScanCard {
    val tag = LabelPayload.decode(code.trim())
    if (expectId != null && tag != null && tag.id != expectId) {
        return ScanCard(read, "That's a different tag", Roles.warn,
            note = ScanNote("That's tag ${ItemId.shortForm(tag.id)}, not ${ItemId.shortForm(expectId)}."),
            buttons = listOf(again(read)))
    }
    val typedId = read == ReadKind.TYPED && code.length == ItemId.SHORT_LENGTH
    return when (lookup) {
        ScanLookup.NeedsSignIn -> ScanCard(read, "Sign in again", Roles.warn, note = ScanNote("Your scans are saved."),
            buttons = listOf(CardButton("Sign in", CardAction.SIGN_IN, primary = true)))

        ScanLookup.ShortIdClash -> ScanCard(read, "More than one unit has tag ID $code", Roles.warn,
            note = ScanNote("Scan the QR code or type the VIN instead."),
            buttons = listOf(again(read), CardButton("Scan the QR code", CardAction.SCAN_QR, primary = true)))

        is ScanLookup.TagOnly -> {
            val t = lookup.tag
            val type = tagTypeWord(t)
            val meta = listOf(t.color.ifBlank { null }, type).filterNotNull().joinToString(", ") + ". From the tag, may be out of date."
            ScanCard(read, "Details from the tag", Roles.warn,
                tag = TagSummary(ItemId.shortForm(t.id), tagName(t), meta, type),
                note = ScanNote("Another phone checked this unit in and hasn't sent it yet. Don't check it in again.", warn = true),
                buttons = listOf(again(read)))
        }

        ScanLookup.NotFound -> when {
            isVin(code) -> vinCard(read, code, found = true)
            typedId -> ScanCard(read, "No unit with tag ID $code", Roles.neutral,
                note = ScanNote("Check the characters, or scan the tag."), buttons = listOf(again(read)))
            else -> otherBarcode(read)
        }

        ScanLookup.Unreachable -> when {
            isVin(code) -> vinCard(read, code, found = false)
            typedId -> ScanCard(read, "No unit with tag ID $code", Roles.neutral,
                note = ScanNote("Not on this phone. Another phone may not have sent it yet. Don't check it in again.", warn = true),
                buttons = listOf(again(read)))
            else -> otherBarcode(read)
        }

        is ScanLookup.Found -> foundCard(lookup, read, today, zone)
    }
}

private fun otherBarcode(read: ReadKind) = ScanCard(read, "This barcode can't identify a unit", Roles.neutral,
    note = ScanNote("Scan the VIN or the tag instead."), buttons = listOf(again(read)))

// VIN not in the shop system (or the shop system couldn't be asked).
private fun vinCard(read: ReadKind, code: String, found: Boolean): ScanCard {
    val vin = Vin.normalize(code)
    return if (found) {
        val note = when (Vin.check(vin)) {
            VinCheck.Ok -> ScanNote("VIN check digit passes.", Icons.Outlined.CheckCircle, Roles.good.ink)
            else -> ScanNote("VIN check digit fails. Check it against the frame.", warn = true)
        }
        ScanCard(read, "Not in the shop system", Roles.neutral, vin = vin, note = note,
            buttons = listOf(again(read), CardButton("Check in this unit", CardAction.CHECK_IN, primary = true)))
    } else {
        ScanCard(read, "Couldn't check the shop system", Roles.warn, vin = vin,
            note = ScanNote("If another phone already checked this unit in, don't check it in again.", warn = true),
            buttons = listOf(again(read), CardButton("Check in anyway", CardAction.CHECK_IN_ANYWAY, primary = false)))
    }
}

private fun foundCard(f: ScanLookup.Found, read: ReadKind, today: LocalDate, zone: ZoneId): ScanCard {
    val u = f.unit
    val status = statusOf(u.status) ?: UnitStatus.INCOMING
    val rec = recordCard(u, today, zone)
    if (status != UnitStatus.INCOMING) {
        return ScanCard(read, "Found", status.look().tone, unitId = u.id, record = rec, facts = factsFor(u), buttons = listOf(again(read)))
    }
    val payload = f.tagPayload
    return when {
        // Moving needs the label read (handoff s2): a typed or VIN lookup never offers it.
        payload == null -> ScanCard(read, "Found, still Incoming", Roles.good, unitId = u.id, record = rec, facts = factsFor(u),
            note = ScanNote("Scan the tag to move it to In stock."), buttons = listOf(again(read)))
        f.tagOutOfDate -> ScanCard(read, "Tag out of date", Roles.warn, unitId = u.id, record = rec,
            note = ScanNote("The details on this tag don't match the unit any more. Reprint it before moving it to In stock.", warn = true),
            buttons = listOf(again(read), CardButton("Reprint tag", CardAction.REPRINT, primary = true)))
        else -> ScanCard(read, "Found, still Incoming", Roles.good, unitId = u.id, record = rec, facts = factsFor(u),
            note = ScanNote("The box tag reads. Check it matches the box: ${boxCheck(u)}."),
            buttons = listOf(again(read), CardButton("Move to In stock", CardAction.MOVE, primary = true)),
            tagPayload = payload)
    }
}

fun movedCard(result: MoveResult, today: LocalDate, zone: ZoneId): ScanCard = when (result) {
    is MoveResult.Moved -> {
        val left = if (result.stillIncoming == 0) "Nothing else is incoming." else "${result.stillIncoming} still incoming."
        ScanCard(ReadKind.TAG, if (result.sent) "Moved to In stock" else "Moved on this phone", Roles.good,
            unitId = result.unit.id, record = recordCard(result.unit, today, zone),
            note = if (result.sent) ScanNote(left, Icons.Outlined.Inventory2, Palette.text) else ScanNote("It sends when the phone connects. $left", Icons.Outlined.Inventory2, Palette.text),
            buttons = listOf(CardButton("Scan next box", CardAction.SCAN_NEXT, primary = true)))
    }
    is MoveResult.Refused -> ScanCard(ReadKind.TAG, "Not moved", Roles.warn, unitId = result.unit.id,
        record = recordCard(result.unit, today, zone), note = ScanNote(result.message, warn = true),
        buttons = listOf(CardButton("Scan another", CardAction.SCAN_AGAIN, primary = false)))
}

private fun tagName(t: LabelContent): String =
    listOf(t.year, t.make, t.model, t.subModel).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unit with no make or model yet" }

private fun tagTypeWord(t: LabelContent): String = unitTypeWord(t.unitType)
