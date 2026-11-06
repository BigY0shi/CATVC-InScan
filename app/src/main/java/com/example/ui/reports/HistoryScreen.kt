package com.example.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.shop.RangeQuery
import com.example.shop.ReportsApi
import com.example.shop.ShopResult
import com.example.ui.inscan.InType
import com.example.ui.inscan.NoticeCard
import com.example.ui.inscan.Palette
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.Roles
import com.example.ui.inscan.Space
import com.example.ui.inscan.StatusBanner
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// More → My history (my-history-ui-spec.md), and "Dale's work" for one person from Work by person (D-H3).

@Composable
fun LoadHistory(state: HistoryState, api: ReportsApi, today: LocalDate) {
    LaunchedEffect(state.range, state.reload) {
        val asked = state.range
        val first = state.page == null
        state.loading = true
        state.refetchFailed = false
        if (!first) state.live = "Updating to ${guessRange(asked, today).spoken(today)}…"
        val r = api.history(state.personId, asked)
        if (r is ShopResult.Ok) {
            state.page = r.value
            state.events = r.value.events
            state.shownRange = asked
            state.problem = null
            if (!first) state.live = "Showing ${guessRange(asked, today).spoken(today)}"
        } else {
            val p = problemOf(r)
            if (first || p == Problem.NotAllowed) state.problem = p
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
fun HistoryScreen(
    state: HistoryState,
    api: ReportsApi?,
    // This phone's outbox, for the signed-in person's own view only; null in the manager view.
    phone: PhoneStatus?,
    today: LocalDate,
    zone: ZoneId,
    onBack: () -> Unit,
    onOpenUnit: (String) -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (api != null) LoadHistory(state, api, today)
    val scope = rememberCoroutineScope()
    val manager = state.personId != null
    val who = state.personName ?: "Someone"
    val title = if (manager) "${possessive(who)} work" else "My history"
    val page = state.page
    val range = page?.let { resolvedRange(state.shownRange ?: state.range, it.from, it.to, today) }
    val asked = guessRange(state.range, today).let { g -> if (state.range == state.shownRange && range != null) range else g }
    var titleBottom by remember { mutableIntStateOf(Int.MAX_VALUE) }
    Column(modifier.fillMaxSize()) {
        if (manager) ReportBar("Back", onBack, title, state.scroll.value > titleBottom, backSpoken = "Back to Work by person")
        else ReportBar("More", onBack, title, state.scroll.value > titleBottom)
        ReportBody(state.scroll, { titleBottom = it }, title) {
            val p = state.problem
            if (p == Problem.NotAllowed) {
                Text(if (manager) "Reports are for managers and admins. Ask an Admin if you need them." else "Your history isn't turned on for your role. Ask an Admin.",
                    style = InType.body, color = Palette.text, modifier = Modifier.testTag("history.refused"))
                return@ReportBody
            }
            val offline = p is Problem.Offline && page == null
            val stale = state.loading && page != null
            WithHang(null) {
                RangeRow(asked, today, onPrevious = { state.range = asked.previous() }, onNext = asked.next(today)?.let { n -> { state.range = n } },
                    onOpen = { state.rangeSheet = true }, tag = "history")
                val live = state.live
                when {
                    offline -> {}
                    stale && live != null -> Busy(live)
                    state.refetchFailed && live != null -> Text(live, style = InType.meta, color = Palette.text)
                    page != null -> Text(updatedLine(page.asOf, zone) ?: "", style = InType.meta, color = Palette.text2, modifier = Modifier.testTag("history.updated"))
                }
            }
            if (phone != null) PhoneSections(phone, offline || phone.offline, onOpenUnit, onReview)
            when {
                offline -> PlainNotice(
                    if (phone?.pending.isNullOrEmpty()) "Your history needs the shop system." else "The rest of your history needs the shop system.",
                    "Connect, then try again.", button = "Try again", icon = Icons.Rounded.Refresh, buttonTag = "history.retry") { state.reload++ }
                p is Problem.Failed && page == null -> PlainNotice("Couldn't load this history.", p.message, button = "Try again", icon = Icons.Rounded.Refresh,
                    buttonTag = "history.retry") { state.reload++ }
                page == null || range == null -> Busy("Loading…")
                else -> Column(Modifier.stale(stale), verticalArrangement = Arrangement.spacedBy(Space.section)) {
                    val totals = page.totals
                    val tiles = totals?.let { historyTiles(it, range, today) }
                    if (tiles != null) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        TileRow(listOfNotNull(tiles.checkIns, tiles.moved))
                        tiles.also?.let { Text(it, style = InType.meta, color = Palette.text2, modifier = Modifier.testTag("history.also")) }
                    }
                    if (state.events.isEmpty()) {
                        val canStepBack = state.range.preset == Presets.THIS_WEEK
                        PlainNotice(nothingLine(range, today),
                            if (manager) "Check-ins, tags and moves $who makes show up here." else "Check-ins, tags and moves you make show up here.",
                            button = if (canStepBack) "See last week" else null, icon = Icons.Rounded.History, buttonTag = "history.lastweek") {
                            state.range = RangeQuery.preset(Presets.LAST_WEEK)
                        }
                    } else historyDays(state.events, today, zone).forEach { day ->
                        Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                            Text(day.heading, style = InType.section, color = Palette.text,
                                modifier = Modifier.semantics { heading(); contentDescription = day.spokenHeading })
                            FlatList {
                                day.rows.forEachIndexed { i, row ->
                                    if (i > 0) Divider()
                                    EventRowView(row) { onOpenUnit(row.unitId) }
                                }
                            }
                        }
                    }
                    val next = page.next
                    if (next != null && api != null) RaisedButton(if (state.loadingMore) "Loading…" else "Show more", {
                        if (!state.loadingMore) scope.launch {
                            state.loadingMore = true
                            val r = api.history(state.personId, state.shownRange ?: state.range, page.asOf, next)
                            if (r is ShopResult.Ok) { state.events = state.events + r.value.events; state.page = page.copy(next = r.value.next) }
                            state.loadingMore = false
                        }
                    }, Modifier.fillMaxWidth().testTag("history.more"))
                    // Openness (D-H1): who else can see this list. Default roles: See Reports is Management and Admin.
                    Foot(if (manager) "$who sees this list under My history." else "Managers and admins can see this list too.")
                }
            }
        }
    }
}

// The signed-in person's own phone status: banner, refused-change notice and "Not sent yet".
@Composable
private fun PhoneSections(phone: PhoneStatus, offline: Boolean, onOpenUnit: (String) -> Unit, onReview: () -> Unit) {
    val waiting = phone.pending.size
    if (offline && waiting > 0) StatusBanner(Icons.Outlined.CloudOff, Roles.warn, "Shop system offline", offlineBannerBody(waiting),
        Modifier.clip(RoundedCornerShape(12.dp)).testTag("history.offline"), bodyStyle = InType.body)
    else if (!offline && waiting > 0) StatusBanner(Icons.Outlined.CloudUpload, Roles.info, "Sending ${plural(waiting.toLong(), "change")}…", null,
        Modifier.clip(RoundedCornerShape(12.dp)).testTag("history.sending"))
    if (phone.refusedCount > 0) NoticeCard(Icons.Outlined.ErrorOutline, Roles.danger, refusedTitle(phone.refusedCount),
        phone.refusedReason ?: "The shop system didn't accept it.", "Review the change", onReview, Modifier.testTag("history.refused-change"))
    if (waiting > 0) Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
        Text("Not sent yet", style = InType.section, color = Palette.text, modifier = Modifier.semantics { heading() })
        FlatList {
            phone.pending.forEachIndexed { i, row ->
                if (i > 0) Divider()
                EventRowView(row) { onOpenUnit(row.unitId) }
            }
        }
        Text("Used another phone? Changes waiting on it show up once it connects.", style = InType.meta, color = Palette.text2)
    }
}
