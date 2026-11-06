package com.example.ui.stock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.countryatv.core.UnitStatus
import com.example.data.ShopUnitEntity
import com.example.ui.inscan.FindField
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.example.ui.inscan.Space
import com.example.ui.inscan.TextAction
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

private val STATUS_ORDER = listOf(UnitStatus.IN_STOCK, UnitStatus.INCOMING, UnitStatus.SALE_PENDING, UnitStatus.SOLD)

// What the search sheet lists for a query: every match on this phone except retired units,
// in stock first, then by status, newest checked in within each.
fun findResults(units: List<ShopUnitEntity>, text: String): List<ShopUnitEntity> {
    if (text.isBlank()) return emptyList()
    val hits = units.mapNotNull { u -> matchUnit(u, text)?.let { u to it } }.filter { statusOfUnit(it.first) != UnitStatus.RETIRED }
    return hits.sortedWith(
        compareBy<Pair<ShopUnitEntity, MatchedBy>> { if (it.second == MatchedBy.ID) 0 else 1 }
            .thenBy { STATUS_ORDER.indexOf(statusOfUnit(it.first)) }
            .thenByDescending { it.first.id },
    ).map { it.first }
}

// The search sheet (4a item 3), opened by Find in the top bar. It covers the app below the status
// area. An exact 8-character tag ID or 17-character VIN jumps straight to that unit.
@Composable
fun FindSheet(
    units: List<ShopUnitEntity>,
    now: Instant,
    zone: ZoneId,
    onOpenUnit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialText: String = "",
    autoFocus: Boolean = true,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var searched by remember { mutableStateOf(initialText) }
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onCancel)
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }
    LaunchedEffect(text) { if (text.isBlank()) searched = "" else { delay(250); searched = text } }
    // Jump only on an exact tag ID or VIN that names one unit on this phone.
    LaunchedEffect(searched) { exactUnit(units, searched)?.let { onOpenUnit(it.id) } }
    val results = remember(units, searched) { findResults(units, searched) }

    Column(
        modifier.fillMaxSize().background(Palette.bg).windowInsetsPadding(WindowInsets.ime)
            .semantics { paneTitle = "Find a unit" }.testTag("search.sheet"),
    ) {
        Column(Modifier.padding(start = Space.gutter, end = Space.gutter, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FindField(text, { text = it }, Modifier.weight(1f), focusRequester = focus, tag = "search",
                    onSearch = { searched = text; exactUnit(units, text)?.let { onOpenUnit(it.id) } })
                Spacer(Modifier.width(4.dp))
                TextAction("Cancel", onCancel, Modifier.testTag("search.cancel"))
            }
            Text("Tag ID, VIN, make or model", style = InType.meta, color = Palette.text2, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
        when {
            searched.isBlank() -> Text(
                "Type a tag ID to jump straight to that unit, or part of a make or model to see matches.",
                style = InType.body, color = Palette.text2,
                modifier = Modifier.padding(start = Space.gutter + 4.dp, end = Space.gutter, top = 24.dp).widthIn(max = 320.dp).testTag("search.before"),
            )
            results.isEmpty() -> Column(Modifier.padding(start = Space.gutter + 4.dp, end = Space.gutter, top = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Nothing matches “${searched.trim()}”.", style = InType.body, color = Palette.text,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("search.none"))
                Text("Check the spelling, or scan the tag.", style = InType.meta, color = Palette.text2)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.section, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(Space.item),
            ) {
                item("count") {
                    Text(if (results.size == 1) "1 unit matches" else "${results.size} units match", style = InType.meta, color = Palette.text2,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("search.count"))
                }
                items(results, key = { it.id }) { u ->
                    val card = remember(u, now) { stockCard(u, now, zone) }
                    StockRecordCard(card, { onOpenUnit(u.id) }, Modifier.testTag("search.unit.${card.shortId}"))
                }
            }
        }
    }
}
