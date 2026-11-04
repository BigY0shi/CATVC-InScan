package com.countryatv.core.reports

import kotlinx.serialization.Serializable

// The reports the server can build today, by the key used in /reports/{report}. The rest of the
// Reports list (Aging, Missing details, Days to sell and the Shop system group) isn't built yet;
// /reports/summary lists those under notBuilt rather than showing a number.
object ReportKeys {
    const val UNITS_BY_STATUS = "units_by_status"
    const val ASKING_PRICE_TOTAL = "asking_price_total"
    const val CHECK_INS = "check_ins"
    const val TIME_TO_IN_STOCK = "time_to_in_stock"
    const val TAGS_PRINTED = "tags_printed"
    const val TAGS_NOT_CONFIRMED = "tags_not_confirmed"
    const val SOLD = "sold"
    const val WORK_BY_PERSON = "work_by_person"

    // In the order the Reports list shows them.
    val BUILT = listOf(UNITS_BY_STATUS, ASKING_PRICE_TOTAL, CHECK_INS, TIME_TO_IN_STOCK, TAGS_PRINTED, TAGS_NOT_CONFIRMED, SOLD, WORK_BY_PERSON)
    val NOT_BUILT = listOf("aging", "missing_details", "days_to_sell", "shop_system")
}

// What a figure counts, so the app formats it: count (a plain number), cents (money),
// minutes (a duration) or people.
object FigureUnits {
    const val COUNT = "count"
    const val CENTS = "cents"
    const val MINUTES = "minutes"
    const val PEOPLE = "people"
}

// One report, as GET /reports/{report} returns it (reports spec §4, §10, §14).
@Serializable
data class ReportDto(
    val report: String,
    // "snapshot" (right now; no range) or "period" (a date range).
    val kind: String,
    // The server time the figures were computed at.
    val asOf: String,
    // The preset asked for, or null for custom dates and snapshots.
    val preset: String? = null,
    // The resolved range as shop-calendar dates, both inclusive ("2026-10-26"). Null for snapshots.
    val from: String? = null,
    val to: String? = null,
    // The same range as UTC instants [startUtc, endUtc).
    val startUtc: String? = null,
    val endUtc: String? = null,
    // One or two headline figures. tiles[0] is the figure on the Reports list.
    val tiles: List<Tile>,
    // The chart, or null when the report has none or the range is a single day.
    val series: Series? = null,
    // Every breakdown key's rows, in the order the switch shows them.
    val breakdowns: List<Breakdown> = emptyList()
)

@Serializable
data class Tile(
    val key: String,
    // Null with tracked = true: nothing to measure (a median of no units). Null with
    // tracked = false: the shop system doesn't record this yet ("Not tracked yet"). Never 0 for either.
    val value: Long? = null,
    val unit: String = FigureUnits.COUNT,
    val tracked: Boolean = true,
    // The context line's figures, e.g. incoming and salePending beside "37 in stock".
    val parts: List<Figure> = emptyList(),
    // Period reports only: the same figure for the comparison range.
    val comparison: Comparison? = null,
    // For a tile that counts units: the units it counts.
    val drill: UnitFilter? = null
)

@Serializable
data class Figure(val key: String, val value: Long? = null, val unit: String = FigureUnits.COUNT, val tracked: Boolean = true)

// basis: same_days_last_week (this week so far), week_before (a finished week), same_day_last_week
// (today), same_days_last_month (this month so far), previous_30_days, or previous_days (custom
// dates: the same number of days just before). from and to are shop dates, inclusive.
@Serializable
data class Comparison(val basis: String, val from: String, val to: String, val value: Long? = null)

// bucket: day, week or month (reports spec §6). Points cover the range exactly, oldest first.
@Serializable
data class Series(val bucket: String, val points: List<SeriesPoint>)

@Serializable
data class SeriesPoint(val from: String, val to: String, val value: Long? = null)

// key: status, type, make, person, day, week, month, reason, printer, days_waiting.
@Serializable
data class Breakdown(val key: String, val rows: List<BreakdownRow>)

@Serializable
data class BreakdownRow(
    // Stable key for the row (a status name, a type label, an employee id, a date).
    val key: String,
    // What the row says, in shop words.
    val label: String,
    // The row's count. Null only for rows that show figures instead (Work by person).
    val count: Long? = null,
    // More figures for the row: askingPriceCents, medianMinutes, or Work by person's three columns.
    val figures: List<Figure> = emptyList(),
    // Rows that count units carry the filter for their list; GET /units/query with it returns
    // exactly count units. Null: the row doesn't open a list (no chevron).
    val drill: UnitFilter? = null,
    // Work by person rows: the person, for GET /people/{employeeId}/history.
    val personId: String? = null
)

// GET /reports/summary: every list figure the caller may see, in one call.
@Serializable
data class ReportSummary(
    val asOf: String,
    // Period figures use this range (This week).
    val preset: String,
    val from: String,
    val to: String,
    val figures: List<SummaryFigure>,
    // Reports on the list that the server doesn't build yet.
    val notBuilt: List<String> = emptyList()
)

// tile is exactly the report's own tiles[0], computed by the report's own function.
@Serializable
data class SummaryFigure(val report: String, val kind: String, val tile: Tile)

// POST /reports/{report}/export body. Either preset or from and to. rows: "each" (one row per
// check-in, the default) or "table" (the breakdown named by "by", with shares).
@Serializable
data class ExportRequest(
    val preset: String? = null,
    val from: String? = null,
    val to: String? = null,
    val rows: String = "each",
    val by: String? = null
)
