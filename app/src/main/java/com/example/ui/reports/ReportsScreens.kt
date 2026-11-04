package com.example.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.countryatv.core.reports.ReportDto
import com.countryatv.core.reports.ReportKeys
import com.countryatv.core.reports.UnitFilter
import com.example.data.ShopUnitEntity
import com.example.shop.ReportsApi
import com.example.shop.ShopResult
import com.example.shop.UnitDto
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.Space
import com.example.ui.stock.StockRecordCard
import com.example.ui.stock.stockCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// More → Reports (reports-ui-spec.md §3), one report (§4) and its drill-down lists (§7).
// Online only (D-RS3): every figure comes from the shop system as the screen opens.

private const val REFUSAL = "Reports are for managers and admins. Ask an Admin if you need them."

// Swallows touches on a stale (50%) frame, so the old figures are inert while the new range loads.
private fun Modifier.inert(on: Boolean): Modifier = if (!on) this else this.stale(true).pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
}

// ---------- The list ----------

@Composable
fun ReportsListScreen(state: ReportsListState, api: ReportsApi?, zone: ZoneId, onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    if (api != null) LaunchedEffect(state.reload) {
        state.loading = true
        val r = api.reportSummary()
        state.problem = problemOf(r)
        if (r is ShopResult.Ok) state.summary = r.value
        state.loading = false
    }
    var titleBottom by remember { mutableIntStateOf(Int.MAX_VALUE) }
    Column(modifier.fillMaxSize()) {
        ReportBar("More", onBack, "Reports", state.scroll.value > titleBottom)
        val p0 = state.problem
        val under = when {
            p0 == Problem.NotAllowed || (p0 != null && state.summary == null) -> null
            state.summary == null -> "Getting the latest figures."
            else -> updatedLine(state.summary?.asOf, zone)
        }
        ReportBody(state.scroll, { titleBottom = it }, "Reports", under = under) {
            val p = state.problem
            when {
                p == Problem.NotAllowed -> Text(REFUSAL, style = InType.body, color = Palette.text, modifier = Modifier.testTag("reports.refused"))
                p is Problem.Offline && state.summary == null -> PlainNotice("Reports need the shop system.", "This phone isn't connected right now. Connect, then try again.",
                    button = "Try again", icon = Icons.Rounded.Refresh, buttonTag = "reports.retry") { state.reload++ }
                p is Problem.Failed && state.summary == null -> PlainNotice("Couldn't load the reports.", p.message, button = "Try again", icon = Icons.Rounded.Refresh,
                    buttonTag = "reports.retry") { state.reload++ }
                else -> {
                    val summary = state.summary
                    reportList(summary).forEach { g ->
                        Section(g.title) {
                            FlatList {
                                g.rows.forEachIndexed { i, row ->
                                    if (i > 0) Divider()
                                    ListRowView(row, loading = summary == null, onClick = if (row.opens && summary != null) ({ onOpen(row.key) }) else null)
                                }
                            }
                        }
                    }
                    Foot("Each figure says its own range.")
                }
            }
        }
    }
}

@Composable
private fun ListRowView(row: ListRow, loading: Boolean, onClick: (() -> Unit)?) {
    val spoken = "${row.name}, ${row.figure}" + if (onClick != null) ". Opens the report." else ""
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).pressRow(onClick, spoken, "reports.${row.key}").padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.name, style = InType.cardTitle, color = Palette.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Palette.text2, trackColor = Palette.divider, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                } else if (row.lock) {
                    Icon(Icons.Rounded.Lock, null, tint = Palette.text2, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                }
                Text(row.figure, style = InType.body, color = Palette.text)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(24.dp)) { if (onClick != null || loading) Icon(Icons.Rounded.ChevronRight, null, tint = Palette.text2, modifier = Modifier.size(24.dp)) }
    }
}

// ---------- One report ----------

// Loads the report for the current range. Only the newest request counts: a new range cancels the
// call in flight (LaunchedEffect restarts), and a failed refetch puts the previous range back.
@Composable
fun LoadReport(state: ReportPageState, api: ReportsApi, today: LocalDate) {
    LaunchedEffect(state.range, state.reload) {
        val asked = state.range
        val first = state.data == null
        state.loading = true
        state.refetchFailed = false
        if (!first) state.live = "Updating to ${guessRange(asked, today).spoken(today)}…"
        val r = api.report(state.key, if (state.snapshot) com.example.shop.RangeQuery() else asked)
        if (r is ShopResult.Ok) {
            state.data = r.value
            state.shownRange = asked
            state.problem = null
            state.openColumn = null
            if (state.by == null || r.value.breakdowns.none { it.key == state.by }) state.by = r.value.breakdowns.firstOrNull()?.key
            if (!first) state.live = "Showing ${guessRange(asked, today).spoken(today)}"
        } else {
            val p = problemOf(r)
            if (first || p == Problem.NotAllowed) { state.problem = p; if (p == Problem.NotAllowed) state.data = null }
            else {
                state.live = "Couldn't load ${guessRange(asked, today).spoken(today)}. Try again."
                state.refetchFailed = true
                state.shownRange?.let { if (it != asked) state.range = it }
            }
        }
        state.loading = false
    }
}

@Composable
fun ReportScreen(
    state: ReportPageState,
    api: ReportsApi?,
    today: LocalDate,
    zone: ZoneId,
    canExport: Boolean,
    onBack: () -> Unit,
    onDrill: (DrillState) -> Unit,
    onPerson: (id: String, name: String, range: com.example.shop.RangeQuery) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (api != null) LoadReport(state, api, today)
    val name = reportName(state.key)
    val dto = state.data
    val range = dto?.let { if (state.snapshot) null else resolvedRange(state.shownRange ?: state.range, it.from, it.to, today) }
    val asked = if (state.snapshot) null else guessRange(state.range, today).let { g -> if (state.range == state.shownRange && range != null) range else g }
    val exportable = canExport && state.key == ReportKeys.CHECK_INS
    var titleBottom by remember { mutableIntStateOf(Int.MAX_VALUE) }
    Column(modifier.fillMaxSize()) {
        ReportBar("Reports", onBack, name, state.scroll.value > titleBottom,
            right = if (exportable && state.problem != Problem.NotAllowed) ({
                ExportChip(enabled = dto != null && !isEmptyPeriod(dto)) { state.sheet = ReportSheet.Export() }
            }) else null)
        ReportBody(state.scroll, { titleBottom = it }, name,
            under = if (state.snapshot && dto != null && state.problem != Problem.NotAllowed) "Right now. ${updatedLine(dto.asOf, zone)}." else null) {
            val p = state.problem
            if (p == Problem.NotAllowed) { Text(REFUSAL, style = InType.body, color = Palette.text, modifier = Modifier.testTag("report.refused")); return@ReportBody }
            val stale = state.loading && dto != null
            // Range row (period) or the snapshot line.
            if (asked != null) {
                WithHang(null) {
                    RangeRow(asked, today, onPrevious = { state.range = asked.previous() }, onNext = asked.next(today)?.let { n -> { state.range = n } },
                        onOpen = { state.sheet = ReportSheet.Range })
                    LiveLine(state, dto, zone, stale)
                }
            }
            when {
                dto == null && p is Problem.Offline -> PlainNotice("Reports need the shop system.", "This phone isn't connected right now. Connect, then try again.",
                    button = "Try again", icon = Icons.Rounded.Refresh, buttonTag = "report.retry") { state.reload++ }
                dto == null && p is Problem.Failed -> PlainNotice("Couldn't load this report.", p.message, button = "Try again", icon = Icons.Rounded.Refresh,
                    buttonTag = "report.retry") { state.reload++ }
                dto == null -> Busy("Loading…")
                else -> Column(Modifier.inert(stale), verticalArrangement = Arrangement.spacedBy(Space.section)) {
                    ReportContent(state, dto, range, today, exportable, onDrill, onPerson)
                }
            }
        }
    }
}

@Composable
private fun LiveLine(state: ReportPageState, dto: ReportDto?, zone: ZoneId, stale: Boolean) {
    val live = state.live
    when {
        stale && live != null -> Busy(live, Modifier.testTag("report.live"))
        state.refetchFailed && live != null -> Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
            Text(live, style = InType.meta, color = Palette.text, modifier = Modifier.weight(1f).testTag("report.live"))
            com.example.ui.inscan.TextAction("Try again", { state.reload++ })
        }
        dto != null -> Text(updatedLine(dto.asOf, zone) ?: "", style = InType.meta, color = Palette.text2, modifier = Modifier.testTag("report.updated"))
        else -> {}
    }
}

@Composable
private fun ReportContent(
    state: ReportPageState,
    dto: ReportDto,
    range: RangeShown?,
    today: LocalDate,
    exportable: Boolean,
    onDrill: (DrillState) -> Unit,
    onPerson: (String, String, com.example.shop.RangeQuery) -> Unit,
) {
    TileRow(shownTiles(dto).map { tileText(dto.report, it) })
    if (range != null && isEmptyPeriod(dto)) {
        val (a, b) = emptyNotice(dto.report, range, today, exportable)
        PlainNotice(a, b)
        return
    }
    if (dto.report == ReportKeys.WORK_BY_PERSON) {
        PeopleTable(dto, range, today) { id, n -> onPerson(id, n, state.shownRange ?: state.range) }
        footnote(dto.report)?.let { Foot(it) }
        return
    }
    if (range != null) chartModel(dto.report, dto.series, range, today)?.let { m ->
        Section(m.heading) { ColumnChart(m, dto.report, state.openColumn, { state.openColumn = it }) }
    }
    if (dto.breakdowns.isNotEmpty()) Section("Breakdown") {
        val by = state.by ?: dto.breakdowns.first().key
        val keys = dto.breakdowns.map { it.key }
        BreakdownSwitch(keys, by, { state.by = it; state.showAll = false })
        val b = dto.breakdowns.first { it.key == by }
        val rows = barRows(dto.report, b, today)
        val shown = if (rows.size > 8 && !state.showAll) rows.take(7) else rows
        if (rows.isEmpty()) Text("Nothing to break down.", style = InType.body, color = Palette.text2)
        else FlatList {
            if (b.key == "status" && dto.report == ReportKeys.UNITS_BY_STATUS) {
                val split = statusSplit(b)
                if (split.isNotEmpty()) { SplitBar(split); Divider() }
            }
            shown.forEachIndexed { i, row ->
                if (i > 0) Divider()
                BarRowView(row, row.drill?.let { f -> {
                    onDrill(DrillState(dto.report, drillTitle(dto.report, if (b.key == "status") null else b.key, if (b.key == "status") "${row.label} units" else row.label), f, range))
                } }, "report.row.${row.key}")
            }
        }
        if (shown.size < rows.size) RaisedButton("Show all ${rows.size}", { state.showAll = true }, Modifier.fillMaxWidth().testTag("report.showall"))
    }
    footnote(dto.report)?.let { Foot(it) }
}

// Work by person (§10): a list built like a table. Narrow screens stack the figures (measured, not by font scale).
@Composable
private fun PeopleTable(dto: ReportDto, range: RangeShown?, today: LocalDate, onPerson: (String, String) -> Unit) {
    val rows = personRows(dto)
    val title = range?.let { "${it.title(today)}, by person" } ?: "By person"
    Section(title) {
        if (rows.isEmpty()) { Text("Nobody did any work ${range?.phrase(today) ?: "yet"}.", style = InType.body, color = Palette.text2); return@Section }
        BoxWithConstraints {
            val wide = maxWidth >= 340.dp
            FlatList {
                if (wide) Row(Modifier.fillMaxWidth().background(Palette.surface).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
                    .semantics { }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Person", style = InType.meta, color = Palette.text2, modifier = Modifier.weight(1f))
                    HeadCell("Check-ins", 64); HeadCell("Moved to In stock", 72); HeadCell("Edits", 52); Spacer(Modifier.width(8.dp + 24.dp))
                }
                rows.forEach { r ->
                    Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).pressRow({ onPerson(r.id, r.name) }, r.spoken, "people.${r.id}")
                        .padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (wide) {
                            Text(r.name, style = InType.cardTitle, color = Palette.text, modifier = Modifier.weight(1f))
                            ValueCell(r.checkIns, 64); ValueCell(r.moved, 72); ValueCell(r.edits, 52)
                        } else Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            Text(r.name, style = InType.cardTitle, color = Palette.text)
                            Text(plural(r.checkIns, "check-in"), style = InType.meta, color = Palette.text2)
                            Text("${r.moved} moved to In stock", style = InType.meta, color = Palette.text2)
                            Text(plural(r.edits, "edit"), style = InType.meta, color = Palette.text2)
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.ChevronRight, null, tint = Palette.text2, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadCell(text: String, w: Int) {
    Spacer(Modifier.width(8.dp))
    Text(text, style = InType.meta, color = Palette.text2, textAlign = TextAlign.End, modifier = Modifier.width(w.dp))
}

@Composable
private fun ValueCell(v: Long, w: Int) {
    Spacer(Modifier.width(8.dp))
    Text("$v", style = InType.cardTitle, color = Palette.text, textAlign = TextAlign.End, modifier = Modifier.width(w.dp))
}

// ---------- Drill-down (§7) ----------

// The list shape carries everything the Stock card shows; it isn't stored on the phone.
fun UnitDto.asEntity() = ShopUnitEntity(
    id = id, vin = vin, vinCheck = vinCheck, year = year, make = make, model = model, subModel = subModel, color = color,
    unitType = unitType, priceCents = priceCents, status = status, notes = notes, version = version,
    updatedAt = updatedAt, updatedBy = updatedBy, inStockSince = inStockSince,
)

@Composable
fun DrillScreen(state: DrillState, api: ReportsApi?, today: LocalDate, zone: ZoneId, now: Instant, onBack: () -> Unit, onOpenUnit: (String) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    if (api != null) LaunchedEffect(state.reload) {
        if (state.total != null && state.problem == null) return@LaunchedEffect
        val r = api.unitQuery(state.filter)
        state.problem = problemOf(r)
        if (r is ShopResult.Ok) { state.items = r.value.items; state.total = r.value.total; state.next = r.value.next }
    }
    var titleBottom by remember { mutableIntStateOf(Int.MAX_VALUE) }
    Column(modifier.fillMaxSize()) {
        ReportBar(reportName(state.report), onBack, state.title, state.scroll.value > titleBottom)
        ReportBody(state.scroll, { titleBottom = it }, state.title,
            under = state.total?.let { drillMeta(state.range, today, it, state.filter.sort) }) {
            val p = state.problem
            val total = state.total
            when {
                p == Problem.NotAllowed -> Text(REFUSAL, style = InType.body, color = Palette.text)
                p is Problem.Offline && total == null -> PlainNotice("Opening the list needs the shop system.", "Connect, then try again.",
                    button = "Try again", icon = Icons.Rounded.Refresh, buttonTag = "drill.retry") { state.reload++ }
                p is Problem.Failed && total == null -> PlainNotice("Couldn't load this list.", p.message, button = "Try again", icon = Icons.Rounded.Refresh,
                    buttonTag = "drill.retry") { state.reload++ }
                total == null -> Busy("Loading…")
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.items.forEach { u -> StockRecordCard(stockCard(u.asEntity(), now, zone), { onOpenUnit(u.id) }, Modifier.testTag("drill.unit.${u.id}")) }
                    }
                    val next = state.next
                    if (next != null && api != null) RaisedButton(if (state.loadingMore) "Loading…" else "Show more", {
                        if (!state.loadingMore) scope.launch {
                            state.loadingMore = true
                            val r = api.unitQuery(state.filter, next)
                            if (r is ShopResult.Ok) { state.items = state.items + r.value.items; state.next = r.value.next }
                            state.problem = problemOf(r)
                            state.loadingMore = false
                        }
                    }, Modifier.fillMaxWidth().testTag("drill.more"))
                    if (p != null && p != Problem.NotAllowed) Text((p as? Problem.Offline)?.message ?: (p as Problem.Failed).message, style = InType.meta, color = Palette.text)
                }
            }
        }
    }
}
