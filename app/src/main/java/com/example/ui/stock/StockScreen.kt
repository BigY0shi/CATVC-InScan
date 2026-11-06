package com.example.ui.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ShopUnitEntity
import com.example.ui.inscan.ChipKind
import com.example.ui.inscan.FindField
import com.example.ui.inscan.InChip
import com.example.ui.inscan.InTopBar
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.RecordCard
import com.example.ui.inscan.RecordCardData
import com.example.ui.inscan.Roles
import com.example.ui.inscan.SignedInChip
import com.example.ui.inscan.Space
import com.example.ui.inscan.StatusBanner
import com.example.ui.inscan.TextAction
import com.example.ui.inscan.ToolChip
import com.example.ui.inscan.look
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

// What sits over the Stock list, drawn by the shell above the bottom bar.
sealed interface StockSheet {
    data object Sort : StockSheet
    data class Filter(val draft: StockFilters, val allMakes: Boolean = false) : StockSheet
}

// The Stock tab's own state. The route that opens it (nav, a Home count card) sets the status.
data class StockUiState(
    val query: StockQuery = StockQuery(),
    val sheet: StockSheet? = null,
    val pages: Int = 1,
)

// How old this phone's copy is, for the freshness line and the offline banner.
data class PhoneCopy(val offline: Boolean, val asOf: String?, val stale: Boolean)

const val PAGE_SIZE = 30

@Composable
fun StockScreen(
    state: StockUiState,
    units: List<ShopUnitEntity>,
    copy: PhoneCopy,
    signedInAs: String,
    now: Instant,
    zone: ZoneId,
    onState: (StockUiState) -> Unit,
    onOpenUnit: (String) -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The search runs 250 ms after typing stops; clearing it skips the wait.
    var searched by remember { mutableStateOf(state.query.text) }
    LaunchedEffect(state.query.text) {
        if (state.query.text.isBlank()) searched = "" else { delay(250); searched = state.query.text }
    }
    val q = state.query.copy(text = searched)
    val view = remember(units, q, now) { stockView(units, q, now, zone) }
    val applied = remember(units, q.filters) { appliedFilters(units, q.filters) }
    val findFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val list = rememberLazyListState()
    fun set(query: StockQuery) = onState(state.copy(query = query, pages = 1))

    // Paging: 30 at a time, the next 30 once the end of the list comes into view.
    val shown = view.results.take(state.pages * PAGE_SIZE)
    LaunchedEffect(list, view.results.size, state.pages) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last >= list.layoutInfo.totalItemsCount - 2 && view.results.size > state.pages * PAGE_SIZE) onState(state.copy(pages = state.pages + 1))
        }
    }
    // Dragging the list hides the keyboard.
    val hideOnDrag = remember(keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) keyboard?.hide()
                return Offset.Zero
            }
        }
    }

    Column(modifier.fillMaxSize().background(Palette.bg)) {
        InTopBar("Stock") { SignedInChip(signedInAs, onAccount, Modifier.testTag("topbar.user")) }
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(hideOnDrag).windowInsetsPadding(WindowInsets.ime).testTag("stock.list"),
            state = list,
            contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.gutter, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(Space.item),
        ) {
            if (copy.offline) item("offline") {
                Column {
                    StatusBanner(
                        Icons.Rounded.CloudOff, Roles.warn, "Shop system offline",
                        if (copy.asOf != null) "Showing this phone's copy, as of ${copy.asOf}." else "Showing this phone's copy.",
                        Modifier.clip(RoundedCornerShape(12.dp)).testTag("stock.offline"), bodyStyle = InType.body,
                    )
                    Spacer(Modifier.height(Space.item))
                }
            }
            // Stack 1: Find and the status chips.
            item("find") {
                Column {
                    FindField(
                        state.query.text, { set(state.query.copy(text = it)) },
                        focusRequester = findFocus, tag = "stock.find",
                        onSearch = { searched = state.query.text },
                    )
                    Text("Tag ID, VIN, make or model", style = InType.meta, color = Palette.text2, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                }
            }
            item("chips") {
                StatusChips(view.chips, state.query.status) { set(state.query.copy(status = it, sort = null)) }
                Spacer(Modifier.height(Space.item))
            }
            // Stack 2: the results line, applied filters, cards.
            item("results") {
                Column {
                    ResultsLine(view, q.filters.count, onSort = { onState(state.copy(sheet = StockSheet.Sort)) },
                        onFilter = { onState(state.copy(sheet = StockSheet.Filter(state.query.filters))) })
                    if (copy.stale && !copy.offline && copy.asOf != null) {
                        Text("On this phone, as of ${copy.asOf}", style = InType.meta, color = Palette.text2, modifier = Modifier.padding(top = 4.dp).testTag("stock.freshness"))
                    }
                }
            }
            if (applied.isNotEmpty()) item("applied") {
                AppliedRow(applied, onRemove = { set(state.query.copy(filters = it.remove(state.query.filters))) },
                    onClearAll = { set(state.query.copy(filters = StockFilters())) })
            }
            items(shown, key = { it.id }) { u ->
                val card = remember(u, now) { stockCard(u, now, zone) }
                StockRecordCard(card, { onOpenUnit(u.id) }, Modifier.testTag("stock.unit.${card.shortId}"))
            }
            view.empty?.let { e ->
                item("empty") {
                    EmptyNotice(e, copy.offline,
                        onShow = { set(state.query.copy(status = it, sort = null)) },
                        onClearSearch = { set(state.query.copy(text = "")); searched = ""; runCatching { findFocus.requestFocus() } },
                        onClearFilters = { set(state.query.copy(filters = StockFilters())) })
                }
            }
            if (view.results.isNotEmpty()) item("more") {
                Text(
                    if (shown.size < view.results.size) "Loading more…" else "That's all ${view.results.size}.",
                    style = InType.meta, color = Palette.text2,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("stock.more"),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

// Single-select status chips. They wrap and never scroll sideways.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusChips(chips: List<Pair<StockStatus, Int?>>, selected: StockStatus, onSelect: (StockStatus) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Status" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (s, n) ->
            InChip(s.word, s == selected, { onSelect(s) }, count = n, kind = ChipKind.RADIO,
                modifier = Modifier.testTag("stock.status.${s.name}"))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsLine(view: StockView, filters: Int, onSort: () -> Unit, onFilter: () -> Unit) {
    // The screen's one live region. It speaks about a second after the count settles.
    var spoken by remember { mutableStateOf(view.resultsLine) }
    LaunchedEffect(view.resultsLine) { delay(1000); spoken = view.resultsLine }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            view.resultsLine, style = InType.meta, color = Palette.text2,
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = spoken }
                .testTag("stock.count"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolChip(Icons.Rounded.SwapVert, view.sortLabel, onSort, Modifier.testTag("stock.sort"), trailing = Icons.Rounded.ExpandMore, spoken = "Sort: ${view.sortLabel}")
            ToolChip(Icons.Rounded.Tune, if (filters > 0) "Filter · $filters" else "Filter", onFilter, Modifier.testTag("stock.filter"),
                spoken = if (filters > 0) "Filter, $filters applied" else "Filter")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppliedRow(applied: List<AppliedFilter>, onRemove: (AppliedFilter) -> Unit, onClearAll: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().semantics { contentDescription = "Applied filters" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        applied.forEach { a ->
            InChip(a.label, true, { onRemove(a) }, kind = ChipKind.BUTTON, trailing = Icons.Rounded.Close,
                spoken = "Remove filter: ${a.label}", modifier = Modifier.testTag("stock.applied.${a.label}"))
        }
        TextAction("Clear all", onClearAll, Modifier.align(Alignment.CenterVertically).testTag("stock.applied.clear"))
    }
}

// Home's full record card with Stock's meta line: colour on every card, gaps marked.
@Composable
fun StockRecordCard(card: StockCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RecordCard(
        RecordCardData(card.shortId, card.name, "", card.status.look(), card.typeWord, card.spoken),
        onClick, modifier,
    ) { MetaLine(card.parts) }
}

private const val GAP_ICON = "gap"

// No part of the meta line breaks inside itself: non-breaking spaces join its words.
@Composable
fun MetaLine(parts: List<MetaPart>, modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        parts.forEachIndexed { i, p ->
            // The dot stays with the part before it, so a line never starts with one.
            if (i > 0) append("\u00A0· ")
            val words = p.text.replace(' ', ' ')
            if (p.gap) {
                appendInlineContent(GAP_ICON, "warning")
                withStyle(SpanStyle(color = Palette.text, fontWeight = FontWeight.Medium)) { append(words) }
            } else append(words)
        }
    }
    val inline = mapOf(GAP_ICON to InlineTextContent(Placeholder(18.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Roles.warn.ink, modifier = Modifier.padding(end = 2.dp))
    })
    Text(text, style = InType.meta, color = Palette.text2, inlineContent = inline, modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyNotice(e: StockEmpty, offline: Boolean, onShow: (StockStatus) -> Unit, onClearSearch: () -> Unit, onClearFilters: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 20.dp).testTag("stock.empty"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val acts = mutableListOf<@Composable () -> Unit>()
        when (e) {
            is StockEmpty.NoneInStatus -> Text(
                when (e.status) {
                    StockStatus.IN_STOCK -> "Nothing ready to sell."
                    StockStatus.INCOMING -> "Nothing waiting to be confirmed."
                    StockStatus.SALE_PENDING -> "No sales in progress."
                    StockStatus.SOLD -> "No sold units on this phone."
                    StockStatus.RETIRED -> "No retired units on this phone."
                    StockStatus.ALL -> "No units on this phone yet."
                }, style = InType.body, color = Palette.text,
            )
            is StockEmpty.FiltersMatchNothing -> {
                Text("No units match ${if (e.filters == 1) "this filter" else "these ${e.filters} filters"}.", style = InType.body, color = Palette.text)
                acts += { RaisedButton("Clear filters", onClearFilters, Modifier.testTag("stock.clearFilters")) }
            }
            is StockEmpty.NothingMatches -> {
                if (offline) {
                    Text("Nothing on this phone's copy matches. Scan the tag, or check again when the shop system is back.", style = InType.body, color = Palette.text)
                } else {
                    Text("Nothing matches “${e.text}”.", style = InType.body, color = Palette.text)
                    Text("Check the spelling, or scan the tag.", style = InType.meta, color = Palette.text2)
                }
                if (e.filters > 0) acts += { RaisedButton("Clear filters", onClearFilters, Modifier.testTag("stock.clearFilters")) }
                acts += { TextAction("Clear search", onClearSearch, Modifier.testTag("stock.clearSearch")) }
            }
            is StockEmpty.MatchesElsewhere -> {
                val withFilters = if (e.filters > 0) " with ${e.filters} ${if (e.filters == 1) "filter" else "filters"} on" else ""
                Text("No match in ${e.status.word}$withFilters.", style = InType.body, color = Palette.text)
                val lines = buildList {
                    if (e.others.isNotEmpty()) add(e.others.joinToString(" and ") { "${it.second} in ${it.first.word}" } + ".")
                    if (e.sold > 0) add(if (e.sold == 1) "1 sold unit matches." else "${e.sold} sold units match.")
                }
                lines.forEach { Text(it, style = InType.meta, color = Palette.text2) }
                when {
                    e.others.size == 1 -> acts += { RaisedButton("Show ${e.others[0].first.word}", { onShow(e.others[0].first) }, Modifier.testTag("stock.showMatches")) }
                    e.others.size > 1 -> acts += { RaisedButton("Show ${e.others.sumOf { it.second }} matches", { onShow(StockStatus.ALL) }, Modifier.testTag("stock.showMatches")) }
                }
                if (e.sold > 0) acts += { RaisedButton("Show sold", { onShow(StockStatus.SOLD) }, Modifier.testTag("stock.showSold")) }
                if (e.filters > 0) acts += { TextAction("Clear filters", onClearFilters, Modifier.testTag("stock.clearFilters")) }
                acts += { TextAction("Clear search", onClearSearch, Modifier.testTag("stock.clearSearch")) }
            }
        }
        if (acts.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                acts.forEach { Row(Modifier.align(Alignment.CenterVertically)) { it() } }
            }
        }
    }
}
