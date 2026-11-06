package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.countryatv.core.reports.Breakdown
import com.countryatv.core.reports.BreakdownRow
import com.countryatv.core.reports.Comparison
import com.countryatv.core.reports.FieldChange
import com.countryatv.core.reports.Figure
import com.countryatv.core.reports.FigureUnits
import com.countryatv.core.reports.HistoryEvent
import com.countryatv.core.reports.HistoryKinds
import com.countryatv.core.reports.HistoryPage
import com.countryatv.core.reports.HistoryTotals
import com.countryatv.core.reports.HistoryUnit
import com.countryatv.core.reports.PersonRef
import com.countryatv.core.reports.ReportDto
import com.countryatv.core.reports.ReportKeys
import com.countryatv.core.reports.ReportSummary
import com.countryatv.core.reports.Series
import com.countryatv.core.reports.SeriesPoint
import com.countryatv.core.reports.SummaryFigure
import com.countryatv.core.reports.Tile
import com.countryatv.core.reports.UnitFilter
import com.example.data.OutboxEntry
import com.example.data.ShopUnitEntity
import com.example.shop.RangeQuery
import com.example.shop.UnitDto
import com.example.ui.inscan.InScanTheme
import com.example.ui.inscan.Palette
import com.example.ui.reports.DateRangeSheet
import com.example.ui.reports.DrillScreen
import com.example.ui.reports.DrillState
import com.example.ui.reports.ExportSheet
import com.example.ui.reports.HistoryScreen
import com.example.ui.reports.HistoryState
import com.example.ui.reports.PhoneStatus
import com.example.ui.reports.Presets
import com.example.ui.reports.Problem
import com.example.ui.reports.ReportPageState
import com.example.ui.reports.ReportScreen
import com.example.ui.reports.ReportSheet
import com.example.ui.reports.ReportsListScreen
import com.example.ui.reports.ReportsListState
import com.example.ui.reports.exportChoices
import com.example.ui.reports.exportLine
import com.example.ui.reports.phoneStatus
import com.example.ui.reports.resolvedRange
import com.example.ui.shell.InBottomBar
import com.example.ui.shell.Tab
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// Reports and My history against the locked mockups (prototypes/reports.html, my-history.html):
// same frame, same sample shop (Fri 30 Oct 2026, 9:38 am). Every DTO here is a test fixture shaped
// exactly like the shop server's answer; nothing is fetched (api = null).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w402dp-h874dp-xhdpi")
class ReportsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("America/Los_Angeles")
    private val today = LocalDate.of(2026, 10, 30)
    private fun at(d: LocalDate, h: Int, m: Int): String = ZonedDateTime.of(d.atTime(h, m), zone).toInstant().toString()
    private val asOf = at(today, 9, 38)
    private fun day(n: Int) = LocalDate.of(2026, 10, 26).plusDays(n.toLong())

    // ---------- Fixtures ----------

    private val week = UnitFilter(asOf = asOf, imported = false, checkedInFrom = "2026-10-26T07:00:00.000Z", checkedInTo = "2026-10-31T07:00:00.000Z")

    private val summary = ReportSummary(asOf, Presets.THIS_WEEK, "2026-10-26", "2026-10-30", listOf(
        SummaryFigure(ReportKeys.UNITS_BY_STATUS, "snapshot", Tile("in_stock", 37)),
        SummaryFigure(ReportKeys.ASKING_PRICE_TOTAL, "snapshot", Tile("asking_price", 31_840_000, FigureUnits.CENTS)),
        SummaryFigure(ReportKeys.CHECK_INS, "period", Tile("check_ins", 14)),
        SummaryFigure(ReportKeys.TIME_TO_IN_STOCK, "period", Tile("usual_minutes", 118, FigureUnits.MINUTES)),
        SummaryFigure(ReportKeys.TAGS_PRINTED, "period", Tile("printed", 17)),
        SummaryFigure(ReportKeys.TAGS_NOT_CONFIRMED, "snapshot", Tile("waiting", 5)),
        SummaryFigure(ReportKeys.SOLD, "period", Tile("sold", 3)),
        SummaryFigure(ReportKeys.WORK_BY_PERSON, "period", Tile("people", 4, FigureUnits.PEOPLE)),
    ), notBuilt = ReportKeys.NOT_BUILT)

    private fun rows(key: String, vararg r: Pair<String, Long>, drill: (String) -> UnitFilter? = { week }) =
        Breakdown(key, r.map { (k, n) -> BreakdownRow(k, k, n, drill = drill(k)) })

    private val checkIns = ReportDto(
        ReportKeys.CHECK_INS, "period", asOf, Presets.THIS_WEEK, "2026-10-26", "2026-10-30", "2026-10-26T07:00:00.000Z", "2026-10-31T07:00:00.000Z",
        tiles = listOf(
            Tile("check_ins", 14, comparison = Comparison("same_days_last_week", "2026-10-19", "2026-10-23", 10), drill = week),
            Tile("in_stock_now", 8, parts = listOf(Figure("of", 14), Figure("stillIncoming", 6))),
        ),
        series = Series("day", listOf(4L, 2, 3, 1, 4).mapIndexed { i, v -> SeriesPoint(day(i).toString(), day(i).toString(), v) }),
        breakdowns = listOf(
            Breakdown("person", listOf("Dale" to 6L, "Ray" to 4L, "Sam" to 3L, "Alex" to 1L).map { (n, c) -> BreakdownRow("id-$n", n, c, drill = week.copy(checkedInBy = "id-$n")) }),
            rows("type", "Dirt Bike" to 5, "Side-by-Side (UTV)" to 4, "ATV" to 3, "Snowmobile" to 1, "Jet Ski" to 1),
            rows("make", "Polaris" to 4, "Can-Am" to 3, "Honda" to 3, "Yamaha" to 2, "Kawasaki" to 1, "Sea-Doo" to 1),
            Breakdown("day", listOf(4L, 2, 3, 1, 4).mapIndexed { i, v -> BreakdownRow(day(i).toString(), day(i).toString(), v, drill = week) }),
        ),
    )

    private val emptyToday = ReportDto(
        ReportKeys.CHECK_INS, "period", at(today.plusDays(1), 7, 52), Presets.TODAY, "2026-10-31", "2026-10-31",
        tiles = listOf(Tile("check_ins", 0, comparison = Comparison("same_day_last_week", "2026-10-24", "2026-10-24", 4)), Tile("in_stock_now", 0)),
        breakdowns = listOf(Breakdown("person", emptyList())),
    )

    private val byStatus = ReportDto(
        ReportKeys.UNITS_BY_STATUS, "snapshot", asOf,
        tiles = listOf(
            Tile("in_stock", 37, parts = listOf(Figure("incoming", 6), Figure("salePending", 2))),
            Tile("asking_price", 31_840_000, FigureUnits.CENTS, parts = listOf(Figure("units", 39))),
        ),
        breakdowns = listOf(
            Breakdown("status", listOf("INCOMING" to 6L, "IN_STOCK" to 37L, "SALE_PENDING" to 2L).map { (k, n) -> BreakdownRow(k, k, n, drill = UnitFilter(asOf = asOf, statuses = listOf(k))) }),
            rows("type", "Side-by-Side (UTV)" to 18, "ATV" to 14, "Dirt Bike" to 8, "Snowmobile" to 3, "Jet Ski" to 2),
            rows("make", "Polaris" to 15, "Honda" to 10, "Can-Am" to 8, "Yamaha" to 7, "Kawasaki" to 5),
        ),
    )

    private val people = ReportDto(
        ReportKeys.WORK_BY_PERSON, "period", asOf, Presets.THIS_WEEK, "2026-10-26", "2026-10-30",
        tiles = listOf(Tile("people", 4, FigureUnits.PEOPLE), Tile("check_ins", 14)),
        breakdowns = listOf(Breakdown("person", listOf(
            listOf("Dale", 6L, 4L, 3L), listOf("Ray", 4L, 3L, 1L), listOf("Sam", 3L, 2L, 0L), listOf("Alex", 1L, 0L, 2L),
        ).map { r -> BreakdownRow("id-${r[0]}", r[0] as String, figures = listOf(Figure("checkIns", r[1] as Long), Figure("movedToInStock", r[2] as Long), Figure("edits", r[3] as Long)), personId = "id-${r[0]}") })),
    )

    private val crockford = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private fun ulid(at: Instant, short: String): String {
        var t = at.toEpochMilli(); val head = CharArray(10)
        for (i in 9 downTo 0) { head[i] = crockford[(t and 31).toInt()]; t = t ushr 5 }
        return String(head) + "00000000" + short
    }

    private fun dto(short: String, d: LocalDate, h: Int, make: String, model: String, type: String, color: String, status: String, price: Long, inStock: Boolean = false): UnitDto {
        val at = ZonedDateTime.of(d.atTime(h, 0), zone).toInstant()
        return UnitDto(ulid(at, short), "1XYZ${short}0000000", "ok", null, 2026, make, model, "", color, type, price * 100, status, "", 1,
            at.toString(), "", at.toString(), "", at.toString(), if (inStock) at.plusSeconds(7200).toString() else null)
    }

    private val daleUnits = listOf(
        dto("M9PQ4TZE", today, 9, "Polaris", "Ranger 1000", "Side-by-Side (UTV)", "Sage", "INCOMING", 18_999),
        dto("K7RW2PXD", today, 8, "Kawasaki", "KX112", "Dirt Bike", "Green", "INCOMING", 5_499),
        dto("5NVA9HQC", day(2), 15, "Polaris", "Sportsman 570", "ATV", "Grey", "IN_STOCK", 9_499, inStock = true),
        dto("3HZW8RTB", day(1), 13, "Honda", "FourTrax Rancher 4x4", "ATV", "Red", "INCOMING", 7_299),
        dto("H2CW8VKD", day(0), 14, "Honda", "CRF450R", "Dirt Bike", "Black", "IN_STOCK", 9_799, inStock = true),
        dto("B4TN7GWS", day(0), 9, "Yamaha", "YZ250F", "Dirt Bike", "Blue", "INCOMING", 8_999),
    )

    // ---------- Frames ----------

    private fun shoot(name: String, scrollPx: Int = 0, scroll: (() -> androidx.compose.foundation.ScrollState)? = null, content: @Composable () -> Unit) {
        compose.setContent { InScanTheme { Box(Modifier.fillMaxSize().background(Palette.bg)) { content() } } }
        if (scroll != null) { compose.waitForIdle(); runBlocking { scroll().scrollTo(scrollPx) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun WithNav(sheet: @Composable () -> Unit = {}, screen: @Composable (Modifier) -> Unit) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                screen(Modifier.weight(1f))
                InBottomBar(Tab.MORE, {}, {}, moreNeedsAttention = false)
            }
            sheet()
        }
    }

    private fun listState(s: ReportSummary?, p: Problem? = null) = ReportsListState().apply { summary = s; problem = p; loading = s == null && p == null }

    private fun report(dto: ReportDto?, by: String? = null, range: RangeQuery? = null) = ReportPageState(dto?.report ?: ReportKeys.CHECK_INS).apply {
        data = dto; loading = false
        shownRange = range ?: dto?.preset?.let { RangeQuery.preset(it) }
        this.range = shownRange ?: this.range
        this.by = by ?: dto?.breakdowns?.firstOrNull()?.key
    }

    // ---------- Reports ----------

    @Test fun list() = shoot("reports-list") { WithNav { ReportsListScreen(listState(summary), null, zone, {}, {}, it) } }

    @Test fun listLoading() = shoot("reports-list-loading") { WithNav { ReportsListScreen(listState(null), null, zone, {}, {}, it) } }

    @Test fun offline() = shoot("reports-offline") {
        WithNav { ReportsListScreen(listState(null, Problem.Offline("Can't reach the shop system right now.")), null, zone, {}, {}, it) }
    }

    @Test fun refused() = shoot("reports-refused") { WithNav { ReportsListScreen(listState(null, Problem.NotAllowed), null, zone, {}, {}, it) } }

    @Test fun checkins() = shoot("reports-checkins") {
        WithNav { ReportScreen(report(checkIns), null, today, zone, true, {}, {}, { _, _, _ -> }, it) }
    }

    @Test fun checkinsTap() {
        val st = report(checkIns, by = "type").apply { openColumn = 3 }
        shoot("reports-checkins-tap", 600, { st.scroll }) { WithNav { ReportScreen(st, null, today, zone, true, {}, {}, { _, _, _ -> }, it) } }
    }

    @Test fun range() {
        val st = report(checkIns)
        shoot("reports-range") {
            WithNav(sheet = { DateRangeSheet(st.range, today, {}, {}, {}) }) { ReportScreen(st, null, today, zone, true, {}, {}, { _, _, _ -> }, it) }
        }
    }

    @Test fun refetch() {
        val st = report(checkIns).apply { range = RangeQuery.preset(Presets.LAST_WEEK); loading = true; live = "Updating to last week…" }
        shoot("reports-refetch") { WithNav { ReportScreen(st, null, today, zone, true, {}, {}, { _, _, _ -> }, it) } }
    }

    @Test fun empty() = shoot("reports-empty") {
        WithNav { ReportScreen(report(emptyToday), null, today.plusDays(1), zone, true, {}, {}, { _, _, _ -> }, it) }
    }

    @Test fun export() {
        val st = report(checkIns)
        val range = resolvedRange(st.range, checkIns.from, checkIns.to, today)
        shoot("reports-export") {
            WithNav(sheet = { ExportSheet(ReportSheet.Export(), exportChoices(checkIns, "person", today), exportLine(range, today), {}, {}, {}, {}) }) {
                ReportScreen(st, null, today, zone, true, {}, {}, { _, _, _ -> }, it)
            }
        }
    }

    @Test fun drill() {
        val range = resolvedRange(RangeQuery.preset(Presets.THIS_WEEK), "2026-10-26", "2026-10-30", today)
        val st = DrillState(ReportKeys.CHECK_INS, "Dale's check-ins", week.copy(checkedInBy = "id-Dale"), range).apply { items = daleUnits; total = 6 }
        shoot("reports-drill") { WithNav { DrillScreen(st, null, today, zone, Instant.parse(asOf), {}, {}, it) } }
    }

    @Test fun status() = shoot("reports-status") {
        WithNav { ReportScreen(report(byStatus), null, today, zone, true, {}, {}, { _, _, _ -> }, it) }
    }

    @Test fun people() = shoot("reports-people") {
        WithNav { ReportScreen(report(people), null, today, zone, true, {}, {}, { _, _, _ -> }, it) }
    }

    @Test fun reportRefused() = shoot("reports-report-refused") {
        WithNav { ReportScreen(ReportPageState(ReportKeys.CHECK_INS).apply { problem = Problem.NotAllowed; loading = false }, null, today, zone, true, {}, {}, { _, _, _ -> }, it) }
    }

    // ---------- My history ----------

    private fun hu(short: String, make: String, model: String, year: Int = 2026) = HistoryUnit("u-$short", short, year, make, model)
    private var nextId = 100L
    private fun ev(d: LocalDate, h: Int, m: Int, kind: String, u: HistoryUnit, tagAt: Pair<Int, Int>? = null, changes: List<FieldChange> = emptyList(),
                   since: Boolean = false, reason: String? = null) =
        HistoryEvent(nextId--, kind, at(d, h, m), u, tagAt?.let { at(d, it.first, it.second) }, changes, since, reason = reason)

    private val ranger = hu("M9PQ4TZE", "Polaris", "Ranger 1000"); private val kx = hu("K7RW2PXD", "Kawasaki", "KX112")
    private val sportsman = hu("5NVA9HQC", "Polaris", "Sportsman 570"); private val outlander = hu("T3MF8ZJB", "Can-Am", "Outlander 570", 2025)
    private val crf = hu("H2CW8VKD", "Honda", "CRF450R"); private val rancher = hu("3HZW8RTB", "Honda", "FourTrax Rancher 4x4")
    private val maverick = hu("R4KX9BWN", "Can-Am", "Maverick R"); private val yz = hu("B4TN7GWS", "Yamaha", "YZ250F")
    private val talon = hu("J8TP3HMC", "Honda", "Talon 1000X")

    private val weekEvents = listOf(
        ev(today, 9, 21, HistoryKinds.CHECK_IN, ranger, 9 to 24),
        ev(today, 8, 44, HistoryKinds.CHECK_IN, kx, 8 to 47),
        ev(day(3), 16, 12, HistoryKinds.MOVED_TO_IN_STOCK, sportsman),
        ev(day(3), 14, 14, HistoryKinds.EDIT, sportsman, changes = listOf(FieldChange("price", "989900", "949900"))),
        ev(day(3), 11, 5, HistoryKinds.REPRINT, outlander, reason = "damaged"),
        ev(day(2), 15, 40, HistoryKinds.CHECK_IN, sportsman, 15 to 43),
        ev(day(2), 10, 2, HistoryKinds.MOVED_TO_IN_STOCK, crf),
        ev(day(2), 9, 15, HistoryKinds.EDIT, outlander, changes = listOf(FieldChange("price", "799900", "749900", true)), since = true),
        ev(day(1), 13, 30, HistoryKinds.CHECK_IN, rancher, 13 to 33),
        ev(day(1), 11, 20, HistoryKinds.MOVED_TO_IN_STOCK, maverick),
        ev(day(1), 10, 5, HistoryKinds.EDIT, crf, changes = listOf(FieldChange("colour", "Red", "Black"))),
        ev(day(0), 14, 10, HistoryKinds.CHECK_IN, crf, 14 to 12),
        ev(day(0), 9, 5, HistoryKinds.CHECK_IN, yz, 9 to 8),
        ev(day(0), 8, 40, HistoryKinds.MOVED_TO_IN_STOCK, talon),
    )
    private val totals = HistoryTotals(checkIns = 6, stillIncoming = 4, movedToInStock = 4, edits = 3, reprints = 1)
    private fun page(person: PersonRef? = null) = HistoryPage(asOf, Presets.THIS_WEEK, "2026-10-26", "2026-10-30", person, totals, weekEvents)

    private fun history(p: HistoryPage?, personId: String? = null, name: String? = null, prob: Problem? = null) = HistoryState(personId, name).apply {
        page = p; events = p?.events.orEmpty(); shownRange = range; loading = false; problem = prob
    }

    private val noPhone = PhoneStatus(false, emptyList(), 0, null)

    @Test fun mine() = shoot("history-mine") {
        WithNav { HistoryScreen(history(page()), null, noPhone, today, zone, {}, {}, {}, it) }
    }

    @Test fun mineEnd() {
        val st = history(page())
        shoot("history-mine-end", 100_000, { st.scroll }) { WithNav { HistoryScreen(st, null, noPhone, today, zone, {}, {}, {}, it) } }
    }

    private val phoneUnits = listOf(
        ShopUnitEntity(id = "01JB000000" + "00000000" + "K7RW2PXD", vin = null, vinCheck = "", year = 2026, make = "Kawasaki", model = "KX112", subModel = "",
            color = "Green", unitType = "Dirt Bike", priceCents = 419_900, status = "INCOMING", notes = "", version = 1, updatedAt = "", updatedBy = ""),
        ShopUnitEntity(id = "01JB000000" + "00000000" + "B4TN7GWS", vin = null, vinCheck = "", year = 2026, make = "Yamaha", model = "YZ250F", subModel = "",
            color = "Blue", unitType = "Dirt Bike", priceCents = 899_900, status = "IN_STOCK", notes = "", version = 1, updatedAt = "", updatedBy = ""),
    )
    private fun millis(h: Int, m: Int) = Instant.parse(at(today, h, m)).toEpochMilli()

    @Test fun historyOffline() {
        val outbox = listOf(
            OutboxEntry(1, phoneUnits[1].id, "tag_confirm", """{"payload":"x"}""", millis(9, 35)),
            OutboxEntry(2, phoneUnits[0].id, "update", """{"priceCents":419900}""", millis(9, 36)),
        )
        val phone = phoneStatus(outbox, phoneUnits, offline = true, zone = zone)
        shoot("history-offline") {
            WithNav { HistoryScreen(history(null, prob = Problem.Offline("Can't reach the shop system right now.")), null, phone, today, zone, {}, {}, {}, it) }
        }
    }

    @Test fun needsFix() {
        val outbox = listOf(OutboxEntry(3, phoneUnits[1].id, "tag_confirm", """{"payload":"x"}""", millis(9, 35), state = OutboxEntry.STATE_NEEDS_ATTENTION,
            problemMessage = "Scanning the tag on 2026 Yamaha YZ250F (B4TN7GWS): already in stock. Ray moved it at 9:30 am."))
        shoot("history-needs-fix") {
            WithNav { HistoryScreen(history(page()), null, phoneStatus(outbox, phoneUnits, false, zone), today, zone, {}, {}, {}, it) }
        }
    }

    @Test fun historyEmpty() {
        val monday = LocalDate.of(2026, 11, 2)
        val p = HistoryPage(at(monday, 7, 52), Presets.THIS_WEEK, "2026-11-02", "2026-11-02", null, HistoryTotals(0, 0, 0, 0, 0), emptyList())
        shoot("history-empty") { WithNav { HistoryScreen(history(p), null, noPhone, monday, zone, {}, {}, {}, it) } }
    }

    @Test fun manager() = shoot("history-manager") {
        WithNav { HistoryScreen(history(page(PersonRef("id-Dale", "Dale", true)), "id-Dale", "Dale"), null, null, today, zone, {}, {}, {}, it) }
    }

    @Test fun historyRefused() = shoot("history-refused") {
        WithNav { HistoryScreen(history(null, prob = Problem.NotAllowed), null, noPhone, today, zone, {}, {}, {}, it) }
    }
}
