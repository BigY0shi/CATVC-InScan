package com.example.ui.reports

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.shop.RangeQuery
import com.example.ui.inscan.DepthTokens
import com.example.ui.inscan.InType
import com.example.ui.inscan.Motion
import com.example.ui.inscan.Palette
import com.example.ui.inscan.PrimaryButton
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.Roles
import com.example.ui.inscan.TextAction
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// The date range sheet (reports spec §5) and the export sheet (§8). Drawn by the shell over the
// whole screen, bottom bar included. Back and a tap on the scrim close them.

@Composable
fun SheetScrim(onDismiss: () -> Unit, title: String, top: androidx.compose.ui.unit.Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(DepthTokens.scrimModal)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss).testTag("sheet.scrim"),
    ) {
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter), enter = slideInVertically(tween(Motion.sheetMs, easing = Motion.out)) { it / 8 }) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Palette.surfaceRaised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .drawBehind { drawRect(Palette.borderStrong, size = Size(size.width, 1.dp.toPx())) }
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .semantics { paneTitle = title }
                    .padding(start = 16.dp, end = 16.dp, top = top, bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

// The sheet's option list: surface fill, strong border, 12 radius, dividers.
@Composable
private fun Options(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.surface).border(1.dp, Palette.borderStrong, RoundedCornerShape(12.dp)), content = content)
}

@Composable
private fun OptionRow(modifier: Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@Composable
private fun pressFill(source: MutableInteractionSource) = if (source.collectIsPressedAsState().value) Palette.surfaceRaised else Palette.surface

// Presets as rows of buttons, each with its real dates; the current one has a check (selected).
@Composable
fun DateRangeSheet(current: RangeQuery, today: LocalDate, onPick: (RangeQuery) -> Unit, onCustom: () -> Unit, onClose: () -> Unit) {
    // The header row is the Close button's 48 dp tall, so the sheet starts 10 higher (the mockup's .sh-head).
    SheetScrim(onClose, "Date range", top = 10.dp) {
        Row(Modifier.fillMaxWidth().padding(end = 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Date range", style = InType.section, color = Palette.text, modifier = Modifier.weight(1f).semantics { heading() })
            Box(Modifier.offset(x = 12.dp)) { TextAction("Close", onClose, Modifier.testTag("range.close")) }
        }
        Options(Modifier.selectableGroup()) {
            Presets.ALL.forEachIndexed { i, p ->
                if (i > 0) Divider()
                val (f, t) = presetDates(p, today)
                val on = current.preset == p
                val source = remember { MutableInteractionSource() }
                OptionRow(Modifier.background(pressFill(source)).clickable(source, indication = null, role = Role.Button) { onPick(RangeQuery.preset(p)) }
                    .semantics(mergeDescendants = true) { selected = on }.testTag("range.$p")) {
                    Column(Modifier.weight(1f)) {
                        Text(presetTitle(p), style = InType.cardTitle, color = Palette.text)
                        Text(rangeDates(p, f, t, today), style = InType.meta, color = Palette.text2)
                    }
                    Box(Modifier.size(24.dp)) { if (on) Icon(Icons.Rounded.Check, null, tint = Roles.accentInk, modifier = Modifier.size(24.dp)) }
                }
            }
        }
        Options {
            val source = remember { MutableInteractionSource() }
            OptionRow(Modifier.background(pressFill(source)).clickable(source, indication = null, role = Role.Button, onClick = onCustom).testTag("range.custom")) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = Palette.text2, modifier = Modifier.size(24.dp))
                Text("Custom dates…", style = InType.cardTitle, color = Palette.text, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = Palette.text2, modifier = Modifier.size(24.dp))
            }
        }
        Text("Weeks start Monday. Times are shop time (Pacific).", style = InType.meta, color = Palette.text2)
    }
}

// Material 3's DateRangePicker in a full-screen dialog (§5). Future dates and dates older than the
// 13 months that detailed events are kept are greyed out rather than refused after the pick.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRangeDialog(today: LocalDate, onPick: (RangeQuery) -> Unit, onDismiss: () -> Unit) {
    val earliest = today.minusMonths(13)
    val state = rememberDateRangePickerState(
        yearRange = earliest.year..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return !d.isAfter(today) && !d.isBefore(earliest)
            }
            override fun isSelectableYear(year: Int) = year in earliest.year..today.year
        },
    )
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Palette.surface)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextAction("Cancel", onDismiss, Modifier.testTag("custom.cancel"))
                Spacer(Modifier.weight(1f))
                val from = state.selectedStartDateMillis; val to = state.selectedEndDateMillis
                TextAction("Use these dates", {
                    if (from != null) {
                        // The picker returns UTC-midnight millis: read them as shop-calendar dates.
                        val f = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate()
                        val t = to?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: f
                        onPick(RangeQuery.dates(f.toString(), t.toString()))
                    }
                }, Modifier.testTag("custom.apply"))
            }
            DateRangePicker(state, Modifier.weight(1f), colors = DatePickerDefaults.colors(containerColor = Palette.surface))
        }
    }
}

// ---------- Export ----------

@Composable
fun ExportSheet(
    sheet: ReportSheet.Export,
    choices: List<ExportChoice>,
    line: String,
    onChoose: (String) -> Unit,
    onShare: () -> Unit,
    onShareReady: (File) -> Unit,
    onClose: () -> Unit,
) {
    SheetScrim(onClose, "Export check-ins") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Export check-ins", style = InType.section, color = Palette.text, modifier = Modifier.semantics { heading() })
            Text(line, style = InType.body, color = Palette.text)
        }
        Options(Modifier.selectableGroup()) {
            choices.forEachIndexed { i, c ->
                if (i > 0) Divider()
                val on = sheet.rows == c.rows
                val source = remember { MutableInteractionSource() }
                OptionRow(Modifier.background(pressFill(source)).selectable(on, interactionSource = source, indication = null, role = Role.RadioButton) { onChoose(c.rows) }
                    .testTag("export.${c.rows}")) {
                    Box(Modifier.size(24.dp).border(2.dp, if (on) Roles.accentInk else Palette.borderStrong, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(12.dp).background(Roles.accentInk, CircleShape))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = InType.cardTitle, color = Palette.text)
                        Text(c.line, style = InType.meta, color = Palette.text2)
                    }
                }
            }
        }
        Text("The file includes names and prices. Send it to yourself; don't save it on a shared phone.", style = InType.meta, color = Palette.text2)
        if (sheet.failed) Text("Couldn't make the file. Check the connection and try again.", style = InType.body, color = Palette.text, modifier = Modifier.testTag("export.failed"))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RaisedButton("Cancel", onClose, Modifier.widthIn(min = 112.dp).testTag("export.cancel"))
            val ready = sheet.ready
            when {
                ready != null -> PrimaryButton("Share file", { onShareReady(ready) }, Modifier.weight(1f).testTag("export.go"), icon = Icons.Rounded.IosShare)
                sheet.preparing -> PrimaryButton("Preparing…", {}, Modifier.weight(1f).testTag("export.go"), enabled = false)
                else -> PrimaryButton("Share file", onShare, Modifier.weight(1f).testTag("export.go"), icon = Icons.Rounded.IosShare)
            }
        }
    }
}

// The system share sheet for a CSV in cacheDir/exports, through FileProvider.
fun shareCsv(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "com.example.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share the file").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}
