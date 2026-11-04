package com.example.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.inscan.IdPlate
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.example.ui.inscan.RaisedSurface
import com.example.ui.inscan.Roles
import com.example.ui.inscan.Space
import com.example.ui.inscan.StatusLook
import com.example.ui.inscan.ToolChip
import com.example.ui.inscan.chipDepth
import com.example.ui.inscan.raised
import java.time.LocalDate

// Pieces shared by the Reports screens and My history (reports-ui-spec.md §3, §4, §6;
// prototypes/reports.html). Information is flat; only the range, steppers, switch and Export are raised.

// The single-series data colour (§6): mid-grey, so the headline figures lead. Never emerald.
val DataInk = Palette.borderStrong

// ---------- Frame ----------

// Top bar: a labelled Back on the left, the report name centred once the big title has scrolled
// under the bar, and Export (built like Find) on the right.
@Composable
fun ReportBar(
    back: String,
    onBack: () -> Unit,
    barTitle: String,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
    backSpoken: String = "Back to $back",
    right: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().background(Palette.surface)) {
        Box(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp)) {
            Row(
                Modifier.align(Alignment.CenterStart).heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics(mergeDescendants = true) { contentDescription = backSpoken }
                    .padding(start = 8.dp, end = 12.dp).testTag("report.back"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ChevronLeft, null, tint = Roles.accentInk, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(back, style = InType.label, color = Roles.accentInk)
            }
            if (showTitle) Text(barTitle, style = InType.appTitle, color = Palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).widthIn(max = 180.dp).clearAndSetSemantics {})
            if (right != null) Box(Modifier.align(Alignment.CenterEnd)) { right() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
    }
}

@Composable
fun ExportChip(enabled: Boolean, onClick: () -> Unit) {
    if (enabled) ToolChip(Icons.Rounded.FileDownload, "Export", onClick, Modifier.testTag("report.export"))
    else Row(
        Modifier.height(48.dp).background(Palette.surface, RoundedCornerShape(24.dp)).border(1.dp, Palette.divider, RoundedCornerShape(24.dp))
            .semantics(mergeDescendants = true) { contentDescription = "Export, nothing to export" }.padding(start = 10.dp, end = 14.dp)
            .testTag("report.export"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.FileDownload, null, tint = Palette.text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("Export", style = InType.label, color = Palette.text2)
    }
}

// The scrolling body: padding 16/16/28, 24 between blocks. Reports when the big title is under the
// bar. [under] is the meta line that hangs 8 under the title ("Updated 9:38 am").
@Composable
fun ReportBody(scroll: ScrollState, onTitleBottom: (Int) -> Unit, title: String, modifier: Modifier = Modifier, under: String? = null,
               content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(scroll).padding(start = Space.gutter, end = Space.gutter, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(Space.section),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = InType.screenTitle, color = Palette.text,
                modifier = Modifier.semantics { heading() }.onGloballyPositioned { onTitleBottom(it.size.height + 16) }.testTag("report.title"))
            if (under != null) Text(under, style = InType.meta, color = Palette.text2, modifier = Modifier.testTag("report.under"))
        }
        content()
    }
}

@Composable
fun WithHang(hang: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
        if (hang != null) Text(hang, style = InType.meta, color = Palette.text2, modifier = Modifier.testTag("report.updated"))
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.item)) {
        Text(title, style = InType.section, color = Palette.text, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
fun Foot(text: String, modifier: Modifier = Modifier) = Text(text, style = InType.meta, color = Palette.text2, modifier = modifier)

// "Loading…" with the small ring (no skeletons, §9).
@Composable
fun Busy(text: String, modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), color = Palette.text2, trackColor = Palette.divider, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = InType.label, color = Palette.text)
    }
}

// A flat notice: a sentence, a quieter line, and an optional raised button (offline, couldn't load, nothing here).
@Composable
fun PlainNotice(title: String, sub: String?, modifier: Modifier = Modifier, button: String? = null, icon: ImageVector? = null, buttonTag: String = "notice.button", onButton: () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = InType.body, color = Palette.text)
        if (sub != null) Text(sub, style = InType.meta, color = Palette.text2)
        if (button != null) {
            Spacer(Modifier.height(12.dp))
            RaisedSurface(onButton, Modifier.heightIn(min = 56.dp).testTag(buttonTag)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (icon != null) { Icon(icon, null, tint = Palette.text, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)) }
                    Text(button, style = InType.tileLabel, color = Palette.text)
                }
            }
        }
    }
}

// ---------- Range row (§4 item 3) ----------

@Composable
fun RangeRow(range: RangeShown, today: LocalDate, onPrevious: () -> Unit, onNext: (() -> Unit)?, onOpen: () -> Unit, modifier: Modifier = Modifier, tag: String = "report") {
    val step = range.stepWord()
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics { contentDescription = "Date range" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stepper(Icons.Rounded.ChevronLeft, "Previous $step", true, onPrevious, "$tag.range.prev")
        RaisedSurface(onOpen, Modifier.weight(1f).heightIn(min = 56.dp).testTag("$tag.range")) {
            Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(range.title(today), style = InType.label, color = Palette.text)
                    Text(range.dates(today), style = InType.meta, color = Palette.text2)
                }
                Icon(Icons.Rounded.ExpandMore, null, tint = Palette.text2, modifier = Modifier.size(24.dp))
            }
        }
        Stepper(Icons.Rounded.ChevronRight, "Next $step", onNext != null, onNext ?: {}, "$tag.range.next")
    }
}

@Composable
private fun Stepper(icon: ImageVector, spoken: String, enabled: Boolean, onClick: () -> Unit, tag: String) {
    RaisedSurface(onClick, Modifier.width(48.dp).fillMaxHeight().heightIn(min = 56.dp).semantics { contentDescription = spoken }.testTag(tag), enabled = enabled) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (enabled) Palette.text else Palette.text2, modifier = Modifier.size(24.dp)) }
    }
}

// ---------- Tiles (§4 item 5) ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlatTile(t: TileText, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Palette.surface, RoundedCornerShape(12.dp)).padding(Space.card)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (t.value != null) Text(t.value, style = InType.bigNumber, color = Palette.text, modifier = Modifier.alignByBaseline())
            Text(t.word, style = InType.tileLabel, color = Palette.text, modifier = Modifier.alignByBaseline())
        }
        if (t.context.isNotEmpty()) Text(t.context, style = InType.meta, color = Palette.text2, modifier = Modifier.padding(top = 2.dp))
    }
}

// Tiles share a row and its height (the taller tile sets it). Measured twice with subcomposition,
// because a wrapping value and word report too small an intrinsic height and got clipped.
@Composable
fun TileRow(tiles: List<TileText>, modifier: Modifier = Modifier) {
    SubcomposeLayout(modifier.fillMaxWidth()) { c ->
        val gap = 12.dp.roundToPx()
        val w = if (tiles.size > 1) (c.maxWidth - gap * (tiles.size - 1)) / tiles.size else c.maxWidth
        val free = Constraints(minWidth = w, maxWidth = w)
        val h = subcompose("measure") { tiles.forEach { FlatTile(it) } }.maxOfOrNull { it.measure(free).height } ?: 0
        val placeables = subcompose("place") { tiles.forEach { FlatTile(it) } }.map { it.measure(Constraints.fixed(w, h)) }
        layout(c.maxWidth, h) { placeables.forEachIndexed { i, p -> p.place(i * (w + gap), 0) } }
    }
}

// ---------- Breakdown switch and rows (§4 item 7) ----------

@Composable
fun BreakdownSwitch(keys: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = "Break down by" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { k ->
            val on = k == selected
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .then(if (on) Modifier.chipDepth(Roles.accentTint, 12.dp).border(1.dp, Roles.accentInk, RoundedCornerShape(12.dp)) else Modifier.raised(pressed))
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(on, interactionSource = source, indication = null, role = Role.RadioButton) { onSelect(k) }
                    .padding(horizontal = 4.dp).testTag("report.by.$k"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                if (on) { Icon(Icons.Rounded.Check, null, tint = Roles.accentInk, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(2.dp)) }
                Text(breakdownName(k), style = InType.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (on) Roles.accentInk else Palette.text, maxLines = 1)
            }
        }
    }
}

// A flat bordered list (bg fill, border-strong, 12 radius, divider between rows).
@Composable
fun FlatList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.bg).border(1.dp, Palette.borderStrong, RoundedCornerShape(12.dp)), content = content)
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))

// Press: the row's fill steps up to surface (records are not actions, so no scale).
@Composable
fun Modifier.pressRow(onClick: (() -> Unit)?, spoken: String, tag: String? = null): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this.background(if (pressed) Palette.surface else Color.Transparent)
        .then(if (onClick != null) Modifier.clickable(source, indication = null, role = Role.Button, onClick = onClick) else Modifier)
        .semantics(mergeDescendants = true) { contentDescription = spoken }
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
}

@Composable
fun BarRowView(row: BarRow, onClick: (() -> Unit)?, tag: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).pressRow(onClick, row.spoken, tag).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (row.status != null) StatusWord(row.status) else Text(row.label, style = InType.cardTitle, color = Palette.text)
            if (row.note != null) Text(row.note, style = InType.meta, color = Palette.text2, modifier = Modifier.offset(y = (-4).dp))
            if (row.share != null) BarTrack(row.share)
        }
        Spacer(Modifier.width(12.dp))
        Text("${row.count ?: ""}", style = InType.cardTitle, color = Palette.text, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(24.dp)) { if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = Palette.text2, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
fun StatusWord(look: StatusLook) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(look.icon, null, tint = look.tone.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(look.word, style = InType.cardTitle, color = look.tone.ink)
    }
}

// The thin 8 dp bar: square at the left, a 4 dp rounded end; the longest row fills the track.
@Composable
fun BarTrack(share: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).drawBehind {
        val w = size.width * share.coerceIn(0f, 1f)
        if (w <= 0f) return@drawBehind
        val r = 4.dp.toPx()
        drawRect(DataInk, size = Size((w - r).coerceAtLeast(0f), size.height))
        drawRoundRect(DataInk, topLeft = Offset((w - 2 * r).coerceAtLeast(0f), 0f), size = Size(minOf(2 * r, w), size.height), cornerRadius = CornerRadius(r))
    })
}

@Composable
fun SplitBar(parts: List<SplitPart>) {
    val total = parts.sumOf { it.count }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().padding(16.dp).height(8.dp)
        .semantics { contentDescription = "Of $total units not sold: " + parts.joinToString(", ") { "${it.look.word} ${it.count}" } + "." },
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.forEachIndexed { i, p ->
            val shape = RoundedCornerShape(topStart = if (i == 0) 4.dp else 0.dp, bottomStart = if (i == 0) 4.dp else 0.dp,
                topEnd = if (i == parts.lastIndex) 4.dp else 0.dp, bottomEnd = if (i == parts.lastIndex) 4.dp else 0.dp)
            Box(Modifier.weight(p.count.toFloat()).fillMaxHeight().background(p.look.tone.ink, shape))
        }
    }
}

// ---------- Column chart (§6) ----------

@Composable
fun ColumnChart(model: ChartModel, report: String, open: Int?, onOpen: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val plotH = 140.dp; val band = 44.dp; val axisW = 28.dp; val dayH = 28.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(band + plotH + dayH).semantics { contentDescription = model.groupName }) {
        val slotW = (maxWidth - axisW) / model.slots.size
        // Gridlines at clean ticks, labels in text tokens.
        model.ticks.forEach { t ->
            val y = band + plotH * (1f - t.toFloat() / model.top)
            Box(Modifier.offset(x = axisW, y = y).fillMaxWidth().height(1.dp).background(Palette.divider))
            Text("$t", style = InType.meta, color = Palette.text2, textAlign = TextAlign.End,
                modifier = Modifier.width(22.dp).offset(y = y - 10.dp).clearAndSetSemantics {})
        }
        Row(Modifier.offset(x = axisW, y = band).fillMaxWidth()) {
            model.slots.forEachIndexed { i, s ->
                val v = s.value
                val isOpen = open == i
                val slotMod = Modifier.width(slotW).height(plotH + dayH).clip(RoundedCornerShape(8.dp))
                Column(
                    if (v == null) slotMod.clearAndSetSemantics {} else slotMod
                        .selectable(isOpen, role = Role.Button) { onOpen(if (isOpen) null else i) }
                        .semantics(mergeDescendants = true) { contentDescription = "${s.spoken}, ${chartValue(report, v)}"; selected = isOpen }
                        .testTag("report.chart.$i"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(plotH).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        if (v != null) {
                            val h = plotH * (v.toFloat() / model.top)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (s.today && open == null) Text("$v", style = InType.label.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                                    color = Palette.text, modifier = Modifier.padding(bottom = 4.dp).clearAndSetSemantics {})
                                Box(Modifier.width(24.dp).height(h).background(if (isOpen) Palette.text else DataInk, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)))
                            }
                        }
                    }
                    Box(Modifier.height(dayH), contentAlignment = Alignment.Center) {
                        Text(s.label, style = InType.meta.copy(fontWeight = if (s.today || isOpen) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (s.today || isOpen) Palette.text else Palette.text2, maxLines = 1)
                    }
                }
            }
        }
        // The readout: centred on the tapped column, kept inside the plot; not read aloud.
        if (open != null && open in model.slots.indices) {
            val s = model.slots[open]
            val cx = (axisW + slotW * (open + 0.5f)).coerceIn(axisW + 56.dp, maxWidth - 56.dp)
            Column(
                Modifier.offset(x = cx - 56.dp).width(112.dp).height(40.dp).clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.background(Palette.surfaceRaised, RoundedCornerShape(8.dp)).border(1.dp, Palette.borderStrong, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.readoutDate, style = InType.meta.copy(fontSize = 13.sp, lineHeight = 16.sp), color = Palette.text2)
                    Text(chartValue(report, s.value ?: 0), style = InType.label.copy(fontWeight = FontWeight.SemiBold, lineHeight = 18.sp), color = Palette.text)
                }
            }
        }
    }
}

// ---------- My history rows (my-history spec §2 item 9) ----------

fun EventIcon.vector(): ImageVector = when (this) {
    EventIcon.CHECKED_IN -> Icons.Outlined.AddBox
    EventIcon.PRINT -> Icons.Outlined.Print
    EventIcon.MOVED -> Icons.Outlined.CheckCircle
    EventIcon.TAG_SCAN -> Icons.Outlined.QrCode2
    EventIcon.EDIT -> Icons.Outlined.Edit
    EventIcon.SOLD -> Icons.Outlined.Sell
    EventIcon.BROUGHT_BACK -> Icons.AutoMirrored.Outlined.Undo
    EventIcon.STATUS -> Icons.Outlined.SwapHoriz
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventRowView(row: EventRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressRow(onClick, row.spoken, "history.ev.${row.key}").padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(row.icon.vector(), null, tint = Palette.text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(row.action, style = InType.label, color = Palette.text, modifier = Modifier.weight(1f))
                Text(row.time, style = InType.meta, color = Palette.text2)
            }
            Text(row.name, style = InType.cardTitle, color = Palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 32.dp))
            FlowRow(Modifier.padding(start = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IdPlate(row.shortId, Modifier.align(Alignment.CenterVertically))
                if (row.note != null) Text(row.note, style = InType.meta, color = Palette.text2, modifier = Modifier.align(Alignment.CenterVertically))
                if (row.changedSince) Text("Changed since", style = InType.meta, color = Palette.text, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = Palette.text2, modifier = Modifier.size(24.dp))
    }
}

// The 50% inert frame while a new range loads (§6 "Refetch keeps the frame").
fun Modifier.stale(on: Boolean): Modifier = if (on) this.alpha(0.5f).clearAndSetSemantics {} else this
