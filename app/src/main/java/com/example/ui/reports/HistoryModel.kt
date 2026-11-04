package com.example.ui.reports

import com.countryatv.core.ItemId
import com.countryatv.core.UnitStatus
import com.countryatv.core.reports.FieldChange
import com.countryatv.core.reports.HistoryEvent
import com.countryatv.core.reports.HistoryKinds
import com.countryatv.core.reports.HistoryTotals
import com.countryatv.core.reports.HistoryUnit
import com.example.data.OutboxEntry
import com.example.data.ShopUnitEntity
import com.example.shop.ShopSync
import com.example.ui.home.unitName
import com.example.ui.inscan.look
import com.example.ui.inscan.statusOf
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// My history (my-history-ui-spec.md §2): what one person did, by day. The words for each kind
// follow the §2 action table; printed_tag and status_change aren't in it (owner decision, see report).

enum class EventIcon { CHECKED_IN, PRINT, MOVED, TAG_SCAN, EDIT, SOLD, BROUGHT_BACK, STATUS }

data class EventRow(
    val key: String,
    val unitId: String,
    val icon: EventIcon,
    val action: String,
    val time: String,
    val name: String,
    val shortId: String,
    val note: String?,
    val changedSince: Boolean,
    val spoken: String,
)

data class DaySection(val heading: String, val spokenHeading: String, val rows: List<EventRow>)

private val US = Locale.US

private fun fieldWord(field: String) = when (field) {
    "price" -> "price"; "colour" -> "colour"; "vin" -> "VIN"
    else -> "details"
}

private fun changeValue(field: String, v: String?): String = when {
    v.isNullOrBlank() -> "none"
    field == "price" -> v.toLongOrNull()?.let(::dollars) ?: v
    else -> v
}

// "Changed the price" + "$9,899 → $9,499". Several fields, or year/make/model/type: "the details".
private fun editWords(changes: List<FieldChange>): Pair<String, String?> {
    val fields = changes.map { it.field }.distinct()
    val word = if (fields.size == 1) fieldWord(fields[0]) else "details"
    val note = when {
        changes.size == 1 -> changes[0].let { "${changeValue(it.field, it.from)} → ${changeValue(it.field, it.to)}" }
        changes.isEmpty() -> null
        else -> fields.joinToString(", ") { fieldWord(it).let { w -> if (w == "details") it else w } }.replaceFirstChar { it.titlecase(US) }
    }
    return "Changed the $word" to note
}

private fun statusWord(s: String?) = s?.let { statusOf(it)?.look()?.word } ?: s.orEmpty()

fun unitTitle(u: HistoryUnit): String =
    listOfNotNull(u.year?.toString(), u.make.ifBlank { null }, u.model.ifBlank { null }, u.subModel.ifBlank { null })
        .joinToString(" ").ifBlank { "Unit with no make or model yet" }

private fun spelled(id: String) = id.toList().joinToString(" ")

fun eventRow(e: HistoryEvent, zone: ZoneId): EventRow {
    val at = parseInstant(e.occurredAt)
    val time = at?.let { clock(it, zone) }.orEmpty()
    val (icon, action, note) = when (e.kind) {
        HistoryKinds.CHECK_IN -> Triple(EventIcon.CHECKED_IN, "Checked in",
            parseInstant(e.tagPrintedAt)?.let { "Tag printed ${clock(it, zone)}" } ?: "No tag yet")
        HistoryKinds.REPRINT -> Triple(EventIcon.PRINT, "Reprinted the tag", e.reason?.takeIf { it.isNotBlank() }?.let { "Reason: $it" })
        HistoryKinds.PRINTED_TAG -> Triple(EventIcon.PRINT, "Printed the tag", null)
        HistoryKinds.MOVED_TO_IN_STOCK -> Triple(EventIcon.MOVED, "Moved to In stock", null)
        HistoryKinds.CONFIRMED_NEW_TAG -> Triple(EventIcon.TAG_SCAN, "Confirmed the new tag", null)
        HistoryKinds.EDIT -> editWords(e.changes).let { Triple(EventIcon.EDIT, it.first, it.second) }
        HistoryKinds.MARKED_SOLD -> Triple(EventIcon.SOLD, "Marked sold", e.priceCents?.let { "Asking ${dollars(it)}" })
        HistoryKinds.BROUGHT_BACK -> Triple(EventIcon.BROUGHT_BACK, "Brought it back in", null)
        else -> Triple(EventIcon.STATUS, "Changed the status",
            listOfNotNull("${statusWord(e.fromStatus)} → ${statusWord(e.toStatus)}", e.reason?.takeIf { it.isNotBlank() }?.let { "Reason: $it" }).joinToString(". "))
    }
    val since = e.changedSince
    val name = unitTitle(e.unit)
    val spokenNote = note?.replace("→", "to")?.replace("Reason: ", "reason ")
    val spoken = listOfNotNull(action, spokenNote, if (since) "changed since" else null, name, time, "ID ${spelled(e.unit.shortId)}").joinToString(", ") + "."
    return EventRow("e${e.eventId}", e.unit.id, icon, action, time, name, e.unit.shortId, note, since, spoken)
}

// Days, newest first: "Today", "Yesterday", "Wed 28 Oct" (spoken in full).
fun daySections(rows: List<Pair<LocalDate, EventRow>>, today: LocalDate): List<DaySection> =
    rows.groupBy({ it.first }, { it.second }).toSortedMap(compareByDescending { it }).map { (d, list) ->
        val heading = when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> shortDay(d) }
        val spoken = when (d) { today -> "Today, ${fullDay(d)}"; today.minusDays(1) -> "Yesterday, ${fullDay(d)}"; else -> fullDay(d) }
        DaySection(heading, spoken, list)
    }

fun historyDays(events: List<HistoryEvent>, today: LocalDate, zone: ZoneId): List<DaySection> =
    daySections(events.map { e -> (parseInstant(e.occurredAt)?.atZone(zone)?.toLocalDate() ?: today) to eventRow(e, zone) }, today)

// The two tiles and the "Also" line (§2 items 6 and 7). No week-on-week comparison here.
data class HistoryTiles(val checkIns: TileText, val moved: TileText?, val also: String?)

fun historyTiles(t: HistoryTotals, range: RangeShown, today: LocalDate): HistoryTiles {
    val ctx = when {
        t.checkIns == 0L -> when (range.query.preset) {
            Presets.THIS_WEEK -> "This week so far"
            Presets.TODAY -> "Today so far"
            Presets.THIS_MONTH -> "This month so far"
            else -> range.title(today)
        }
        t.stillIncoming == 0L -> "None still Incoming"
        else -> "${t.stillIncoming} still Incoming"
    }
    val first = TileText("${t.checkIns}", noun(t.checkIns, "check-in"), ctx)
    if (t.checkIns == 0L) return HistoryTiles(first, null, null)
    val also = "Also ${range.phrase(today)}: " + if (t.edits == 0L && t.reprints == 0L) "no edits or reprints." else "${plural(t.edits, "edit")}, ${plural(t.reprints, "reprint")}."
    return HistoryTiles(first, TileText("${t.movedToInStock}", "moved to In stock", "By scanning the box tag"), also)
}

// "Nothing this week yet." / "Nothing last week."
fun nothingLine(range: RangeShown, today: LocalDate): String = when (range.query.preset) {
    Presets.TODAY, Presets.THIS_WEEK, Presets.THIS_MONTH -> "Nothing ${range.phrase(today)} yet."
    else -> "Nothing ${range.phrase(today)}."
}

// ---------- This phone's changes not sent yet (§2 item 5) ----------

// The phone keeps each change until the shop system accepts it. It doesn't store the value an
// edit replaced, so a queued edit shows the new value only.
fun queuedRow(entry: OutboxEntry, units: List<ShopUnitEntity>, zone: ZoneId): EventRow {
    val unit = units.firstOrNull { it.id == entry.itemId }
    val name = unit?.let(::unitName) ?: "Unit ${ItemId.shortForm(entry.itemId)}"
    val short = ItemId.shortForm(entry.itemId)
    val p = runCatching { JSONObject(entry.payloadJson) }.getOrNull()
    fun str(k: String) = p?.optString(k)?.takeIf { p.has(k) && !p.isNull(k) }
    val (icon, action, note) = when (entry.kind) {
        ShopSync.KIND_CREATE -> Triple(EventIcon.CHECKED_IN, "Checked in", null)
        ShopSync.KIND_TAG_PRINT -> if (str("reprintReason").isNullOrBlank()) Triple(EventIcon.PRINT, "Printed the tag", null)
            else Triple(EventIcon.PRINT, "Reprinted the tag", "Reason: ${str("reprintReason")}")
        ShopSync.KIND_TAG_CONFIRM -> Triple(EventIcon.TAG_SCAN, "Scanned the tag", null)
        ShopSync.KIND_STATUS -> when (val s = str("status")) {
            UnitStatus.SOLD.name -> Triple(EventIcon.SOLD, "Marked sold", null)
            else -> Triple(EventIcon.STATUS, "Changed the status", "To ${statusWord(s)}")
        }
        else -> {
            val fields = listOf("priceCents" to "price", "color" to "colour", "vin" to "vin", "year" to "year", "make" to "make",
                "model" to "model", "subModel" to "model", "unitType" to "type").filter { str(it.first) != null }
            val word = fields.map { it.second }.distinct().let { if (it.size == 1) fieldWord(it[0]) else "details" }
            val only = fields.singleOrNull()
            val now = only?.let { (k, f) -> if (f == "price") str(k)?.toLongOrNull()?.let(::dollars) else str(k) }
            Triple(EventIcon.EDIT, "Changed the $word", now?.let { "Now $it" })
        }
    }
    val time = clock(Instant.ofEpochMilli(entry.createdAt), zone)
    val spoken = listOfNotNull(action, note, name, time, "ID ${spelled(short)}").joinToString(", ") + ", not sent yet."
    return EventRow("q${entry.seq}", entry.itemId, icon, action, time, name, short, note, false, spoken)
}

data class PhoneStatus(
    val offline: Boolean,
    val pending: List<EventRow>,
    // The first refused change: its reason in words. Refused changes never sit under "Not sent yet".
    val refusedCount: Int,
    val refusedReason: String?,
)

fun phoneStatus(outbox: List<OutboxEntry>, units: List<ShopUnitEntity>, offline: Boolean, zone: ZoneId): PhoneStatus {
    val refused = outbox.filter { it.state == OutboxEntry.STATE_NEEDS_ATTENTION }
    val pending = outbox.filter { it.state == OutboxEntry.STATE_PENDING }.sortedByDescending { it.createdAt }.map { queuedRow(it, units, zone) }
    return PhoneStatus(offline, pending, refused.size, refused.firstOrNull()?.problemMessage)
}

fun offlineBannerBody(n: Int): String =
    "${if (n == 1) "1 of your changes is" else "$n of your changes are"} saved on this phone. They'll send by themselves once it connects. You don't need to do them again."

fun refusedTitle(n: Int): String = if (n == 1) "1 of your changes didn't go through" else "$n of your changes didn't go through"
