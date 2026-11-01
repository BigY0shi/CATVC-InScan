package com.countryatv.server

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// The range presets a report can ask for. Phones send the preset, never the dates, so only the
// server decides where a shop week starts (reports spec §5).
enum class RangePreset(val key: String) {
    TODAY("today"),
    THIS_WEEK("this_week"),
    LAST_WEEK("last_week"),
    THIS_MONTH("this_month"),
    LAST_30_DAYS("last_30_days");

    companion object {
        fun byKey(key: String): RangePreset? = entries.firstOrNull { it.key == key }
    }
}

// Shop-calendar dates [from, to], both inclusive, and the UTC instants [startUtc, endUtc) that
// cover them. Compare stored times (fixed-width UTC text) against stampOf(startUtc) and stampOf(endUtc).
data class ShopRange(val from: LocalDate, val to: LocalDate, val startUtc: Instant, val endUtc: Instant) {
    val days: Long get() = ChronoUnit.DAYS.between(from, to) + 1
}

// How a chart splits a range (reports spec §6): a single day has no chart; up to 14 days, a column
// per day; up to 16 weeks, per week; longer, per month.
enum class BucketSize { NONE, DAY, WEEK, MONTH }

data class Bucket(val from: LocalDate, val to: LocalDate, val startUtc: Instant, val endUtc: Instant)

// Turns shop dates into UTC instants. SQLite has no time zones, so every "this week" question is
// answered here, with the shop's own zone and week start (Shop settings), and a day is whatever
// length the zone says: 25 hours on Sun 1 Nov 2026 in Pacific time, 23 hours on Sun 14 Mar 2027.
class ShopCalendar(val zone: ZoneId, val weekStart: DayOfWeek, private val clock: Clock) {

    fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), zone)

    fun startOf(day: LocalDate): Instant = day.atStartOfDay(zone).toInstant()

    fun weekStartOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(weekStart))

    fun range(from: LocalDate, to: LocalDate): ShopRange {
        require(!to.isBefore(from)) { "to is before from" }
        return ShopRange(from, to, startOf(from), startOf(to.plusDays(1)))
    }

    fun range(preset: RangePreset): ShopRange {
        val today = today()
        return when (preset) {
            RangePreset.TODAY -> range(today, today)
            RangePreset.THIS_WEEK -> range(weekStartOf(today), today)
            RangePreset.LAST_WEEK -> weekStartOf(today).minusWeeks(1).let { range(it, it.plusDays(6)) }
            RangePreset.THIS_MONTH -> range(today.withDayOfMonth(1), today)
            RangePreset.LAST_30_DAYS -> range(today.minusDays(29), today)
        }
    }

    fun bucketSize(range: ShopRange): BucketSize = when {
        range.days <= 1 -> BucketSize.NONE
        range.days <= 14 -> BucketSize.DAY
        range.days <= 16 * 7 -> BucketSize.WEEK
        else -> BucketSize.MONTH
    }

    // Buckets cover the range exactly: the first and last week or month are cut to the range.
    fun buckets(range: ShopRange): List<Bucket> {
        val size = bucketSize(range)
        if (size == BucketSize.NONE) return emptyList()
        return buildList {
            var start = range.from
            while (!start.isAfter(range.to)) {
                val natural = when (size) {
                    BucketSize.DAY -> start
                    BucketSize.WEEK -> weekStartOf(start).plusDays(6)
                    else -> start.with(TemporalAdjusters.lastDayOfMonth())
                }
                val end = minOf(natural, range.to)
                add(Bucket(start, end, startOf(start), startOf(end.plusDays(1))))
                start = end.plusDays(1)
            }
        }
    }
}
