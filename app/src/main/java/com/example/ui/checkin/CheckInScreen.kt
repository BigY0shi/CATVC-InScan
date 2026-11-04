package com.example.ui.checkin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.VerbatimTtsAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.countryatv.core.UnitType
import com.example.ui.inscan.ChipKind
import com.example.ui.inscan.DepthTokens
import com.example.ui.inscan.InChip
import com.example.ui.inscan.InType
import com.example.ui.inscan.Motion
import com.example.ui.inscan.Palette
import com.example.ui.inscan.PrimaryButton
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.RaisedSurface
import com.example.ui.inscan.RecordCard
import com.example.ui.inscan.RecordCardData
import com.example.ui.inscan.Roles
import com.example.ui.inscan.Shapes
import com.example.ui.inscan.Space
import com.example.ui.inscan.TextAction
import com.example.ui.inscan.UnitTypeImage
import com.example.ui.inscan.chipDepth
import com.example.ui.inscan.focusRing
import com.example.ui.inscan.shopWord
import com.example.ui.inscan.well
import java.time.LocalDate

// What the stateless screen draws. CheckInFlow works these out; screenshot tests build them directly.
data class CheckInUi(
    val draft: CheckInDraft,
    val signedInAs: String,
    val today: LocalDate,
    val makeChips: List<String> = emptyList(),
    val colourChips: List<String> = emptyList(),
    // The unit this VIN is already on, as a record card.
    val existingCard: RecordCardData? = null,
    val tag: TagPreview? = null,
)

// The real tag from LabelRenderer, and what to say about its size.
data class TagPreview(
    val image: ImageBitmap?,
    val problem: String?,
    val caption: String,
    val sizeLine: String,
    val vinFits: Boolean,
    val shortId: String,
    val spoken: String,
)

class CheckInActions(
    val onDraft: (CheckInDraft) -> Unit = {},
    val onScanVin: () -> Unit = {},
    val onOpenExisting: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onSend: () -> Unit = {},
    val onYesPrinted: () -> Unit = {},
    val onLabelSize: () -> Unit = {},
    val onScanTag: () -> Unit = {},
    // Leave without saving (nothing saved), or leave the saved unit (Print later, Done).
    val onLeave: () -> Unit = {},
    val onAnother: () -> Unit = {},
)

private val Center = Alignment.CenterVertically

// Line heights as the mockup's CSS draws them: the first and last lines keep their full height.
private val NoTrim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val TextStyle.nt: TextStyle get() = copy(lineHeightStyle = NoTrim)

// Check in a unit, full screen over the shell with the bottom bar hidden (checkin-ui-spec.md 3a).
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckInScreen(ui: CheckInUi, on: CheckInActions, modifier: Modifier = Modifier, statusBarSpace: Dp = 0.dp, autoFocus: Boolean = true) {
    val d = ui.draft
    val update = on.onDraft
    val big = LocalDensity.current.fontScale >= 1.5f

    // Focus: the field Next marked; otherwise, on a new step, its first empty field. The keyboard
    // is hidden on Type, Review and Print.
    val keyboard = LocalSoftwareKeyboardController.current
    val target = when {
        !autoFocus || d.sheet != null -> null
        d.missing != null -> d.missing
        d.step == CheckInStep.MAKE -> if (d.make.isBlank()) Field.MAKE else if (d.model.isBlank()) Field.MODEL else null
        d.step == CheckInStep.YEAR -> if (d.year.isBlank()) Field.YEAR else null
        d.step == CheckInStep.PRICE -> if (d.price.isBlank()) Field.PRICE else null
        else -> null
    }
    LaunchedEffect(d.step) { if (d.step == CheckInStep.TYPE || d.step == CheckInStep.REVIEW || d.step == CheckInStep.PRINT) keyboard?.hide() }

    // Back: previous step (or Review, when editing from there); step 1 asks first; on Print it's
    // Print later (before the tag went anywhere) or Done.
    BackHandler(enabled = d.sheet == null) {
        when {
            d.step == CheckInStep.PRINT && d.phase == PrintPhase.SEND -> update(d.copy(sheet = CheckInSheet.PRINT_LATER))
            d.step == CheckInStep.PRINT -> on.onLeave()
            else -> update(d.back())
        }
    }
    BackHandler(enabled = d.sheet != null) { update(d.copy(sheet = null)) }

    CompositionLocalProvider(LocalFocusTarget provides FocusTarget(target, "${d.step}-${d.missing}")) {
    Box(modifier.fillMaxSize().background(Palette.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(statusBarSpace).background(Palette.surface))
            TopBar(d, hideTitle = big, onLeft = {
                when {
                    d.step != CheckInStep.PRINT -> update(d.copy(sheet = CheckInSheet.LEAVE))
                    d.phase == PrintPhase.SEND -> update(d.copy(sheet = CheckInSheet.PRINT_LATER))
                    else -> on.onLeave()
                }
            })
            Progress(if (d.step == CheckInStep.PRINT) 1f else d.step.number / 6f)
            AnimatedContent(
                targetState = d.step, modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState.number > initialState.number
                    (fadeIn(tween(200, delayMillis = 90, easing = Motion.out)) + slideInVertically(tween(200, easing = Motion.out)) { 0 } +
                        androidx.compose.animation.slideInHorizontally(tween(200, easing = Motion.out)) { if (forward) 24 else -24 }) togetherWith
                        fadeOut(tween(90))
                }, label = "step",
            ) { step ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = Space.gutter, end = Space.gutter, top = 20.dp, bottom = Space.section)
                        .testTag("checkin.body.${step.name}"),
                    verticalArrangement = Arrangement.spacedBy(Space.section),
                ) {
                    Head(stepHeading(d), stepHelp(d), extra = if (step == CheckInStep.REVIEW) ui.signedInAs else null)
                    when (step) {
                        CheckInStep.VIN -> VinStep(ui, on)
                        CheckInStep.TYPE -> TypeStep(d, update)
                        CheckInStep.MAKE -> MakeStep(ui, update)
                        CheckInStep.YEAR -> YearStep(ui, update)
                        CheckInStep.PRICE -> PriceStep(d, update)
                        CheckInStep.REVIEW -> ReviewStep(d, update)
                        CheckInStep.PRINT -> PrintStep(ui, on)
                    }
                }
            }
            BottomBar(ui, on, stacked = big)
        }
        when (d.sheet) {
            CheckInSheet.LEAVE -> Sheet("Leave without saving?", "Nothing is saved yet. What you've entered for this unit will be lost.",
                onDismiss = { update(d.copy(sheet = null)) }) {
                SheetActions(
                    "Leave", on.onLeave, "checkin.leave.go",
                    "Keep editing", { update(d.copy(sheet = null)) }, "checkin.leave.keep",
                )
            }
            CheckInSheet.PRINT_LATER -> Sheet("Print the tag later?",
                "The unit is saved as Incoming. Until it has a tag it shows as \"No tag yet\" on Home and on its own page.",
                onDismiss = { update(d.copy(sheet = null)) }) {
                SheetActions(
                    "Print later", on.onLeave, "checkin.later.go",
                    "Print now", { update(d.copy(sheet = null)) }, "checkin.later.print",
                )
            }
            CheckInSheet.WHERE_VIN -> Sheet("Where's the VIN?", null, onDismiss = { update(d.copy(sheet = null)) }) {
                listOf(
                    "On the crate label, while the unit is still boxed. Look here first when unloading a truck.",
                    "On the steering neck, stamped into the frame.",
                    "Along the frame rail, on either side.",
                    "Under the seat.",
                ).forEach { Text(it, style = InType.body.nt, color = Palette.text) }
                RaisedButton("Close", { update(d.copy(sheet = null)) }, Modifier.fillMaxWidth().padding(top = 10.dp).testTag("checkin.where.close"))
            }
            null -> Unit
        }
    }
    }
}

// Which field takes focus, and a key that changes when it should be asked again.
private data class FocusTarget(val field: Field?, val key: String)
private val LocalFocusTarget = staticCompositionLocalOf { FocusTarget(null, "") }

// ---------- Frame ----------

@Composable
private fun TopBar(d: CheckInDraft, hideTitle: Boolean, onLeft: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp).background(Palette.surface).padding(start = 4.dp, end = 8.dp)) {
        val (word, tag) = when {
            d.step != CheckInStep.PRINT -> "Cancel" to "checkin.cancel"
            d.phase == PrintPhase.SEND -> "Print later" to "checkin.print.later"
            else -> "Done" to "checkin.print.done"
        }
        TextAction(word, onLeft, Modifier.align(Alignment.CenterStart).testTag(tag))
        if (!hideTitle) Text("Check in a unit", style = InType.section.nt, color = Palette.text, modifier = Modifier.align(Alignment.Center))
        if (d.step != CheckInStep.PRINT) {
            Text("Step ${d.step.number} of 6", style = InType.meta.nt, color = Palette.text2,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).testTag("checkin.step"))
        }
    }
}

// Status, not an action: a text-2 fill on the divider track, never emerald.
@Composable
private fun Progress(fraction: Float) {
    val f by animateFloatAsState(fraction, tween(Motion.sheetMs, easing = Motion.standard), label = "progress")
    Box(Modifier.fillMaxWidth().height(4.dp).background(Palette.divider).clearAndSetSemantics {}) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Palette.text2))
    }
}

@Composable
private fun Head(title: String, help: String?, extra: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = InType.screenTitle.nt, color = Palette.text, modifier = Modifier.semantics { heading() }.testTag("checkin.heading"))
        if (help != null) Text(help, style = InType.body.nt, color = Palette.text2)
        if (extra != null) {
            Row(verticalAlignment = Center) {
                Icon(Icons.Outlined.Person, null, tint = Palette.text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Checking in as ${extra.trim().split(" ").first()}", style = InType.body.nt, color = Palette.text2)
            }
        }
    }
}

@Composable
private fun BottomBar(ui: CheckInUi, on: CheckInActions, stacked: Boolean) {
    val d = ui.draft
    val update = on.onDraft
    data class B(val text: String, val tag: String, val onClick: () -> Unit, val icon: ImageVector? = null, val enabled: Boolean = true)
    val back = B("Back", "checkin.back", { update(d.back()) })
    val (left, right) = when {
        d.step == CheckInStep.VIN && d.existing != null ->
            back.copy(onClick = { update(d.copy(sheet = CheckInSheet.LEAVE)) }) to B("Open that unit", "checkin.vin.openExisting", { on.onOpenExisting(d.existing.unitId) })
        d.fromReview -> back to B("Back to review", "checkin.next", { update(d.next()) })
        d.step == CheckInStep.REVIEW -> back to B("Save and print tag", "checkin.save", on.onSave, enabled = !d.saving && !d.saved)
        d.step == CheckInStep.PRINT -> when (d.phase) {
            PrintPhase.SEND -> null to B("Send to printer app", "checkin.print.go", on.onSend, Icons.Outlined.IosShare, enabled = ui.tag?.image != null)
            PrintPhase.ASK_DID_IT_PRINT -> return
            PrintPhase.PRINTED -> B("Check in another", "checkin.print.another", on.onAnother) to
                B("Scan the tag", "checkin.print.scan", on.onScanTag, Icons.Rounded.QrCodeScanner)
        }
        d.step == CheckInStep.VIN -> null to B("Next", "checkin.next", { update(d.next()) })
        else -> back to B("Next", "checkin.next", { update(d.next()) })
    }
    Column(
        Modifier.fillMaxWidth().background(Palette.surface)
            .drawBehind { drawRect(Palette.divider, size = Size(size.width, 1.dp.toPx())) }
            .navigationBarsPadding().imePadding()
            .padding(horizontal = Space.gutter, vertical = 12.dp),
    ) {
        @Composable fun Primary(b: B, m: Modifier) = PrimaryButton(b.text, b.onClick, m.testTag(b.tag), icon = b.icon, enabled = b.enabled)
        @Composable fun Raised(b: B, m: Modifier) = RaisedButton(b.text, b.onClick, m.testTag(b.tag), enabled = b.enabled)
        if (left == null) Primary(right, Modifier.fillMaxWidth())
        else if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Primary(right, Modifier.fillMaxWidth()); Raised(left, Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Raised(left, Modifier.widthIn(min = 112.dp)); Primary(right, Modifier.weight(1f))
        }
    }
}

// ---------- Bits shared by the steps ----------

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)

@Composable
private fun FieldLabel(text: String, optional: Boolean = false) {
    Text(buildAnnotatedString {
        append(text)
        if (optional) withStyle(SpanStyle(color = Palette.text2, fontWeight = FontWeight.Normal)) { append(" (optional)") }
    }, style = InType.label.nt, color = Palette.text)
}

// A value the VIN lookup filled in. Review is where it's checked against the unit.
@Composable
private fun FromVin(modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 4.dp), verticalAlignment = Center) {
        Icon(CheckInIcons.Barcode, null, tint = Roles.info.ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("From the VIN", style = InType.meta.nt, color = Palette.text2)
    }
}

// Next tapped with this missing: it says why under the field.
@Composable
private fun MissingLine(text: String, tag: String) {
    Row(Modifier.padding(start = 4.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag(tag), verticalAlignment = Center) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = Roles.danger.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = InType.label.nt, color = Palette.text)
    }
}

// A 2 dp ring outside the field's own edge: warn (doesn't add up) or danger (missing).
private fun Modifier.ring(color: Color?, radius: Dp = Shapes.radiusCard): Modifier = if (color == null) this else drawWithContent {
    drawContent()
    val w = 3.dp.toPx(); val o = 0.5.dp.toPx()
    drawRoundRect(color, topLeft = Offset(-o, -o), size = Size(size.width + 2 * o, size.height + 2 * o),
        cornerRadius = CornerRadius(radius.toPx() + o), style = Stroke(w))
}

// A typed field: an inset well, 56 tall.
@Composable
private fun WellField(
    value: String,
    onValue: (String) -> Unit,
    tag: String,
    spoken: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    prefix: String? = null,
    ring: Color? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    onIme: () -> Unit = {},
    singleLine: Boolean = true,
    field: Field? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val target = LocalFocusTarget.current
    LaunchedEffect(target) {
        // After the step transition, so focus lands on the field that's on screen.
        if (field != null && target.field == field) { delay(220); runCatching { requester.requestFocus() } }
    }
    val style: TextStyle = if (mono) InType.fieldMono.copy(letterSpacing = 0.04.em) else InType.body
    Row(
        modifier.fillMaxWidth().heightIn(min = if (singleLine) 56.dp else 84.dp)
            .then(if (focused) Modifier.focusRing(Shapes.radiusCard) else Modifier)
            .well(Shapes.radiusCard)
            .ring(ring)
            .padding(start = 16.dp, end = 14.dp, top = if (singleLine) 0.dp else 14.dp, bottom = if (singleLine) 0.dp else 14.dp),
        verticalAlignment = if (singleLine) Center else Alignment.Top,
    ) {
        if (prefix != null) { Text(prefix, style = InType.body.nt, color = Palette.text2); Spacer(Modifier.width(8.dp)) }
        Box(Modifier.weight(1f).then(if (singleLine) Modifier.heightIn(min = 54.dp) else Modifier), contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
            if (value.isEmpty()) Text(placeholder, style = InType.body.nt, color = Palette.text2, modifier = Modifier.clearAndSetSemantics {})
            BasicTextField(
                value, onValue, singleLine = singleLine, minLines = if (singleLine) 1 else 3,
                textStyle = style.copy(color = Palette.text), cursorBrush = SolidColor(Roles.accentInk),
                keyboardOptions = keyboard, keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }),
                modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = spoken; if (ring != null) error(spoken) }.testTag(tag),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickPicks(values: List<String>, selected: String, tagPrefix: String, onPick: (String) -> Unit) {
    if (values.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v ->
            InChip(v, v.equals(selected.trim(), ignoreCase = true), { onPick(v) }, Modifier.testTag("$tagPrefix.$v"))
        }
    }
}

// A 56-tall tick row: "VIN matches the frame exactly".
@Composable
private fun CheckRow(text: String, checked: Boolean, needed: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
            .toggleable(checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 4.dp).testTag(tag),
        verticalAlignment = Center,
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier.size(28.dp).ring(if (needed && !checked) Roles.danger.ink else null, 6.dp)
                .background(if (checked) Roles.accentTint else DepthTokens.wellFill, shape)
                .border(2.dp, when { checked -> Roles.accentInk; needed -> Roles.danger.ink; else -> Palette.borderStrong }, shape),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Rounded.Check, null, tint = Roles.accentInk, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(text, style = InType.body.nt, color = Palette.text)
    }
}

// ---------- Step 1: VIN or serial number ----------

@Composable
private fun WideButton(text: String, icon: ImageVector, onClick: () -> Unit, tag: String) {
    RaisedSurface(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Center) {
            Icon(icon, null, tint = Roles.accentInk, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = InType.tileLabel.nt, color = Palette.text)
        }
    }
}

// VIN | Serial number: built like Scan's mode tiles, 48 tall. Selected is latched in accent tint.
@Composable
private fun KindSwitch(kind: VinKind, onKind: (VinKind) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(VinKind.VIN to "VIN", VinKind.SERIAL to "Serial number").forEach { (k, word) ->
            val on = k == kind
            val shape = RoundedCornerShape(12.dp)
            if (on) {
                Box(
                    Modifier.weight(1f).height(48.dp).chipDepth(Roles.accentTint, 12.dp).clip(shape)
                        .selectable(true, role = Role.RadioButton) { onKind(k) }.testTag("checkin.vin.kind.${k.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) { Text(word, style = InType.label.nt, color = Roles.accentInk) }
            } else {
                RaisedSurface({ onKind(k) }, Modifier.weight(1f).height(48.dp).testTag("checkin.vin.kind.${k.name.lowercase()}"), role = Role.RadioButton) {
                    Box(contentAlignment = Alignment.Center) { Text(word, style = InType.label.nt, color = Palette.text) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VinStep(ui: CheckInUi, on: CheckInActions) {
    val d = ui.draft
    val update = on.onDraft
    val result = vinResult(d)
    val shown = d.vinShown
    Group {
        WideButton("Scan the VIN barcode", CheckInIcons.BarcodeScanner, on.onScanVin, "checkin.vin.scan")
        KindSwitch(d.vinKind) { k -> update(d.copy(vinKind = k, vinText = cleanVin(d.vinText, k), vinShown = false, frameMatch = false, existing = null, missing = null, missingWhy = null)) }
        val warn = shown && (result is VinResult.WrongLength || result == VinResult.CheckFails)
        WellField(
            d.vinText, { update(d.withVinText(it)) }, "checkin.vin.field", field = Field.VIN,
            spoken = if (d.vinKind == VinKind.VIN) "VIN or serial number, VIN" else "VIN or serial number, serial number",
            placeholder = if (d.vinKind == VinKind.VIN) "Type the VIN" else "Type the serial number", mono = true,
            ring = when { d.missing == Field.VIN && result == VinResult.Empty -> Roles.danger.ink; warn -> Roles.warn.ink; else -> null },
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
            onIme = { update(d.next()) },
        )
        Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Center) {
            if (d.vinKind == VinKind.VIN) {
                Text("${d.vinText.length} of 17", style = InType.meta.nt.copy(fontFeatureSettings = "tnum"), color = Palette.text2,
                    modifier = Modifier.padding(start = 4.dp).testTag("checkin.vin.count"))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.height(24.dp).wrapContentHeight(unbounded = true).offset(x = 12.dp)) {
                TextAction("Where's the VIN?", { update(d.copy(sheet = CheckInSheet.WHERE_VIN)) }, Modifier.testTag("checkin.vin.where"))
            }
        }
        if (d.missing == Field.VIN && result == VinResult.Empty) MissingLine(d.missingWhy.orEmpty(), "checkin.vin.missing")
        if (d.vinText.isEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InChip("Add it later", d.vinAbsent == VinAbsent.LATER,
                    { update(d.copy(vinAbsent = if (d.vinAbsent == VinAbsent.LATER) null else VinAbsent.LATER, missing = null, missingWhy = null)) },
                    Modifier.testTag("checkin.vin.later"), kind = ChipKind.CHECKBOX, leading = Icons.Outlined.Schedule)
                InChip("It has none", d.vinAbsent == VinAbsent.NONE,
                    { update(d.copy(vinAbsent = if (d.vinAbsent == VinAbsent.NONE) null else VinAbsent.NONE, missing = null, missingWhy = null)) },
                    Modifier.testTag("checkin.vin.none"), kind = ChipKind.CHECKBOX, leading = Icons.Outlined.Block)
            }
        }
    }
    if (!shown) return
    val existing = ui.existingCard
    when {
        d.existing != null && existing != null -> Group {
            Line(Icons.Outlined.Info, Roles.info.ink,
                if (d.existing.sold) "Already checked in, and no longer in stock. If it's back as a trade-in, the owner can bring it back in."
                else "Already checked in. Open it instead of checking it in twice.")
            RecordCard(existing, { on.onOpenExisting(d.existing.unitId) }, Modifier.testTag("checkin.vin.existing"), imageWidth = 96.dp)
        }
        result is VinResult.WrongLength -> Banner(
            "VINs have 17 characters. This has ${result.length}. Check it against the frame, or switch to Serial number.")
        result == VinResult.CheckFails -> Group {
            Banner("This VIN doesn't add up. A character is probably misread: 0 and O, 1 and I, 8 and B, 5 and S. " +
                "Some off-road VINs don't follow the check, so if it matches the frame exactly, tick the box.")
            CheckRow("VIN matches the frame exactly", d.frameMatch, d.missing == Field.FRAME_MATCH, "checkin.vin.frameMatch") {
                update(d.copy(frameMatch = it, missing = null, missingWhy = null))
            }
            if (d.missing == Field.FRAME_MATCH && !d.frameMatch) MissingLine(d.missingWhy.orEmpty(), "checkin.vin.frameMissing")
            if (d.frameMatch) LookupBox(d)
        }
        result == VinResult.Passes -> Group {
            Line(Icons.Outlined.CheckCircle, Roles.good.ink, "VIN check digit passes.")
            LookupBox(d)
        }
        else -> Unit
    }
}

@Composable
private fun Line(icon: ImageVector, tint: Color, text: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag("checkin.vin.result")) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp).offset(y = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = InType.body.nt, color = Palette.text)
    }
}

@Composable
private fun Banner(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Roles.warn.tint, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag("checkin.vin.banner"),
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Roles.warn.ink, modifier = Modifier.size(20.dp).offset(y = 2.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = InType.body.nt, color = Palette.text)
    }
}

// "From the VIN lookup": only what came back, then what didn't.
@Composable
private fun LookupBox(d: CheckInDraft) {
    val r = d.lookup?.takeIf { it.vin == checkableVin(d) } ?: return
    when (r.outcome) {
        LookupOutcome.FOUND -> Column(
            Modifier.fillMaxWidth().background(Palette.bg, RoundedCornerShape(12.dp)).border(1.dp, Palette.borderStrong, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag("checkin.vin.lookup"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Center) {
                Icon(CheckInIcons.Barcode, null, tint = Roles.info.ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("From the VIN lookup", style = InType.label.nt, color = Palette.text2)
            }
            val found = lookupFoundLabels(r)
            Row {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { found.forEach { Text(it.first, style = InType.meta.nt, color = Palette.text2) } }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { found.forEach { Text(it.second, style = InType.label.nt, color = Palette.text) } }
            }
            lookupNotFoundLine(r)?.let { Text(it, style = InType.meta.nt, color = Palette.text2) }
        }
        LookupOutcome.RUNNING -> Text("Looking up the VIN…", style = InType.body.nt, color = Palette.text2, modifier = Modifier.testTag("checkin.vin.lookup"))
        LookupOutcome.NOTHING -> Text("The VIN lookup didn't find this one.", style = InType.body.nt, color = Palette.text2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("checkin.vin.lookup"))
        LookupOutcome.OFFLINE -> Text("The VIN lookup needs the internet. You'll type the details in.", style = InType.body.nt, color = Palette.text2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("checkin.vin.lookup"))
        LookupOutcome.FAILED -> Text("The VIN lookup didn't answer properly. You'll type the details in.", style = InType.body.nt, color = Palette.text2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("checkin.vin.lookup"))
    }
}

// ---------- Step 2: Type ----------

// Raised, with the drop shadow taken off and the chip edges: latched like Scan's mode tile.
private fun Modifier.latched(radius: Dp): Modifier = drawBehind {
    val r = CornerRadius(radius.toPx())
    drawRoundRect(Brush.verticalGradient(listOf(DepthTokens.raisedTop, DepthTokens.raisedBottom)), cornerRadius = r)
    val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
    clipPath(path) {
        drawRect(DepthTokens.chipHighlight, size = Size(size.width, 1.dp.toPx()))
        drawRect(DepthTokens.chipShade, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
    }
    val w = 1.dp.toPx()
    drawRoundRect(Palette.borderStrong, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = r, style = Stroke(w))
}

@Composable
private fun TypeStep(d: CheckInDraft, update: (CheckInDraft) -> Unit) {
    val oneColumn = LocalDensity.current.fontScale >= 1.5f
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TYPE_ORDER.chunked(if (oneColumn) 1 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t -> TypeCard(t, d.type == t, Modifier.weight(1f).fillMaxHeight()) { update(d.typed(Field.TYPE) { copy(type = t) }) } }
                if (row.size == 1 && !oneColumn) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (d.missing == Field.TYPE) MissingLine(d.missingWhy.orEmpty(), "checkin.type.missing")
}

@Composable
private fun TypeCard(t: UnitType, selected: Boolean, modifier: Modifier, onPick: () -> Unit) {
    val word = t.shopWord()
    val body: @Composable () -> Unit = {
        Box {
            Column {
                UnitTypeImage(word, Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp).height(92.dp))
                Box(
                    Modifier.padding(8.dp).fillMaxWidth().heightIn(min = 44.dp)
                        .background(if (selected) Roles.accentTint else Color.Transparent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(word, style = InType.tileLabel.nt, color = if (selected) Roles.accentInk else Palette.text) }
            }
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Roles.accentInk, modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).size(24.dp))
        }
    }
    if (selected) {
        Box(
            modifier.latched(12.dp).clip(RoundedCornerShape(12.dp)).selectable(true, role = Role.RadioButton, onClick = onPick)
                .semantics(mergeDescendants = true) { contentDescription = word }.testTag("checkin.type.${t.name}"),
        ) { body() }
    } else {
        RaisedSurface(onPick, modifier.semantics(mergeDescendants = true) { contentDescription = word }.testTag("checkin.type.${t.name}"), role = Role.RadioButton) { body() }
    }
}

// ---------- Steps 3 to 5 ----------

@Composable
private fun MakeStep(ui: CheckInUi, update: (CheckInDraft) -> Unit) {
    val d = ui.draft
    val noAutocorrect = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next)
    Group {
        FieldLabel("Make")
        WellField(d.make, { v -> update(d.typed(Field.MAKE) { copy(make = v) }) }, "checkin.make", "Make", field = Field.MAKE,
            ring = if (d.missing == Field.MAKE) Roles.danger.ink else null,
            keyboard = noAutocorrect.copy(capitalization = KeyboardCapitalization.Words))
        if (Field.MAKE in d.fromLookup) FromVin()
        if (d.missing == Field.MAKE) MissingLine(d.missingWhy.orEmpty(), "checkin.make.missing")
        QuickPicks(ui.makeChips, d.make, "checkin.make.chip") { v -> update(d.typed(Field.MAKE) { copy(make = v) }) }
    }
    Group {
        FieldLabel("Model")
        WellField(d.model, { v -> update(d.typed(Field.MODEL) { copy(model = v) }) }, "checkin.model", "Model", field = Field.MODEL,
            ring = if (d.missing == Field.MODEL) Roles.danger.ink else null, keyboard = noAutocorrect)
        if (Field.MODEL in d.fromLookup) FromVin()
        if (d.missing == Field.MODEL) MissingLine(d.missingWhy.orEmpty(), "checkin.model.missing")
    }
    Group {
        FieldLabel("Sub-model", optional = true)
        WellField(d.subModel, { v -> update(d.copy(subModel = v)) }, "checkin.subModel", "Sub-model, optional",
            placeholder = "Trim or version, like EPS or 4x4", keyboard = noAutocorrect.copy(imeAction = ImeAction.Done), onIme = { update(d.next()) })
    }
}

@Composable
private fun YearStep(ui: CheckInUi, update: (CheckInDraft) -> Unit) {
    val d = ui.draft
    Group {
        FieldLabel("Model year")
        WellField(d.year, { v -> update(d.typed(Field.YEAR) { copy(year = v.filter { it.isDigit() }.take(4)) }) }, "checkin.year", "Model year", field = Field.YEAR,
            ring = if (d.missing == Field.YEAR) Roles.danger.ink else null,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next))
        if (Field.YEAR in d.fromLookup) FromVin()
        if (d.missing == Field.YEAR) MissingLine(d.missingWhy.orEmpty(), "checkin.year.missing")
        QuickPicks(yearChips(ui.today), d.year, "checkin.year.chip") { v -> update(d.typed(Field.YEAR) { copy(year = v) }) }
    }
    Group {
        FieldLabel("Colour")
        WellField(d.colour, { v -> update(d.copy(colour = v)) }, "checkin.colour", "Colour",
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done), onIme = { update(d.next()) })
        QuickPicks(ui.colourChips, d.colour, "checkin.colour.chip") { v -> update(d.copy(colour = v)) }
    }
}

@Composable
private fun PriceStep(d: CheckInDraft, update: (CheckInDraft) -> Unit) {
    Group {
        WellField(d.price, { v -> update(d.typed(Field.PRICE) { copy(price = v.filter { it.isDigit() || it == '.' || it == ',' }) }) }, "checkin.price", "Price, the asking price", field = Field.PRICE,
            prefix = "$", ring = if (d.missing == Field.PRICE) Roles.danger.ink else null,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), onIme = { update(d.next()) })
        if (d.missing == Field.PRICE) MissingLine(d.missingWhy.orEmpty(), "checkin.price.missing")
    }
}

// ---------- Step 6: Review ----------

private fun verbatim(s: String) = s.toList().joinToString(" ")

@Composable
private fun ReviewStep(d: CheckInDraft, update: (CheckInDraft) -> Unit) {
    val vin = savedVin(d)
    val vinLabel = if (d.vinKind == VinKind.SERIAL) "Serial number" else "VIN"
    val vinValue = vin ?: when (d.vinAbsent) { VinAbsent.LATER -> "No VIN yet, adding it later"; else -> "None on this unit" }
    val makeModel = listOf(d.make, d.model, d.subModel).filter { it.isNotBlank() }.joinToString(" ")
    val yearColour = listOf(d.year, d.colour).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { "Not entered" }
    Column(
        Modifier.fillMaxWidth().background(Palette.bg, RoundedCornerShape(12.dp)).border(1.dp, Palette.borderStrong, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        ReviewRow("vin", vinLabel, vinValue, mono = vin != null, fromVin = false,
            spoken = "${if (d.vinKind == VinKind.SERIAL) "Serial number" else "V I N"}, ${vin?.let(::verbatim) ?: vinValue}, Edit") { update(d.edit(CheckInStep.VIN)) }
        ReviewRow("type", "Type", d.type?.shopWord() ?: "Not picked", thumb = d.type?.shopWord(), fromVin = Field.TYPE in d.fromLookup) { update(d.edit(CheckInStep.TYPE)) }
        ReviewRow("make", "Make and model", makeModel, fromVin = Field.MAKE in d.fromLookup || Field.MODEL in d.fromLookup) { update(d.edit(CheckInStep.MAKE)) }
        ReviewRow("year", "Year and colour", yearColour, fromVin = Field.YEAR in d.fromLookup) { update(d.edit(CheckInStep.YEAR)) }
        ReviewRow("price", "Price", priceShown(d), fromVin = false, last = true) { update(d.edit(CheckInStep.PRICE)) }
    }
    Group {
        FieldLabel("Notes", optional = true)
        WellField(d.notes, { update(d.copy(notes = it)) }, "checkin.notes", "Notes, optional",
            placeholder = "Condition, upgrades, anything the next person should know", singleLine = false,
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
    }
}

@Composable
private fun ReviewRow(
    key: String, label: String, value: String, fromVin: Boolean, mono: Boolean = false, thumb: String? = null, last: Boolean = false,
    spoken: String = "$label, $value${if (fromVin) ", from the VIN" else ""}, Edit", onEdit: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .background(if (pressed) Palette.surface else Color.Transparent)
                .clickable(source, indication = null, role = Role.Button, onClick = onEdit)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp).testTag("checkin.review.edit.$key"),
            verticalAlignment = Center,
        ) {
            if (thumb != null) { ThumbFrame(); Spacer(Modifier.width(12.dp)) } else Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = InType.meta.nt, color = Palette.text2)
                Text(value, style = (if (mono) InType.fieldMono.copy(fontSize = 20.sp, letterSpacing = 0.03.em) else InType.cardTitle).nt, color = Palette.text)
                if (fromVin) Row(verticalAlignment = Center) {
                    Icon(CheckInIcons.Barcode, null, tint = Roles.info.ink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("From the VIN", style = InType.meta.nt, color = Palette.text2)
                }
            }
            Text("Edit", style = InType.label.nt, color = Roles.accentInk, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
    }
}

// The 48 × 36 type image slot on Review. Until the shop supplies type images, an empty dashed frame.
@Composable
private fun ThumbFrame() {
    Box(Modifier.size(48.dp, 36.dp).drawBehind {
        drawRoundRect(Palette.borderStrong, topLeft = Offset(0.5f, 0.5f), size = Size(size.width - 1f, size.height - 1f),
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
    })
}

// ---------- Print the tag ----------

@Composable
private fun PrintStep(ui: CheckInUi, on: CheckInActions) {
    val d = ui.draft
    val tag = ui.tag
    val update = on.onDraft
    if (tag != null) {
        Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag("checkin.print.saved"), verticalAlignment = Center) {
            Icon(Icons.Outlined.CheckCircle, null, tint = Roles.good.ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(buildAnnotatedString {
                append("Saved as Incoming. Tag ID ")
                pushTtsAnnotation(VerbatimTtsAnnotation(tag.shortId)); append(tag.shortId); pop()
                append(".")
            }, style = InType.tag.nt, color = Roles.good.ink)
        }
        TagImage(tag)
    }
    when (d.phase) {
        PrintPhase.PRINTED -> Box(Modifier.offset(x = (-12).dp).height(20.dp).wrapContentHeight(unbounded = true)) {
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { update(d.copy(phase = PrintPhase.SEND)) }
                    .padding(horizontal = 12.dp).testTag("checkin.print.again"),
                verticalAlignment = Center,
            ) {
                Icon(Icons.Outlined.Print, null, tint = Roles.accentInk, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Print again", style = InType.label.nt, color = Roles.accentInk)
            }
        }
        PrintPhase.ASK_DID_IT_PRINT -> Column(
            Modifier.fillMaxWidth().background(Palette.bg, RoundedCornerShape(12.dp)).border(1.dp, Palette.borderStrong, RoundedCornerShape(12.dp))
                .padding(16.dp).testTag("checkin.print.ask"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Did the tag print?", style = InType.section.nt, color = Palette.text, modifier = Modifier.semantics { heading() })
            Text("The tag went to the printer app. It can't tell InScan whether it printed.", style = InType.body.nt, color = Palette.text)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RaisedButton("Send again", on.onSend, Modifier.testTag("checkin.print.sendAgain"))
                PrimaryButton("Yes, it printed", on.onYesPrinted, Modifier.weight(1f).testTag("checkin.print.yes"))
            }
        }
        PrintPhase.SEND -> {
            if (tag != null) {
                Column(Modifier.offset(y = (-16).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tag.caption, style = InType.meta.nt, color = Palette.text2)
                    if (!tag.vinFits) Text("The VIN is in the QR code only at this size.", style = InType.meta.nt, color = Palette.text2, modifier = Modifier.testTag("checkin.print.vinNote"))
                }
            }
            PrintRows(ui, on)
        }
    }
}

@Composable
private fun TagImage(tag: TagPreview) {
    if (tag.image != null) {
        Image(
            tag.image, tag.spoken, filterQuality = FilterQuality.None,
            // The tag's white is the paper; multiply keeps black black and turns white into tag stock.
            colorFilter = ColorFilter.tint(Palette.paper, BlendMode.Multiply),
            modifier = Modifier.fillMaxWidth().aspectRatio(tag.image.width.toFloat() / tag.image.height).clip(RoundedCornerShape(Shapes.radiusPlate))
                .border(1.dp, Palette.divider, RoundedCornerShape(Shapes.radiusPlate)).testTag("checkin.print.preview"),
        )
    } else if (tag.problem != null) {
        Text(tag.problem, style = InType.body.nt, color = Palette.text, modifier = Modifier.testTag("checkin.print.tooSmall"))
    }
}

@Composable
private fun PrintRows(ui: CheckInUi, on: CheckInActions) {
    val d = ui.draft
    Column(
        Modifier.fillMaxWidth().background(Palette.bg, RoundedCornerShape(12.dp)).border(1.dp, Palette.borderStrong, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
    ) {
        // No printer driver is built in yet, so there's nothing to pair: the row says so, plainly.
        PrintRow(Icons.Outlined.Print, "No printer paired", "Send the tag to the printer's own app.", "checkin.print.printer", onClick = null)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
        PrintRow(Icons.Outlined.Straighten, "Label size", ui.tag?.sizeLine.orEmpty(), "checkin.print.size", onClick = on.onLabelSize)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Center) {
            Icon(Icons.Outlined.ContentCopy, null, tint = Palette.text2, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Copies", style = InType.label.nt, color = Palette.text)
                Text("One per box", style = InType.tag.nt, color = Palette.text2, modifier = Modifier.padding(top = 2.dp))
            }
            Row(verticalAlignment = Center, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepButton(Icons.Rounded.Remove, "One fewer copy", d.copies > 1) { on.onDraft(d.copy(copies = d.copies - 1)) }
                Text("${d.copies}", style = InType.bigNumber.nt, color = Palette.text, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 28.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "${d.copies} ${if (d.copies == 1) "copy" else "copies"}" }
                        .testTag("checkin.print.copies"))
                StepButton(Icons.Rounded.Add, "One more copy", d.copies < 5) { on.onDraft(d.copy(copies = d.copies + 1)) }
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, spoken: String, enabled: Boolean, onClick: () -> Unit) {
    RaisedSurface(onClick, Modifier.size(48.dp).semantics { contentDescription = spoken }, enabled = enabled) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (enabled) Palette.text else Palette.text2, modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun PrintRow(icon: ImageVector, title: String, value: String, tag: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp).testTag(tag),
        verticalAlignment = Center,
    ) {
        Icon(icon, null, tint = Palette.text2, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = InType.label.nt, color = Palette.text)
            Text(value, style = InType.tag.nt, color = Palette.text2, modifier = Modifier.padding(top = 2.dp))
        }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = Palette.text2, modifier = Modifier.padding(start = 12.dp).size(24.dp))
    }
}

// ---------- Sheets ----------

@Composable
private fun BoxScope.Sheet(title: String, body: String?, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.matchParentSize().background(DepthTokens.scrimModal)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss).testTag("checkin.sheet.scrim"),
    )
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter), enter = slideInVertically(tween(Motion.sheetMs, easing = Motion.out)) { it }) {
        Column(
            Modifier.fillMaxWidth()
                .background(Palette.surfaceRaised, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .drawBehind {
                    val r = 16.dp.toPx()
                    drawRoundRect(Palette.borderStrong, size = Size(size.width, r * 2), cornerRadius = CornerRadius(r), style = Stroke(1.dp.toPx()))
                    drawRect(Palette.surfaceRaised, topLeft = Offset(0f, r), size = Size(size.width, r + 1.dp.toPx()))
                }
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .semantics { paneTitle = title }
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp + bottom)
                .testTag("checkin.sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = InType.section.nt, color = Palette.text, modifier = Modifier.semantics { heading() })
            if (body != null) Text(body, style = InType.body.nt, color = Palette.text)
            content()
        }
    }
}

@Composable
private fun SheetActions(left: String, onLeft: () -> Unit, leftTag: String, right: String, onRight: () -> Unit, rightTag: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RaisedButton(left, onLeft, Modifier.testTag(leftTag))
        PrimaryButton(right, onRight, Modifier.weight(1f).testTag(rightTag))
    }
}
