package com.example.ui.home

import com.countryatv.core.ItemId
import com.countryatv.core.UnitStatus
import com.example.data.ShopUnitEntity
import com.example.ui.inscan.RecordCardData
import com.example.ui.inscan.look
import com.example.ui.inscan.statusOf
import com.example.ui.inscan.unitTypeWord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Everything Home shows, worked out from this phone's copy so it reads the same online and offline.
data class HomeModel(
    val incomingCount: Int,
    val inStockCount: Int,
    val salePendingCount: Int,
    val incoming: List<RecordCardData>,
)

private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

// IDs are ULIDs: the first 10 characters are the creation time in milliseconds, so the check-in
// time travels with the ID and needs no extra field. Null for an ID that isn't a ULID.
fun checkedInAt(id: String): Instant? {
    if (!ItemId.isValid(id)) return null
    var ms = 0L
    for (c in id.take(10)) ms = ms * 32 + CROCKFORD.indexOf(c)
    return Instant.ofEpochMilli(ms)
}

// Weekday within the last 6 days ("Tue"), the date after that ("Oct 12"), "today" for today.
fun shortDay(at: Instant, today: LocalDate, zone: ZoneId): String {
    val d = at.atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(d, today)
    return when {
        days == 0L -> "today"
        days in 1..6 -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
        else -> d.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
    }
}

fun unitName(u: ShopUnitEntity): String =
    listOfNotNull(u.year?.toString(), u.make.ifBlank { null }, u.model.ifBlank { null }, u.subModel.ifBlank { null })
        .joinToString(" ").ifBlank { "Unit with no make or model yet" }

// D-H4 (owner, 2026-11-01): cards carry no names. "Four wheels, checked in Tue", own units included.
fun recordCard(u: ShopUnitEntity, today: LocalDate, zone: ZoneId): RecordCardData {
    val status = statusOf(u.status) ?: UnitStatus.INCOMING
    val type = unitTypeWord(u.unitType)
    val day = checkedInAt(u.id)?.let { shortDay(it, today, zone) }
    val meta = if (day != null) "$type, checked in $day" else type
    val short = ItemId.shortForm(u.id)
    val look = status.look()
    return RecordCardData(
        id = short, name = unitName(u), meta = meta, status = look, typeWord = type,
        spoken = "${unitName(u)}. ${look.word}. Tag ${short.toList().joinToString(" ")}. $meta.",
    )
}

fun homeModel(units: List<ShopUnitEntity>, today: LocalDate, zone: ZoneId): HomeModel {
    fun count(s: UnitStatus) = units.count { statusOf(it.status) == s }
    val incoming = units.filter { statusOf(it.status) == UnitStatus.INCOMING }
        .sortedBy { checkedInAt(it.id) ?: Instant.MAX }
        .take(3)
        .map { recordCard(it, today, zone) }
    return HomeModel(count(UnitStatus.INCOMING), count(UnitStatus.IN_STOCK), count(UnitStatus.SALE_PENDING), incoming)
}
