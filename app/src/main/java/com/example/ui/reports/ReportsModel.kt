package com.example.ui.reports

import com.countryatv.core.UnitStatus
import com.countryatv.core.reports.Breakdown
import com.countryatv.core.reports.BreakdownRow
import com.countryatv.core.reports.FigureUnits
import com.countryatv.core.reports.ReportDto
import com.countryatv.core.reports.ReportKeys
import com.countryatv.core.reports.ReportSummary
import com.countryatv.core.reports.Series
import com.countryatv.core.reports.Tile
import com.countryatv.core.reports.UnitFilter
import com.example.shop.RangeQuery
import com.example.ui.inscan.StatusLook
import com.example.ui.inscan.look
import com.example.ui.inscan.statusOf
import com.example.ui.inscan.unitTypeWord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// Everything the Reports and My history screens say, worked out from the shop system's answers.
// No Compose here, so every wording is unit-tested. Specs: reports-ui-spec.md, my-history-ui-spec.md.

// ---------- Numbers and dates ----------

private val US = Locale.US

fun plural(n: Long, one: String, many: String = one + "s") = if (n == 1L) "$n $one" else "$n $many"
fun noun(n: Long, one: String, many: String = one + "s") = if (n == 1L) one else many

// Whole dollars: "$318,400". Report totals never show cents.
fun dollars(cents: Long): String = "$" + String.format(US, "%,d", Math.floorDiv(cents + 50, 100L))

// "2 hours", "45 minutes", "3 days": a median wait in the largest unit that reads naturally.
fun duration(minutes: Long): Pair<Long, String> = when {
    minutes < 60 -> minutes to noun(minutes, "minute")
    minutes < 48 * 60 -> ((minutes + 30) / 60).let { it to noun(it, "hour") }
    else -> ((minutes + 720) / 1440).let { it to noun(it, "day") }
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", US)
private val DAY_FULL = DateTimeFormatter.ofPattern("EEEE d MMMM", US)
private val DATE = DateTimeFormatter.ofPattern("d MMM", US)

fun shortDay(d: LocalDate): String = d.format(DAY)          // Mon 26 Oct
fun fullDay(d: LocalDate): String = d.format(DAY_FULL)      // Monday 26 October
fun shortDate(d: LocalDate): String = d.format(DATE)        // 26 Oct

fun clock(at: Instant, zone: ZoneId): String =
    at.atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", US)).lowercase(US)

fun parseInstant(s: String?): Instant? = s?.let { runCatching { Instant.parse(it) }.getOrNull() }
fun parseDate(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

// "Updated 9:38 am" from the server's asOf.
fun updatedLine(asOf: String?, zone: ZoneId): String? = parseInstant(asOf)?.let { "Updated ${clock(it, zone)}" }

// ---------- Ranges (reports spec §4, §5) ----------

object Presets {
    const val TODAY = "today"
    const val THIS_WEEK = "this_week"
    const val LAST_WEEK = "last_week"
    const val THIS_MONTH = "this_month"
    const val LAST_30_DAYS = "last_30_days"
    val ALL = listOf(TODAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_30_DAYS)
}

// The shop setting "Week starts on" (default Monday, reports spec §12). The shop system resolves
// every range it is sent; the phone only uses this to print the preset dates in the range sheet.
val SHOP_WEEK_START: DayOfWeek = DayOfWeek.MONDAY

fun presetTitle(p: String): String = when (p) {
    Presets.TODAY -> "Today"
    Presets.THIS_WEEK -> "This week"
    Presets.LAST_WEEK -> "Last week"
    Presets.THIS_MONTH -> "This month"
    Presets.LAST_30_DAYS -> "Last 30 days"
    else -> "Custom dates"
}

// The dates a preset covers, for the range sheet only (Starling: each preset with its real dates).
fun presetDates(p: String, today: LocalDate): Pair<LocalDate, LocalDate> {
    val weekStart = today.with(TemporalAdjusters.previousOrSame(SHOP_WEEK_START))
    return when (p) {
        Presets.TODAY -> today to today
        Presets.THIS_WEEK -> weekStart to today
        Presets.LAST_WEEK -> weekStart.minusWeeks(1) to weekStart.minusDays(1)
        Presets.THIS_MONTH -> today.withDayOfMonth(1) to today
        else -> today.minusDays(29) to today
    }
}

// The second line of the range button and of each preset row.
fun rangeDates(preset: String?, from: LocalDate, to: LocalDate, today: LocalDate): String = when (preset) {
    Presets.TODAY -> shortDay(from)
    Presets.THIS_WEEK -> "${shortDay(from)} to today"
    Presets.THIS_MONTH -> "${shortDate(from)} to today"
    Presets.LAST_30_DAYS -> "${shortDate(from)} to ${shortDate(to)}"
    else -> when {
        from == to -> shortDay(from)
        to == today -> "${shortDay(from)} to today"
        else -> "${shortDay(from)} to ${shortDay(to)}"
    }
}

enum class StepUnit(val word: String) { DAY("day"), WEEK("week"), MONTH("month"), DAYS("range") }

// The range as the screen shows it: the query sent, and the dates the shop system resolved.
data class RangeShown(val query: RangeQuery, val from: LocalDate, val to: LocalDate) {
    val days: Long get() = ChronoUnit.DAYS.between(from, to) + 1

    fun title(today: LocalDate): String = query.preset?.let(::presetTitle) ?: when (unit()) {
        StepUnit.DAY -> if (from == today.minusDays(1)) "Yesterday" else from.dayOfWeek.getDisplayName(TextStyle.FULL, US)
        StepUnit.WEEK -> "Week of ${shortDate(from)}"
        StepUnit.MONTH -> from.month.getDisplayName(TextStyle.FULL, US) + if (from.year != today.year) " ${from.year}" else ""
        StepUnit.DAYS -> "Custom dates"
    }

    fun dates(today: LocalDate) = rangeDates(query.preset, from, to, today)

    fun unit(): StepUnit = when (query.preset) {
        Presets.TODAY -> StepUnit.DAY
        Presets.THIS_WEEK, Presets.LAST_WEEK -> StepUnit.WEEK
        Presets.THIS_MONTH -> StepUnit.MONTH
        Presets.LAST_30_DAYS -> StepUnit.DAYS
        else -> when {
            days == 1L -> StepUnit.DAY
            days == 7L && from.dayOfWeek == SHOP_WEEK_START -> StepUnit.WEEK
            from.dayOfMonth == 1 && to == from.with(TemporalAdjusters.lastDayOfMonth()) -> StepUnit.MONTH
            else -> StepUnit.DAYS
        }
    }

    // The stepper names say the unit they step: "Previous week", "Next week".
    fun stepWord(): String = if (query.preset == Presets.LAST_30_DAYS) "30 days" else unit().word

    fun previous(): RangeQuery = when {
        query.preset == Presets.THIS_WEEK -> RangeQuery.preset(Presets.LAST_WEEK)
        unit() == StepUnit.MONTH -> from.minusMonths(1).let { RangeQuery.dates(it.toString(), it.with(TemporalAdjusters.lastDayOfMonth()).toString()) }
        else -> RangeQuery.dates(from.minusDays(days).toString(), to.minusDays(days).toString())
    }

    // Null when the range already reaches today (Next is disabled).
    fun next(today: LocalDate): RangeQuery? {
        if (!to.isBefore(today)) return null
        return when (unit()) {
            StepUnit.DAY -> from.plusDays(1).let { if (it == today) RangeQuery.preset(Presets.TODAY) else RangeQuery.dates(it.toString(), it.toString()) }
            StepUnit.WEEK -> if (!to.plusDays(7).isBefore(today)) RangeQuery.preset(Presets.THIS_WEEK)
                else RangeQuery.dates(from.plusDays(7).toString(), to.plusDays(7).toString())
            StepUnit.MONTH -> from.plusMonths(1).let { m ->
                if (!m.with(TemporalAdjusters.lastDayOfMonth()).isBefore(today)) RangeQuery.preset(Presets.THIS_MONTH)
                else RangeQuery.dates(m.toString(), m.with(TemporalAdjusters.lastDayOfMonth()).toString())
            }
            StepUnit.DAYS -> {
                val nf = from.plusDays(days); val nt = to.plusDays(days)
                if (!nt.isBefore(today)) RangeQuery.dates(nf.toString(), today.toString()) else RangeQuery.dates(nf.toString(), nt.toString())
            }
        }
    }

    // "This week" in a sentence: "Also this week", "Nothing last week".
    fun phrase(today: LocalDate): String = when (query.preset) {
        Presets.TODAY -> "today"
        Presets.THIS_WEEK -> "this week"
        Presets.LAST_WEEK -> "last week"
        Presets.THIS_MONTH -> "this month"
        Presets.LAST_30_DAYS -> "in the last 30 days"
        else -> if (from == to) "on ${shortDay(from)}" else "from ${shortDay(from)} to ${if (to == today) "today" else shortDay(to)}"
    }

    // "Updating to last week…"
    fun spoken(today: LocalDate): String = when (query.preset) {
        null -> title(today).replaceFirstChar { it.lowercase(US) }.let { if (unit() == StepUnit.DAY || unit() == StepUnit.DAYS) dates(today) else it }
        else -> presetTitle(query.preset).lowercase(US)
    }
}

// Before the first answer, the shown range is the phone's reading of the preset; the shop
// system's resolved from and to replace it as soon as it answers.
fun guessRange(q: RangeQuery, today: LocalDate): RangeShown {
    if (q.preset != null) return presetDates(q.preset, today).let { RangeShown(q, it.first, it.second) }
    return RangeShown(q, parseDate(q.from) ?: today, parseDate(q.to) ?: today)
}

fun resolvedRange(q: RangeQuery, from: String?, to: String?, today: LocalDate): RangeShown {
    val f = parseDate(from); val t = parseDate(to)
    return if (f != null && t != null) RangeShown(q, f, t) else guessRange(q, today)
}

// ---------- The Reports list (§3) ----------

data class ListRow(val key: String, val name: String, val figure: String, val opens: Boolean, val lock: Boolean = false)
data class ListGroup(val title: String, val rows: List<ListRow>)

const val NOT_TRACKED = "Not tracked yet"

private val REPORT_NAMES = mapOf(
    ReportKeys.UNITS_BY_STATUS to "Units by status",
    ReportKeys.ASKING_PRICE_TOTAL to "Asking price total",
    "aging" to "Aging",
    "missing_details" to "Missing details",
    ReportKeys.CHECK_INS to "Check-ins",
    ReportKeys.TIME_TO_IN_STOCK to "Time to In stock",
    ReportKeys.TAGS_PRINTED to "Tags printed",
    ReportKeys.TAGS_NOT_CONFIRMED to "Tags not confirmed",
    ReportKeys.SOLD to "Sold",
    "days_to_sell" to "Days to sell",
    ReportKeys.WORK_BY_PERSON to "Work by person",
    "shop_system" to "Phones, problems and security",
)

fun reportName(key: String): String = REPORT_NAMES[key] ?: key

private val GROUPS = listOf(
    "Inventory" to listOf(ReportKeys.UNITS_BY_STATUS, ReportKeys.ASKING_PRICE_TOTAL, "aging", "missing_details"),
    "Intake" to listOf(ReportKeys.CHECK_INS, ReportKeys.TIME_TO_IN_STOCK, ReportKeys.TAGS_PRINTED, ReportKeys.TAGS_NOT_CONFIRMED),
    "Sales" to listOf(ReportKeys.SOLD, "days_to_sell"),
    "People" to listOf(ReportKeys.WORK_BY_PERSON),
    "Shop system" to listOf("shop_system"),
)

// The list, in the owner's order. summary null: first load ("Loading…" in place of each figure).
// A report the shop system doesn't build yet reads "Not tracked yet", never 0, and doesn't open.
fun reportList(summary: ReportSummary?): List<ListGroup> = GROUPS.map { (title, keys) ->
    ListGroup(title, keys.map { key ->
        val fig = summary?.figures?.firstOrNull { it.report == key }
        when {
            summary == null -> ListRow(key, reportName(key), "Loading…", opens = key in ReportKeys.BUILT)
            fig == null -> ListRow(key, reportName(key), NOT_TRACKED, opens = false)
            else -> ListRow(key, reportName(key), listFigure(key, fig.tile), opens = true)
        }
    })
}

// The figure on the list names its own range (summary period figures are This week).
fun listFigure(key: String, t: Tile): String {
    if (!t.tracked) return NOT_TRACKED
    val v = t.value
    return when (key) {
        ReportKeys.UNITS_BY_STATUS -> "${v ?: 0} in stock"
        ReportKeys.ASKING_PRICE_TOTAL -> dollars(v ?: 0)
        ReportKeys.CHECK_INS, ReportKeys.TAGS_PRINTED, ReportKeys.SOLD -> "${v ?: 0} this week"
        ReportKeys.TIME_TO_IN_STOCK -> if (v == null) "None moved this week" else duration(v).let { "Usually within ${it.first} ${it.second}" }
        ReportKeys.TAGS_NOT_CONFIRMED -> "${v ?: 0} waiting"
        ReportKeys.WORK_BY_PERSON -> "${plural(v ?: 0, "person", "people")} this week"
        else -> v?.toString() ?: NOT_TRACKED
    }
}

// ---------- One report (§4, §10) ----------

data class TileText(val value: String?, val word: String, val context: String)

private fun Tile.part(key: String): Long? = parts.firstOrNull { it.key == key }?.value

private fun comparisonLine(t: Tile): String {
    val c = t.comparison ?: return ""
    val basis = when (c.basis) {
        "same_days_last_week" -> "Same days last week"
        "week_before" -> "Week before"
        "same_day_last_week" -> "Same day last week"
        "same_days_last_month" -> "Same days last month"
        "previous_30_days" -> "Previous 30 days"
        else -> "Same number of days before"
    }
    return "$basis: ${c.value ?: 0}"
}

fun tileText(report: String, t: Tile): TileText {
    if (!t.tracked) return TileText(null, tileWord(report, t, 2), NOT_TRACKED)
    val v = t.value
    return when (t.key) {
        "in_stock" -> {
            val more = listOfNotNull(t.part("incoming")?.takeIf { it > 0 }?.let { "$it Incoming" }, t.part("salePending")?.takeIf { it > 0 }?.let { "$it Sale pending" })
            TileText("${v ?: 0}", "in stock", if (more.isEmpty()) "" else "Plus " + more.joinToString(" and "))
        }
        "asking_price" -> TileText(dollars(v ?: 0), "asking price", when {
            t.part("priced") != null -> "${t.part("priced")} of ${t.part("units")} priced."
            t.part("units") != null -> "${plural(t.part("units")!!, "unit")} In stock or Sale pending."
            else -> ""
        })
        "in_stock_now" -> TileText("${v ?: 0}", "in stock", "Of the ${t.part("of") ?: 0}. ${t.part("stillIncoming") ?: 0} still Incoming.")
        "usual_minutes" -> {
            val ctx = "${t.part("moved") ?: 0} moved; ${t.part("stillIncoming") ?: 0} still Incoming."
            if (v == null) TileText(null, "None moved yet", ctx) else duration(v).let { TileText("${it.first}", "${it.second} usually", ctx) }
        }
        "incoming_over_3_days" -> TileText("${v ?: 0}", "Incoming over 3 days", "")
        "people" -> TileText("${v ?: 0}", noun(v ?: 0, "person", "people"), comparisonLine(t))
        "asking_price_at_sale" -> TileText(dollars(v ?: 0), "asking price at sale", "")
        else -> TileText("${v ?: 0}", tileWord(report, t, v ?: 0), comparisonLine(t))
    }
}

private fun tileWord(report: String, t: Tile, n: Long): String = when (t.key) {
    "check_ins" -> noun(n, "check-in")
    "printed" -> "printed"
    "reprints" -> noun(n, "reprint")
    "waiting" -> "waiting"
    "sold" -> "sold"
    "missing_price" -> "missing a price"
    else -> t.key.replace('_', ' ')
}

// When the first figure is 0, only one tile shows (§4).
fun shownTiles(dto: ReportDto): List<Tile> =
    if (dto.tiles.firstOrNull()?.let { it.tracked && (it.value ?: 0L) == 0L && it.key != "usual_minutes" } == true) dto.tiles.take(1) else dto.tiles.take(2)

// Nothing in the range (§9): a period report whose first figure is 0.
fun isEmptyPeriod(dto: ReportDto): Boolean = dto.kind == "period" && dto.tiles.firstOrNull()?.let { it.tracked && it.value == 0L } == true

// What each report counts, for row names and the drill-down title.
fun reportNoun(report: String): Pair<String, String> = when (report) {
    ReportKeys.CHECK_INS -> "check-in" to "check-ins"
    ReportKeys.TAGS_PRINTED -> "tag" to "tags"
    ReportKeys.SOLD -> "sale" to "sales"
    ReportKeys.WORK_BY_PERSON -> "check-in" to "check-ins"
    else -> "unit" to "units"
}

fun breakdownName(key: String): String = when (key) {
    "person" -> "Person"; "type" -> "Type"; "make" -> "Make"; "status" -> "Status"
    "day" -> "Day"; "week" -> "Week"; "month" -> "Month"; "reason" -> "Reason"
    "printer" -> "Printer"; "days_waiting" -> "Days waiting"
    else -> key.replaceFirstChar { it.titlecase(US) }
}

data class BarRow(
    val key: String,
    val label: String,
    val status: StatusLook?,
    val count: Long?,
    // Under the name: a second figure the row carries (asking price, a median wait).
    val note: String?,
    // 0..1 of the track; null: no bar (status rows sit beside the split instead).
    val share: Float?,
    val drill: UnitFilter?,
    val spoken: String,
)

fun rowLabel(b: Breakdown, row: BreakdownRow, today: LocalDate): String = when (b.key) {
    "type" -> unitTypeWord(row.label)
    "day" -> parseDate(row.key)?.let { if (it == today) "Today, ${shortDay(it)}" else shortDay(it) } ?: row.label
    "week" -> parseDate(row.key)?.let { "Week of ${shortDate(it)}" } ?: row.label
    "month" -> parseDate(row.key)?.let { it.month.getDisplayName(TextStyle.FULL, US) + if (it.year != today.year) " ${it.year}" else "" } ?: row.label
    "status" -> statusOf(row.key)?.look()?.word ?: row.label
    else -> row.label
}

fun barRows(report: String, b: Breakdown, today: LocalDate): List<BarRow> {
    val max = b.rows.maxOfOrNull { it.count ?: 0 }?.coerceAtLeast(1) ?: 1
    val (one, many) = reportNoun(report)
    return b.rows.map { row ->
        val label = rowLabel(b, row, today)
        val n = row.count ?: 0
        val note = row.figures.firstOrNull()?.let { f ->
            when {
                f.key == "askingPriceCents" && f.value != null -> "${dollars(f.value!!)} asking price"
                f.key == "medianMinutes" -> f.value?.let { duration(it).let { d -> "Usually ${d.first} ${d.second}" } } ?: "None moved"
                else -> null
            }
        }
        val status = if (b.key == "status") statusOf(row.key)?.look() else null
        BarRow(
            key = row.key, label = label, status = status, count = row.count, note = note,
            share = if (status != null) null else n.toFloat() / max,
            drill = row.drill,
            spoken = "$label, ${n} ${noun(n, one, many)}${note?.let { ". $it" } ?: ""}." + if (row.drill != null) " Opens the list." else "",
        )
    }
}

// The status split (Units by status): the list's first, non-tappable row.
data class SplitPart(val look: StatusLook, val count: Long)

fun statusSplit(b: Breakdown): List<SplitPart> = b.rows.mapNotNull { r -> statusOf(r.key)?.let { SplitPart(it.look(), r.count ?: 0) } }.filter { it.count > 0 }

// The drill-down list's title: "Dale's check-ins", "Two wheels check-ins", "In stock units".
fun drillTitle(report: String, breakdown: String?, label: String): String {
    val many = reportNoun(report).second
    return when (breakdown) {
        "person" -> "$label's $many"
        null -> label
        else -> "$label $many"
    }
}

// "This week, Mon 26 Oct to today. 6 units, newest first." / "Right now. 37 units, newest first."
fun drillMeta(range: RangeShown?, today: LocalDate, total: Long, sort: String): String {
    val order = if (sort == UnitFilter.SORT_OLDEST_CHECKED_IN) "oldest first" else "newest first"
    val head = range?.let { "${it.title(today)}, ${it.dates(today)}." } ?: "Right now."
    return "$head ${plural(total, "unit")}, $order."
}

// Footnotes (§4 item 8): what is counted and from when.
fun footnote(report: String): String? = when (report) {
    ReportKeys.UNITS_BY_STATUS -> "Asking price adds the prices of units In stock or Sale pending."
    ReportKeys.ASKING_PRICE_TOTAL -> "Asking price is each unit's price field. Units missing a price aren't tracked yet."
    ReportKeys.CHECK_INS -> "Counted on the day the work was done, in shop time. A check-in made offline counts on the day it was made."
    ReportKeys.TIME_TO_IN_STOCK -> "From check-in to the first scan of the box tag. Usually is the middle value."
    ReportKeys.TAGS_PRINTED -> "A reprint is any tag after a unit's first."
    ReportKeys.TAGS_NOT_CONFIRMED -> "Incoming units with a printed tag that hasn't been scanned yet. Days count from the newest tag."
    ReportKeys.SOLD -> "Sold counts units marked sold. Until sale prices are recorded, the money figure is the asking price at the time."
    ReportKeys.WORK_BY_PERSON -> "Moved to In stock counts tag confirms done in the range, including units checked in earlier. Edits count changes to unit details."
    else -> null
}

// The empty notice (§9): "No check-ins today, so there's nothing to export."
fun emptyNotice(report: String, range: RangeShown, today: LocalDate, canExport: Boolean): Pair<String, String> {
    val many = reportNoun(report).second
    val first = "No $many ${range.phrase(today)}" + if (canExport && report == ReportKeys.CHECK_INS) ", so there's nothing to export." else "."
    val unit = range.unit().let { if (it == StepUnit.DAYS) "range" else it.word }
    return first to "Step back a $unit with the arrow, or pick another range."
}

// ---------- Chart (§6) ----------

data class ChartSlot(
    val label: String,
    val value: Long?,
    val today: Boolean,
    // Full name for TalkBack and the readout: "Thursday 29 October".
    val spoken: String,
    val readoutDate: String,
)

data class ChartModel(val heading: String, val slots: List<ChartSlot>, val top: Long, val ticks: List<Long>, val groupName: String)

private fun niceStep(raw: Double): Long {
    if (raw <= 1) return 1
    val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val f = raw / mag
    return (when { f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 5 -> 5.0; else -> 10.0 } * mag).toLong()
}

// A trend in columns, one per day, week or month. This week also shows the days not reached
// yet as labels with no column, so the week reads Mon to Sun.
fun chartModel(report: String, series: Series?, range: RangeShown, today: LocalDate): ChartModel? {
    if (series == null || series.points.isEmpty()) return null
    val (one, many) = reportNoun(report)
    val pts = series.points.mapNotNull { p -> parseDate(p.from)?.let { it to p } }
    val slots = pts.map { (d, p) ->
        val isToday = series.bucket == "day" && d == today
        val label = when (series.bucket) {
            "day" -> if (isToday) "Today" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, US)
            "week" -> shortDate(d)
            else -> d.month.getDisplayName(TextStyle.SHORT, US)
        }
        val full = when (series.bucket) {
            "day" -> fullDay(d)
            "week" -> "Week of ${fullDay(d)}"
            else -> d.month.getDisplayName(TextStyle.FULL, US) + " ${d.year}"
        }
        ChartSlot(label, p.value, isToday, (if (isToday) "Today, " else "") + full, if (series.bucket == "day") shortDay(d) else label)
    }.toMutableList()
    if (series.bucket == "day" && range.query.preset == Presets.THIS_WEEK) {
        var d = pts.last().first.plusDays(1)
        while (slots.size < 7) {
            slots += ChartSlot(d.dayOfWeek.getDisplayName(TextStyle.SHORT, US), null, false, fullDay(d), shortDay(d)); d = d.plusDays(1)
        }
    }
    val max = slots.maxOf { it.value ?: 0 }
    val step = niceStep(max / 3.0)
    val top = step * ((max + step) / step)
    val heading = when (series.bucket) { "day" -> "By day"; "week" -> "By week"; else -> "By month" }
    val values = pts.joinToString(", ") { (d, p) -> "${if (series.bucket == "day") d.dayOfWeek.getDisplayName(TextStyle.FULL, US) else shortDate(d)} ${p.value ?: 0}" }
    return ChartModel(heading, slots, top, (0..top step step).toList(), "${reportName(report)} ${heading.lowercase(US)}, ${range.phrase(today)}: $values.")
}

fun chartValue(report: String, v: Long): String = reportNoun(report).let { (one, many) -> plural(v, one, many) }

// ---------- Work by person (§10) ----------

data class PersonRow(val id: String, val name: String, val checkIns: Long, val moved: Long, val edits: Long, val spoken: String)

fun personRows(dto: ReportDto): List<PersonRow> = dto.breakdowns.firstOrNull { it.key == "person" }?.rows.orEmpty().map { r ->
    fun f(k: String) = r.figures.firstOrNull { it.key == k }?.value ?: 0
    val c = f("checkIns"); val m = f("movedToInStock"); val e = f("edits")
    PersonRow(r.personId ?: r.key, r.label, c, m, e,
        "${r.label}: ${plural(c, "check-in")}, $m moved to In stock, ${plural(e, "edit")}. Opens ${possessive(r.label)} work.")
}

fun possessive(name: String) = if (name.endsWith("s")) "$name'" else "$name's"

// ---------- Export (§8) ----------

data class ExportChoice(val rows: String, val title: String, val line: String)

fun exportChoices(dto: ReportDto, by: String?, today: LocalDate): List<ExportChoice> {
    val each = dto.tiles.firstOrNull()?.value ?: 0
    val table = dto.breakdowns.firstOrNull { it.key == by } ?: dto.breakdowns.firstOrNull()
    val n = table?.rows?.size?.toLong() ?: 0
    return listOf(
        ExportChoice("each", "Each check-in", "${plural(each, "row")}. One line per unit, for sorting and filtering."),
        ExportChoice("table", "This table", "${plural(n, "row")}. The breakdown on screen, by ${breakdownName(table?.key ?: "person").lowercase(US)}."),
    )
}

fun exportLine(range: RangeShown, today: LocalDate): String =
    "${range.title(today)}, " + if (range.from == range.to) "${shortDay(range.from)}." else "${shortDay(range.from)} to ${shortDay(range.to)}."
