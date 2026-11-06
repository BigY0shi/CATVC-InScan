package com.example.ui.stock

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.VerbatimTtsAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.countryatv.core.ItemId
import com.countryatv.core.LabelSpec
import com.countryatv.core.Money
import com.countryatv.core.UnitStatus
import com.example.data.ShopUnitEntity
import com.example.ui.home.checkedInAt
import com.example.ui.home.unitName
import com.example.ui.inscan.IdPlate
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.example.ui.inscan.PrimaryButton
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.Roles
import com.example.ui.inscan.SectionHeader
import com.example.ui.inscan.Shapes
import com.example.ui.inscan.SignedInChip
import com.example.ui.inscan.Space
import com.example.ui.inscan.StatusTag
import com.example.ui.inscan.TextAction
import com.example.ui.inscan.look
import com.example.ui.inscan.unitTypeWord
import com.example.ui.inscan.well
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Top bar for screens inside the Stock tab: a labelled Back on the left (never icon-only,
// never swipe-only), the signed-in chip on the right.
@Composable
fun BackTopBar(back: String, signedInAs: String, onBack: () -> Unit, onAccount: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.surface)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onBack)
                    .padding(start = 8.dp, end = 12.dp).semantics(mergeDescendants = true) { contentDescription = "Back to $back" }
                    .testTag("unit.back"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Roles.accentInk, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text(back, style = InType.label, color = Roles.accentInk)
            }
            Spacer(Modifier.weight(1f))
            SignedInChip(signedInAs, onAccount, Modifier.testTag("topbar.user"))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
    }
}

// Which status changes the unit screen offers: the shop's rules (UnitStatus.canMoveTo), minus the
// ones that happen elsewhere. Incoming is confirmed by scanning the box tag, and starting or
// finishing a sale waits for the sale flow, which the shop system refuses until then.
data class StatusAction(val next: UnitStatus, val label: String, val needsReason: Boolean = false)

fun statusActions(current: UnitStatus, isAdmin: Boolean): List<StatusAction> = buildList {
    if (current == UnitStatus.SALE_PENDING && current.canMoveTo(UnitStatus.IN_STOCK)) add(StatusAction(UnitStatus.IN_STOCK, "Move back to In stock"))
    if (isAdmin && current == UnitStatus.SOLD && current.canMoveTo(UnitStatus.IN_STOCK)) add(StatusAction(UnitStatus.IN_STOCK, "Move back to In stock"))
    if (isAdmin && current.canMoveTo(UnitStatus.RETIRED)) add(StatusAction(UnitStatus.RETIRED, "Retire this unit", needsReason = true))
}

private fun statusSentence(s: UnitStatus) = when (s) {
    UnitStatus.INCOMING -> "Not confirmed yet. Scan the box tag to confirm it's in stock."
    UnitStatus.IN_STOCK -> "Ready to sell."
    UnitStatus.SALE_PENDING -> "Sale started, not finished."
    UnitStatus.SOLD -> "Sold."
    UnitStatus.RETIRED -> "Retired. It stays in the shop's records."
}

// The unit screen (stock/{unitId}): status, tag, details. Plain and flat; the only controls are
// the status changes this person may make and the tag.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitScreen(
    unit: ShopUnitEntity?,
    signedInAs: String,
    isAdmin: Boolean,
    waiting: Int,
    checkProblem: String?,
    now: Instant,
    zone: ZoneId,
    onBack: () -> Unit,
    onAccount: () -> Unit,
    onTag: () -> Unit,
    onChangeStatus: (UnitStatus, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Palette.bg)) {
        BackTopBar("Stock", signedInAs, onBack, onAccount)
        if (unit == null) {
            Column(Modifier.padding(Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.item)) {
                Text("This unit isn't on this phone.", style = InType.body, color = Palette.text, modifier = Modifier.testTag("unit.missing"))
                Text("Scan its tag, or check again when the shop system is reachable.", style = InType.meta, color = Palette.text2)
            }
            return
        }
        val status = statusOfUnit(unit)
        val card = remember(unit, now) { stockCard(unit, now, zone) }
        var retiring by remember { mutableStateOf(false) }
        if (retiring) RetireDialog(unitName(unit), onDismiss = { retiring = false }) { reason -> retiring = false; onChangeStatus(UnitStatus.RETIRED, reason) }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Space.gutter, end = Space.gutter, top = Space.section, bottom = 28.dp)
                .testTag("unit.screen"),
            verticalArrangement = Arrangement.spacedBy(Space.section),
        ) {
            // Who it is.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IdPlate(card.shortId)
                    StatusTag(status.look(), Modifier.align(Alignment.CenterVertically))
                }
                Text(card.name, style = InType.screenTitle, color = Palette.text, modifier = Modifier.semantics { heading() }.testTag("unit.name"))
                MetaLine(card.parts)
                if (checkProblem != null) {
                    Text("Couldn't check the shop system just now, so this is this phone's copy. $checkProblem", style = InType.meta, color = Palette.text2,
                        modifier = Modifier.testTag("unit.checkProblem"))
                }
                if (waiting > 0) {
                    Text(if (waiting == 1) "1 change to this unit is waiting on this phone." else "$waiting changes to this unit are waiting on this phone.",
                        style = InType.meta, color = Palette.text2, modifier = Modifier.testTag("unit.waiting"))
                }
            }
            // Status, and the changes this person may make.
            Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                SectionHeader("Status")
                Text(statusSentence(status), style = InType.body, color = Palette.text)
                statusActions(status, isAdmin).forEach { a ->
                    RaisedButton(a.label, { if (a.needsReason) retiring = true else onChangeStatus(a.next, "") },
                        Modifier.fillMaxWidth().testTag("unit.status.${a.next.name}"))
                }
            }
            // The tag.
            Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                SectionHeader("Tag")
                Text("The tag on the box carries ID ${card.shortId} and a QR code that opens this unit.", style = InType.body, color = Palette.text)
                RaisedButton("Preview and print the tag", onTag, Modifier.fillMaxWidth().testTag("unit.tag"), icon = Icons.Outlined.Print)
            }
            // Details.
            Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                SectionHeader("Details")
                DetailRows(unit, zone)
            }
        }
    }
}

@Composable
private fun DetailRows(u: ShopUnitEntity, zone: ZoneId) {
    val dateFmt = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.US)
    fun at(i: Instant) = i.atZone(zone).format(dateFmt).replace("AM", "am").replace("PM", "pm")
    val rows = buildList<Triple<String, String?, Boolean>> {
        add(Triple("Type", unitTypeWord(u.unitType), false))
        add(Triple("Make", u.make.ifBlank { null }, false))
        add(Triple("Model", listOf(u.model, u.subModel).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }, false))
        add(Triple("Year", u.year?.toString(), false))
        add(Triple("Colour", u.color.trim().ifBlank { null }, false))
        add(Triple("VIN", u.vin?.ifBlank { null }, true))
        add(Triple("Price", u.priceCents.takeIf { it > 0 }?.let { Money.format(it) }, false))
        checkedInAt(u.id)?.let { add(Triple("Checked in", at(it), false)) }
        u.inStockSince?.let { runCatching { Instant.parse(it) }.getOrNull() }?.let { add(Triple("In stock since", at(it), false)) }
        if (u.notes.isNotBlank()) add(Triple("Notes", u.notes.trim(), false))
    }
    Column(
        Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(Shapes.radiusCard))
            .border(1.dp, Palette.divider, RoundedCornerShape(Shapes.radiusCard)),
    ) {
        rows.forEachIndexed { i, (label, value, mono) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).semantics(mergeDescendants = true) {}.testTag("unit.detail.$label")) {
                Text(label, style = InType.meta, color = Palette.text2)
                when {
                    value == null -> MetaLine(listOf(MetaPart(when (label) { "VIN" -> "no VIN"; "Price" -> "no price"; else -> "not entered" }, gap = true)))
                    mono -> Text(buildAnnotatedString { pushTtsAnnotation(VerbatimTtsAnnotation(value)); append(value); pop() }, style = InType.id, color = Palette.text)
                    else -> Text(value, style = InType.body, color = Palette.text)
                }
            }
        }
    }
}

@Composable
private fun RetireDialog(name: String, onDismiss: () -> Unit, onRetire: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retire $name?", style = InType.appTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("It leaves the stock list and stays in the shop's records. Say why.", style = InType.body)
                WellField(reason, { reason = it }, "Reason", Modifier.testTag("unit.retire.reason"))
            }
        },
        confirmButton = { TextAction("Retire", { if (reason.isNotBlank()) onRetire(reason) }, Modifier.testTag("unit.retire.confirm")) },
        dismissButton = { TextAction("Keep it", onDismiss) },
    )
}

@Composable
private fun WellField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp).well().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = InType.body, color = Palette.text2)
        BasicTextField(value, onChange, singleLine = true, textStyle = InType.body.copy(color = Palette.text), cursorBrush = SolidColor(Roles.accentInk),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder })
    }
}

// The tag screen (stock/{unitId}/label): what will print, at this phone's label size, and the
// hand-off to the printer maker's app.
@Composable
fun TagScreen(
    unit: ShopUnitEntity?,
    signedInAs: String,
    spec: LabelSpec,
    rendered: Result<Bitmap>?,
    onBack: () -> Unit,
    onAccount: () -> Unit,
    onSize: () -> Unit,
    onShare: (reprintReason: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Palette.bg)) {
        BackTopBar("Unit", signedInAs, onBack, onAccount)
        if (unit == null) {
            Text("This unit isn't on this phone.", style = InType.body, color = Palette.text, modifier = Modifier.padding(Space.gutter))
            return
        }
        var reason by remember { mutableStateOf("") }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = Space.gutter, end = Space.gutter, top = Space.section, bottom = Space.section)
                .testTag("tag.screen"),
            verticalArrangement = Arrangement.spacedBy(Space.item),
        ) {
            Text("Tag for ${unitName(unit)}", style = InType.screenTitle, color = Palette.text, modifier = Modifier.semantics { heading() })
            Text("ID ${ItemId.shortForm(unit.id)}", style = InType.id, color = Palette.text2)
            rendered?.onSuccess { bitmap ->
                Box(Modifier.fillMaxWidth().background(Palette.paper, RoundedCornerShape(Shapes.radiusPlate)).padding(8.dp)) {
                    Image(bitmap.asImageBitmap(), "Tag preview", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxWidth().testTag("tag.preview"))
                }
            }
            rendered?.onFailure {
                Text("This tag doesn't fit the label size set for this phone. Set a larger label size.", style = InType.body, color = Palette.text,
                    modifier = Modifier.testTag("tag.tooSmall"))
            }
            Text("%.0f × %.0f mm, %d dpi".format(spec.widthMm, spec.heightMm, spec.dpi), style = InType.meta, color = Palette.text2)
            RaisedButton("Label size", onSize, Modifier.fillMaxWidth().testTag("tag.size"), icon = Icons.Rounded.Straighten)
            Spacer(Modifier.height(4.dp))
            WellField(reason, { reason = it }, "Replacing a tag? Why (optional)", Modifier.testTag("tag.reason"))
            Text(
                "No label printer is built into InScan yet. Share the tag to the printer maker's app and print it there. " +
                    "Then stick it on the box and scan it.",
                style = InType.meta, color = Palette.text2,
            )
        }
        Column(Modifier.fillMaxWidth().background(Palette.bg).padding(horizontal = Space.gutter, vertical = 12.dp)) {
            PrimaryButton("Share to printer app", { onShare(reason) }, Modifier.fillMaxWidth().testTag("tag.share"),
                icon = Icons.Outlined.Print, enabled = rendered?.isSuccess == true)
        }
    }
}
