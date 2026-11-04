package com.example.ui.stock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.ShopUnitEntity
import com.example.ui.inscan.ChipKind
import com.example.ui.inscan.DepthTokens
import com.example.ui.inscan.InChip
import com.example.ui.inscan.InType
import com.example.ui.inscan.Motion
import com.example.ui.inscan.Palette
import com.example.ui.inscan.PrimaryButton
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.Roles
import com.example.ui.inscan.Space
import com.example.ui.inscan.TextAction
import java.time.Instant
import java.time.ZoneId

// The Sort and Filter sheets, drawn by the shell over the whole screen (bottom bar included),
// as the mockup does. Swipe-down is off: Cancel, Back or a tap on the scrim throws changes away.
@Composable
fun StockSheets(
    state: StockUiState,
    units: List<ShopUnitEntity>,
    now: Instant,
    zone: ZoneId,
    onState: (StockUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheet = state.sheet ?: return
    val close = { onState(state.copy(sheet = null)) }
    BackHandler(onBack = close)
    Box(
        modifier.fillMaxSize().background(DepthTokens.scrimModal)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = close)
            .testTag("sheet.scrim"),
    ) {
        when (sheet) {
            StockSheet.Sort -> SheetFrame(tall = false, title = "Sort by", cancelTag = "sort.cancel", onCancel = close) {
                SortOptions(state.query, onPick = { onState(state.copy(query = state.query.copy(sort = it), sheet = null, pages = 1)) })
            }
            is StockSheet.Filter -> {
                val model = remember(units, state.query, sheet, now) {
                    filterSheetModel(units, state.query.status, state.query.text, sheet.draft, now, zone, sheet.allMakes)
                }
                SheetFrame(tall = true, title = "Filter", cancelTag = "sheet.cancel", onCancel = close) {
                    FilterBody(model, sheet, Modifier.weight(1f)) { onState(state.copy(sheet = it)) }
                    FilterFooter(model,
                        onClear = { onState(state.copy(sheet = sheet.copy(draft = StockFilters()))) },
                        onApply = { onState(state.copy(query = state.query.copy(filters = sheet.draft), sheet = null, pages = 1)) })
                }
            }
        }
    }
}

@Composable
private fun BoxScope.SheetFrame(tall: Boolean, title: String, cancelTag: String, onCancel: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    // Enters with the 200 ms "up".
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    // The tall sheet stops under the top bar, so the screen's title stays visible above it.
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    AnimatedVisibility(
        shown, Modifier.align(Alignment.BottomCenter).then(if (tall) Modifier.padding(top = top) else Modifier),
        enter = slideInVertically(tween(Motion.sheetMs, easing = Motion.out)) { it / 8 },
    ) {
        Column(
            Modifier.fillMaxWidth().then(if (tall) Modifier.fillMaxHeight() else Modifier)
                .background(Palette.surfaceRaised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .drawBehind { drawRect(Palette.borderStrong, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }
                // Taps inside the sheet never reach the scrim.
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .semantics { paneTitle = title }
                .testTag("sheet.$title"),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = InType.appTitle, color = Palette.text, modifier = Modifier.weight(1f).semantics { heading() })
                TextAction("Cancel", onCancel, Modifier.testTag(cancelTag))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
            content()
        }
    }
}

// Radio rows that apply on tap and close.
@Composable
private fun SortOptions(q: StockQuery, onPick: (StockSort) -> Unit) {
    val current = q.sort ?: defaultSortRow(q.status)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottom).fillMaxWidth()
            .background(Palette.surface, RoundedCornerShape(12.dp))
            .border(1.dp, Palette.borderStrong, RoundedCornerShape(12.dp))
            .selectableGroup(),
    ) {
        StockSort.entries.forEachIndexed { i, s ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
            val on = s == current
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(on, role = Role.RadioButton) { onPick(s) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("sort.${s.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(24.dp).border(2.dp, if (on) Roles.accentInk else Palette.borderStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (on) Box(Modifier.size(12.dp).background(Roles.accentInk, CircleShape)) }
                Spacer(Modifier.size(12.dp))
                Text(s.label, style = InType.body, color = Palette.text)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBody(m: FilterSheetModel, sheet: StockSheet.Filter, modifier: Modifier, onSheet: (StockSheet.Filter) -> Unit) {
    val f = sheet.draft
    fun set(nf: StockFilters) = onSheet(sheet.copy(draft = nf))
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(Space.section),
    ) {
        Group("Missing details", m.notTracked.takeIf { it.isNotEmpty() }?.let { "${joinAnd(it)} aren't tracked yet." }) {
            m.missing.forEach { o ->
                InChip(o.label, o.selected, { if (o.key == "noVin") set(f.copy(noVin = !f.noVin)) }, count = o.count, kind = ChipKind.CHECKBOX,
                    enabled = o.available, raisedFill = true, spoken = if (o.available) "${o.label}, ${o.count}" else "${o.label}, not tracked yet",
                    modifier = Modifier.testTag("sheet.missing.${o.key}"))
            }
        }
        Group("Type") {
            m.types.forEach { o ->
                InChip(o.label, o.selected, { set(f.copy(types = f.types.toggle(o.key))) }, count = o.count, kind = ChipKind.CHECKBOX,
                    raisedFill = true, modifier = Modifier.testTag("sheet.type.${o.key}"))
            }
        }
        Group("Make", if (m.makes.isEmpty()) "No makes with the other choices." else null) {
            m.makes.forEach { o ->
                InChip(o.label, o.selected, { set(f.copy(makes = f.makes.toggle(o.key))) }, count = o.count, kind = ChipKind.CHECKBOX,
                    raisedFill = true, modifier = Modifier.testTag("sheet.make.${o.key}"))
            }
            if (m.allMakes > 6 && !sheet.allMakes) {
                InChip("All makes (${m.allMakes})", false, { onSheet(sheet.copy(allMakes = true)) }, kind = ChipKind.BUTTON, raisedFill = true,
                    modifier = Modifier.testTag("sheet.make.all"))
            }
        }
        Group("Model year") {
            m.years.forEach { o ->
                InChip(o.label, o.selected, { set(f.copy(years = f.years.toggle(o.key))) }, count = o.count, kind = ChipKind.CHECKBOX,
                    raisedFill = true, modifier = Modifier.testTag("sheet.year.${o.key}"))
            }
        }
        Group("Checked in", radio = true) {
            CheckedIn.entries.forEach { c ->
                InChip(c.label, f.checkedIn == c, { set(f.copy(checkedIn = c)) }, kind = ChipKind.RADIO, raisedFill = true,
                    modifier = Modifier.testTag("sheet.when.${c.name}"))
            }
        }
        Group("Who checked it in", "Not tracked yet: this phone's copy doesn't say who checked a unit in.") {
            InChip("Only units checked in by you", false, {}, kind = ChipKind.SWITCH, enabled = false, raisedFill = true,
                spoken = "Only units checked in by you, not tracked yet", modifier = Modifier.testTag("sheet.mine"))
        }
        Group("Retired units") {
            InChip("Show retired", f.showRetired, { set(f.copy(showRetired = !f.showRetired)) }, kind = ChipKind.SWITCH, raisedFill = true,
                modifier = Modifier.testTag("sheet.retired"))
        }
    }
}

private fun <T> Set<T>.toggle(v: T) = if (v in this) this - v else this + v

private fun joinAnd(xs: List<String>) = if (xs.size <= 1) xs.joinToString() else xs.dropLast(1).joinToString(", ") + " and " + xs.last()

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, note: String? = null, radio: Boolean = false, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = InType.section, color = Palette.text, modifier = Modifier.semantics { heading() })
        FlowRow(
            Modifier.fillMaxWidth().then(if (radio) Modifier.selectableGroup() else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { chips() }
        if (note != null) Text(note, style = InType.meta, color = Palette.text2)
    }
}

@Composable
private fun FilterFooter(m: FilterSheetModel, onClear: () -> Unit, onApply: () -> Unit) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + bottom)) {
        if (m.nothingBecause != null) {
            Text(m.nothingBecause, style = InType.meta, color = Palette.text, modifier = Modifier.padding(bottom = 8.dp).testTag("sheet.nothing"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RaisedButton("Clear all", onClear, Modifier.widthIn(min = 112.dp).testTag("sheet.clear"))
            PrimaryButton(
                when { m.total == 0 -> "No units match"; m.total == 1 -> "Show 1 unit"; else -> "Show ${m.total} units" },
                onApply, Modifier.weight(1f).testTag("sheet.apply"), enabled = m.total > 0,
            )
        }
    }
}

