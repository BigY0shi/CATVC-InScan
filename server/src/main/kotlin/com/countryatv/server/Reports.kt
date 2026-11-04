package com.countryatv.server

import com.countryatv.core.Problem
import com.countryatv.core.UnitStatus
import com.countryatv.core.reports.Breakdown
import com.countryatv.core.reports.BreakdownRow
import com.countryatv.core.reports.Comparison
import com.countryatv.core.reports.Figure
import com.countryatv.core.reports.FigureUnits
import com.countryatv.core.reports.HistoryKinds
import com.countryatv.core.reports.Median
import com.countryatv.core.reports.ReportDto
import com.countryatv.core.reports.ReportKeys
import com.countryatv.core.reports.ReportSummary
import com.countryatv.core.reports.Series
import com.countryatv.core.reports.SeriesPoint
import com.countryatv.core.reports.SummaryFigure
import com.countryatv.core.reports.Tile
import com.countryatv.core.reports.UnitFilter
import io.ktor.http.HttpStatusCode
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// A range a report was asked for: a preset (resolved on the server, reports spec §5) or custom dates.
data class ResolvedRange(val preset: RangePreset?, val range: ShopRange)

// ?preset= or ?from=&to= (shop dates, yyyy-MM-dd, inclusive). Neither means This week.
fun resolveRange(calendar: ShopCalendar, preset: String?, from: String?, to: String?): ResolvedRange {
    fun bad(why: String): Nothing = throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf(why))
    if (preset != null && (from != null || to != null)) bad("a preset or dates, not both")
    if (from != null || to != null) {
        if (from == null || to == null) bad("from and to go together")
        val f = runCatching { LocalDate.parse(from) }.getOrElse { bad("from") }
        val t = runCatching { LocalDate.parse(to) }.getOrElse { bad("to") }
        if (t.isBefore(f)) bad("to is before from")
        if (t.isAfter(calendar.today())) bad("dates in the future")
        return ResolvedRange(null, calendar.range(f, t))
    }
    val p = preset?.let { RangePreset.byKey(it) ?: bad("preset") } ?: RangePreset.THIS_WEEK
    return ResolvedRange(p, calendar.range(p))
}

// The range a period figure is compared with (reports spec §4): like with like while a period is
// still running ("same days last week"), the whole period before once it's finished.
fun comparisonRange(calendar: ShopCalendar, r: ResolvedRange): Pair<String, ShopRange> {
    val from = r.range.from
    val to = r.range.to
    return when (r.preset) {
        RangePreset.TODAY -> "same_day_last_week" to calendar.range(from.minusWeeks(1), to.minusWeeks(1))
        RangePreset.THIS_WEEK -> "same_days_last_week" to calendar.range(from.minusWeeks(1), to.minusWeeks(1))
        RangePreset.LAST_WEEK -> "week_before" to calendar.range(from.minusWeeks(1), to.minusWeeks(1))
        RangePreset.THIS_MONTH -> "same_days_last_month" to calendar.range(from.minusMonths(1), to.minusMonths(1))
        RangePreset.LAST_30_DAYS -> "previous_30_days" to calendar.range(from.minusDays(30), to.minusDays(30))
        null -> "previous_days" to calendar.range(from.minusDays(r.range.days), from.minusDays(1))
    }
}

// Everything one report needs: the connection, the shop calendar, the pinned asOf, and names.
class ReportContext(val c: Connection, val calendar: ShopCalendar, val asOf: String, val asOfInstant: Instant) {
    val names: Map<String, String> by lazy { allNames(c) }
    fun name(id: String) = names[id] ?: "Someone no longer in the shop system"
}

// Builds every report in the first-release slice (reports spec §17 item 3). Each report is one
// function; /reports/summary takes each report's tiles[0] from that same function.
class ReportService(private val db: Database, private val clock: Clock, private val settings: ShopSettings) {

    private val snapshot = setOf(ReportKeys.UNITS_BY_STATUS, ReportKeys.ASKING_PRICE_TOTAL, ReportKeys.TAGS_NOT_CONFIRMED)

    fun report(key: String, preset: String?, from: String?, to: String?): ReportDto {
        if (key !in ReportKeys.BUILT) throw ApiProblem(HttpStatusCode.NotFound, Problem.REPORT_NOT_FOUND)
        val calendar = settings.calendar()
        val now = clock.instant()
        // Snapshots have no range; a range sent to one is ignored rather than refused.
        val range = if (key in snapshot) null else resolveRange(calendar, preset, from, to)
        return db.read { c -> build(key, ReportContext(c, calendar, stampOf(now), now), range) }
    }

    fun summary(): ReportSummary {
        val calendar = settings.calendar()
        val now = clock.instant()
        val week = resolveRange(calendar, RangePreset.THIS_WEEK.key, null, null)
        return db.read { c ->
            val ctx = ReportContext(c, calendar, stampOf(now), now)
            val figures = ReportKeys.BUILT.map { key ->
                val dto = build(key, ctx, if (key in snapshot) null else week)
                SummaryFigure(key, dto.kind, dto.tiles.first())
            }
            ReportSummary(ctx.asOf, RangePreset.THIS_WEEK.key, week.range.from.toString(), week.range.to.toString(), figures, ReportKeys.NOT_BUILT)
        }
    }

    // For the export: the check-in cohort and its report, built in one read.
    fun <T> withReport(key: String, preset: String?, from: String?, to: String?, block: (ReportContext, ResolvedRange, ReportDto) -> T): T {
        val calendar = settings.calendar()
        val now = clock.instant()
        val range = resolveRange(calendar, preset, from, to)
        return db.read { c ->
            val ctx = ReportContext(c, calendar, stampOf(now), now)
            block(ctx, range, build(key, ctx, range))
        }
    }

    private fun build(key: String, ctx: ReportContext, r: ResolvedRange?): ReportDto = when (key) {
        ReportKeys.UNITS_BY_STATUS -> unitsByStatus(ctx)
        ReportKeys.ASKING_PRICE_TOTAL -> askingPriceTotal(ctx)
        ReportKeys.CHECK_INS -> checkIns(ctx, r!!)
        ReportKeys.TIME_TO_IN_STOCK -> timeToInStock(ctx, r!!)
        ReportKeys.TAGS_PRINTED -> tagsPrinted(ctx, r!!)
        ReportKeys.TAGS_NOT_CONFIRMED -> tagsNotConfirmed(ctx)
        ReportKeys.SOLD -> sold(ctx, r!!)
        ReportKeys.WORK_BY_PERSON -> workByPerson(ctx, r!!)
        else -> throw ApiProblem(HttpStatusCode.NotFound, Problem.REPORT_NOT_FOUND)
    }

    // ---- shared pieces ----

    private fun snapshotDto(key: String, ctx: ReportContext, tiles: List<Tile>, breakdowns: List<Breakdown>) =
        ReportDto(key, "snapshot", ctx.asOf, tiles = tiles, breakdowns = breakdowns)

    private fun periodDto(key: String, ctx: ReportContext, r: ResolvedRange, tiles: List<Tile>, series: Series?, breakdowns: List<Breakdown>) =
        ReportDto(key, "period", ctx.asOf, r.preset?.key, r.range.from.toString(), r.range.to.toString(),
            stampOf(r.range.startUtc), stampOf(r.range.endUtc), tiles, series, breakdowns)

    private fun count(ctx: ReportContext, f: UnitFilter) = UnitQuery.count(ctx.c, f)

    private fun comparison(ctx: ReportContext, r: ResolvedRange, value: (ShopRange) -> Long): Comparison {
        val (basis, prev) = comparisonRange(ctx.calendar, r)
        return Comparison(basis, prev.from.toString(), prev.to.toString(), value(prev))
    }

    // Rows for one dimension, largest first (then by label), each with its drill.
    private fun grouped(ctx: ReportContext, key: String, f: UnitFilter, dim: UnitDimension, sumExpr: Sql? = null, sumKey: String? = null,
                        label: (GroupCount) -> String = { it.label }): Breakdown {
        val rows = UnitQuery.groupCounts(ctx.c, f, dim, sumExpr).map { g ->
            BreakdownRow(g.key, label(g), g.count,
                figures = if (sumKey != null) listOf(Figure(sumKey, g.sum, FigureUnits.CENTS)) else emptyList(),
                drill = UnitQuery.narrow(f, dim, g.key))
        }
        return Breakdown(key, rows.sortedWith(compareByDescending<BreakdownRow> { it.count }.thenBy { it.label.lowercase() }))
    }

    private fun personLabel(ctx: ReportContext): (GroupCount) -> String = { ctx.name(it.key) }

    // Status rows in their natural order, zero rows included.
    private fun statusRows(ctx: ReportContext, f: UnitFilter, statuses: List<UnitStatus>, sumExpr: Sql? = null): Breakdown =
        Breakdown("status", statuses.map { s ->
            val drill = f.copy(statuses = listOf(s.name))
            BreakdownRow(s.name, s.label, count(ctx, drill),
                figures = if (sumExpr != null) listOf(Figure("askingPriceCents", UnitQuery.sum(ctx.c, drill, sumExpr) ?: 0, FigureUnits.CENTS)) else emptyList(),
                drill = drill)
        })

    // The time breakdown and the chart share buckets (reports spec §6). A single day has neither.
    private fun timeRows(ctx: ReportContext, r: ResolvedRange, count: (Bucket) -> Long, drill: ((Bucket) -> UnitFilter)?): Pair<Series?, Breakdown?> {
        val size = ctx.calendar.bucketSize(r.range)
        if (size == BucketSize.NONE) return null to null
        val bucketName = size.name.lowercase()
        val buckets = ctx.calendar.buckets(r.range)
        val values = buckets.map(count)
        val series = Series(bucketName, buckets.zip(values).map { (b, v) -> SeriesPoint(b.from.toString(), b.to.toString(), v) })
        val rows = buckets.zip(values).map { (b, v) ->
            BreakdownRow(b.from.toString(), if (b.from == b.to) b.from.toString() else "${b.from} to ${b.to}", v, drill = drill?.invoke(b))
        }
        return series to Breakdown(bucketName, rows)
    }

    private val price = Sql("u.price_cents")

    // ---- Inventory ----

    private val notSold = listOf(UnitStatus.INCOMING, UnitStatus.IN_STOCK, UnitStatus.SALE_PENDING)
    private val forSale = listOf(UnitStatus.IN_STOCK, UnitStatus.SALE_PENDING)

    private fun unitsByStatus(ctx: ReportContext): ReportDto {
        val base = UnitFilter(asOf = ctx.asOf, statuses = notSold.map { it.name })
        val inStock = base.copy(statuses = listOf(UnitStatus.IN_STOCK.name))
        val priced = base.copy(statuses = forSale.map { it.name })
        val tiles = listOf(
            Tile("in_stock", count(ctx, inStock), parts = listOf(
                Figure("incoming", count(ctx, base.copy(statuses = listOf(UnitStatus.INCOMING.name)))),
                Figure("salePending", count(ctx, base.copy(statuses = listOf(UnitStatus.SALE_PENDING.name))))
            ), drill = inStock),
            Tile("asking_price", UnitQuery.sum(ctx.c, priced, price) ?: 0, FigureUnits.CENTS, parts = listOf(Figure("units", count(ctx, priced))), drill = priced)
        )
        return snapshotDto(ReportKeys.UNITS_BY_STATUS, ctx, tiles, listOf(
            statusRows(ctx, base, notSold),
            grouped(ctx, "type", base, UnitDimension.TYPE),
            grouped(ctx, "make", base, UnitDimension.MAKE)
        ))
    }

    private fun askingPriceTotal(ctx: ReportContext): ReportDto {
        val p = UnitFilter(asOf = ctx.asOf, statuses = forSale.map { it.name })
        val units = count(ctx, p)
        val tiles = listOf(
            // Every unit has a price until D-C2 lets it be empty, so priced equals units for now.
            Tile("asking_price", UnitQuery.sum(ctx.c, p, price) ?: 0, FigureUnits.CENTS,
                parts = listOf(Figure("priced", units), Figure("units", units)), drill = p),
            // Needs D-C2 (price can be empty). Not tracked yet, never 0.
            Tile("missing_price", null, tracked = false)
        )
        return snapshotDto(ReportKeys.ASKING_PRICE_TOTAL, ctx, tiles, listOf(
            statusRows(ctx, p, forSale, price),
            grouped(ctx, "type", p, UnitDimension.TYPE, price, "askingPriceCents"),
            grouped(ctx, "make", p, UnitDimension.MAKE, price, "askingPriceCents")
        ))
    }

    // ---- Intake ----

    // Units checked in during a range: not imports, as known at asOf, newest first.
    fun checkInCohort(ctx: ReportContext, range: ShopRange) =
        UnitFilter(asOf = ctx.asOf, imported = false, checkedInFrom = stampOf(range.startUtc), checkedInTo = stampOf(range.endUtc))

    private fun bucketRange(b: Bucket) = ShopRange(b.from, b.to, b.startUtc, b.endUtc)

    private fun checkIns(ctx: ReportContext, r: ResolvedRange): ReportDto {
        val cohort = checkInCohort(ctx, r.range)
        val total = count(ctx, cohort)
        val inStock = cohort.copy(statuses = listOf(UnitStatus.IN_STOCK.name))
        val tiles = listOf(
            Tile("check_ins", total, comparison = comparison(ctx, r) { count(ctx, checkInCohort(ctx, it)) }, drill = cohort),
            Tile("in_stock_now", count(ctx, inStock), parts = listOf(
                Figure("of", total),
                Figure("stillIncoming", count(ctx, cohort.copy(statuses = listOf(UnitStatus.INCOMING.name))))
            ), drill = inStock)
        )
        val (series, time) = timeRows(ctx, r, { count(ctx, checkInCohort(ctx, bucketRange(it))) }, { checkInCohort(ctx, bucketRange(it)) })
        return periodDto(ReportKeys.CHECK_INS, ctx, r, tiles, series, listOfNotNull(
            grouped(ctx, "person", cohort, UnitDimension.CHECKED_IN_BY, label = personLabel(ctx)),
            grouped(ctx, "type", cohort, UnitDimension.TYPE),
            grouped(ctx, "make", cohort, UnitDimension.MAKE),
            time
        ))
    }

    // Minutes from check-in to the first move to In stock, for the units in a filter, keyed by a dimension.
    private fun waits(ctx: ReportContext, f: UnitFilter, dim: UnitDimension): Map<String, List<Long>> {
        val extra = Sql("").add(UnitQuery.firstMove("occurred_at", f.asOf)).add(" AS moved_at, ").add(UnitQuery.dimensionExpr(dim, f)).add(" AS g")
        return UnitQuery.rows(ctx.c, f, null, null, extra) { rs ->
            val minutes = Duration.between(Instant.parse(rs.getString("checked_in_at")), Instant.parse(rs.getString("moved_at"))).toMinutes().coerceAtLeast(0)
            (rs.getString("g") ?: "") to minutes
        }.groupBy({ it.first }, { it.second })
    }

    private fun timeToInStock(ctx: ReportContext, r: ResolvedRange): ReportDto {
        val cohort = checkInCohort(ctx, r.range)
        val moved = cohort.copy(movedToInStock = true, sort = UnitFilter.SORT_OLDEST_CHECKED_IN)
        val allWaits = waits(ctx, moved, UnitDimension.TYPE).values.flatten()
        val waitingSince = stampOf(ctx.asOfInstant.minus(Duration.ofDays(3)))
        val longWait = UnitFilter(asOf = ctx.asOf, statuses = listOf(UnitStatus.INCOMING.name), checkedInTo = waitingSince, sort = UnitFilter.SORT_OLDEST_CHECKED_IN)
        val tiles = listOf(
            Tile("usual_minutes", Median.of(allWaits), FigureUnits.MINUTES, parts = listOf(
                Figure("moved", allWaits.size.toLong()),
                Figure("stillIncoming", count(ctx, cohort.copy(statuses = listOf(UnitStatus.INCOMING.name))))
            ), drill = moved),
            Tile("incoming_over_3_days", count(ctx, longWait), drill = longWait)
        )
        fun withMedians(b: Breakdown, dim: UnitDimension): Breakdown {
            val byKey = waits(ctx, moved, dim)
            return b.copy(rows = b.rows.map { it.copy(figures = listOf(Figure("medianMinutes", Median.of(byKey[it.key].orEmpty()), FigureUnits.MINUTES))) })
        }
        return periodDto(ReportKeys.TIME_TO_IN_STOCK, ctx, r, tiles, null, listOf(
            withMedians(grouped(ctx, "person", moved, UnitDimension.CONFIRMED_BY, label = personLabel(ctx)), UnitDimension.CONFIRMED_BY),
            withMedians(grouped(ctx, "type", moved, UnitDimension.TYPE), UnitDimension.TYPE)
        ))
    }

    private data class Print(val printedAt: String, val printedBy: String, val printedVia: String, val reason: String, val reprint: Boolean)

    // Tag prints in a range, as known at asOf. A reprint is any tag after a unit's first.
    private fun prints(ctx: ReportContext, range: ShopRange): List<Print> = ctx.c.query(Sql(
        """SELECT t.printed_at, t.printed_by, t.printed_via, t.reprint_reason,
             EXISTS (SELECT 1 FROM tags t2 WHERE t2.unit_id = t.unit_id AND (t2.printed_at < t.printed_at OR (t2.printed_at = t.printed_at AND t2.rowid < t.rowid))) AS reprint
           FROM tags t WHERE t.printed_at >= ? AND t.printed_at < ?
             AND EXISTS (SELECT 1 FROM audit_log a WHERE a.entity_id = t.id AND a.action = 'tag.print' AND a.at <= ?)""",
        stampOf(range.startUtc), stampOf(range.endUtc), ctx.asOf)) { Print(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5) == 1) }

    private fun tagsPrinted(ctx: ReportContext, r: ResolvedRange): ReportDto {
        val all = prints(ctx, r.range)
        val tiles = listOf(
            Tile("printed", all.size.toLong(), comparison = comparison(ctx, r) { prints(ctx, it).size.toLong() }),
            Tile("reprints", all.count { it.reprint }.toLong())
        )
        // Prints are events, not units: these rows don't open a unit list (reports spec §7).
        fun rows(key: String, by: (Print) -> Pair<String, String>) = Breakdown(key, all.groupBy(by).map { (k, v) -> BreakdownRow(k.first, k.second, v.size.toLong()) }
            .sortedWith(compareByDescending<BreakdownRow> { it.count }.thenBy { it.label.lowercase() }))
        val (series, time) = timeRows(ctx, r, { b -> all.count { it.printedAt >= stampOf(b.startUtc) && it.printedAt < stampOf(b.endUtc) }.toLong() }, null)
        return periodDto(ReportKeys.TAGS_PRINTED, ctx, r, tiles, series, listOfNotNull(
            rows("reason") { p -> if (!p.reprint) "first" to "First tag" else (p.reason.ifBlank { "reprint" }) to p.reason.ifBlank { "Reprint, no reason given" } },
            rows("person") { it.printedBy to ctx.name(it.printedBy) },
            rows("printer") { it.printedVia to it.printedVia },
            time
        ))
    }

    private fun tagsNotConfirmed(ctx: ReportContext): ReportDto {
        val p = UnitFilter(asOf = ctx.asOf, statuses = listOf(UnitStatus.INCOMING.name), hasTag = true, sort = UnitFilter.SORT_OLDEST_CHECKED_IN)
        fun ago(days: Long) = stampOf(ctx.asOfInstant.minus(Duration.ofDays(days)))
        // Days since the unit's newest tag was printed, in 24-hour steps from asOf.
        val bands = listOf(
            Triple("under_1_day", "Under 1 day", p.copy(newestTagPrintedFrom = ago(1))),
            Triple("1_to_2_days", "1 to 2 days", p.copy(newestTagPrintedFrom = ago(3), newestTagPrintedTo = ago(1))),
            Triple("3_to_6_days", "3 to 6 days", p.copy(newestTagPrintedFrom = ago(7), newestTagPrintedTo = ago(3))),
            Triple("7_days_or_more", "7 days or more", p.copy(newestTagPrintedTo = ago(7)))
        )
        return snapshotDto(ReportKeys.TAGS_NOT_CONFIRMED, ctx, listOf(Tile("waiting", count(ctx, p), drill = p)), listOf(
            Breakdown("days_waiting", bands.map { (k, label, f) -> BreakdownRow(k, label, count(ctx, f), drill = f) }),
            grouped(ctx, "person", p, UnitDimension.TAG_PRINTED_BY, label = personLabel(ctx))
        ))
    }

    // ---- Sales ----

    private fun soldIn(ctx: ReportContext, range: ShopRange) =
        UnitFilter(asOf = ctx.asOf, soldFrom = stampOf(range.startUtc), soldTo = stampOf(range.endUtc))

    private fun sold(ctx: ReportContext, r: ResolvedRange): ReportDto {
        val f = soldIn(ctx, r.range)
        val atSale = UnitQuery.latestSale("price_cents", f.soldFrom!!, f.soldTo!!, ctx.asOf)
        val tiles = listOf(
            Tile("sold", count(ctx, f), comparison = comparison(ctx, r) { count(ctx, soldIn(ctx, it)) }, drill = f),
            // Interim mark-sold records no sale price: this is the asking price at the time (until M3).
            Tile("asking_price_at_sale", UnitQuery.sum(ctx.c, f, atSale) ?: 0, FigureUnits.CENTS, drill = f)
        )
        val (series, time) = timeRows(ctx, r, { count(ctx, soldIn(ctx, bucketRange(it))) }, { soldIn(ctx, bucketRange(it)) })
        return periodDto(ReportKeys.SOLD, ctx, r, tiles, series, listOfNotNull(
            grouped(ctx, "type", f, UnitDimension.TYPE),
            grouped(ctx, "make", f, UnitDimension.MAKE),
            grouped(ctx, "person", f, UnitDimension.SOLD_BY, label = personLabel(ctx)),
            time
        ))
    }

    // ---- People ----

    private fun workByPerson(ctx: ReportContext, r: ResolvedRange): ReportDto {
        val events = HistoryClassifier.events(ctx.c, null, stampOf(r.range.startUtc), stampOf(r.range.endUtc), ctx.asOf)
        val rows = events.groupBy { it.actor }.map { (actor, list) ->
            fun n(kind: String) = list.count { it.event.kind == kind }.toLong()
            BreakdownRow(actor, ctx.name(actor), figures = listOf(
                Figure("checkIns", n(HistoryKinds.CHECK_IN)),
                Figure("movedToInStock", n(HistoryKinds.MOVED_TO_IN_STOCK)),
                Figure("edits", n(HistoryKinds.EDIT))
            ), personId = actor)
        }.filter { row -> row.figures.any { (it.value ?: 0) > 0 } }
            .sortedWith(compareByDescending<BreakdownRow> { row -> row.figures.sumOf { it.value ?: 0 } }.thenBy { it.label.lowercase() })
        val cohort = checkInCohort(ctx, r.range)
        val tiles = listOf(
            Tile("people", rows.size.toLong(), FigureUnits.PEOPLE),
            Tile("check_ins", rows.sumOf { it.figures.first().value ?: 0 }, drill = cohort)
        )
        return periodDto(ReportKeys.WORK_BY_PERSON, ctx, r, tiles, null, listOf(Breakdown("person", rows)))
    }
}
