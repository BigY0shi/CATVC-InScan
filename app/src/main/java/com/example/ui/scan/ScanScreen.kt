package com.example.ui.scan

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.VerbatimTtsAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.example.ui.inscan.ChipSurface
import com.example.ui.inscan.DepthTokens
import com.example.ui.inscan.IdPlate
import com.example.ui.inscan.InType
import com.example.ui.inscan.Motion
import com.example.ui.inscan.Palette
import com.example.ui.inscan.PrimaryButton
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.RaisedSurface
import com.example.ui.inscan.RecordCard
import com.example.ui.inscan.Roles
import com.example.ui.inscan.Shapes
import com.example.ui.inscan.Space
import com.example.ui.inscan.TextAction
import com.example.ui.inscan.UnitTypeImage
import com.example.ui.inscan.chipDepth
import com.example.ui.inscan.well

// The Scan screen, stateless. [preview] draws the camera picture: CameraPreviewScanner on a phone,
// a plain placeholder in screenshot tests (the camera can't run there). [safe] is the status-bar and
// navigation-bar space: the picture runs full-bleed behind it, the controls stay out of it.
@Composable
fun ScanScreen(
    state: ScanUi,
    safe: PaddingValues,
    preview: @Composable () -> Unit,
    onMode: (ScanMode) -> Unit,
    onCancel: () -> Unit,
    onLight: () -> Unit,
    onTyped: (String) -> Unit,
    onLookUp: () -> Unit,
    onNoTag: () -> Unit,
    onPermission: () -> Unit,
    onCard: (CardAction) -> Unit,
    onOpenUnit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = state.card
    val live = state.mode == ScanMode.CAMERA && state.camera == null && !state.typedView
    Box(modifier.fillMaxSize().background(Palette.bg)) {
        if (live) Box(Modifier.fillMaxSize()) { preview() }
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(safe.calculateTopPadding()))
            TitleStrip(if (state.typedView) "Type a tag ID or VIN" else "Scan a tag or VIN", state.firstName)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.typedView -> TypeItForm(state, onTyped, onLookUp, onNoTag)
                    state.camera != null -> CameraProblem(state.camera, onPermission)
                    else -> Picture(state, onLight)
                }
                if (card != null) ResultCard(card, state.moving, onCard, onOpenUnit, Modifier.align(Alignment.BottomCenter))
            }
            Panel(state, card != null, safe, onMode, onCancel)
        }
    }
}

// Title strip: bg at 80% over the picture, no blur. Says whose name goes on a status change.
@Composable
private fun TitleStrip(title: String, firstName: String) {
    Row(
        // 56 tall; with large text it grows instead of cutting the title.
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(DepthTokens.overCamera).padding(horizontal = Space.gutter, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = InType.section, color = Palette.text,
            modifier = Modifier.weight(1f).testTag("scan.title").semantics { heading() })
        Spacer(Modifier.width(12.dp))
        Text("Scanning as $firstName", style = InType.meta, color = Palette.text2, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 160.dp))
    }
}

// ---------- Camera picture: brackets, instruction, Light ----------

@Composable
private fun Picture(state: ScanUi, onLight: () -> Unit) {
    val bracket = state.card?.tone?.ink ?: Palette.text
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The frame stays put when the card comes up (the panel loses its 76 dp mode row then).
        val top = (maxHeight - (if (state.card != null) 76.dp else 0.dp) - 220.dp) / 2
        Brackets(bracket, Modifier.align(Alignment.TopCenter).padding(top = top.coerceAtLeast(0.dp)).size(280.dp, 220.dp))
        if (state.card == null) {
            Instruction(state, Modifier.align(Alignment.TopCenter).padding(top = top.coerceAtLeast(0.dp) + 236.dp, start = 21.dp, end = 21.dp))
            if (state.torch != Torch.NONE) LightChip(state.torch == Torch.ON, onLight, Modifier.align(Alignment.BottomStart).padding(16.dp))
        }
    }
}

// Four corner brackets: 4 dp stroke, 28 dp arms, 14 dp outer radius. Guidance only.
@Composable
private fun Brackets(color: Color, modifier: Modifier) {
    Box(modifier.drawBehind {
        val w = 4.dp.toPx(); val arm = 28.dp.toPx(); val r = 14.dp.toPx() - w / 2; val h = w / 2
        val stroke = Stroke(w, cap = StrokeCap.Butt)
        fun corner(flipX: Boolean, flipY: Boolean) {
            fun x(v: Float) = if (flipX) size.width - v else v
            fun y(v: Float) = if (flipY) size.height - v else v
            val p = Path()
            p.moveTo(x(h), y(arm))
            p.lineTo(x(h), y(h + r))
            val rect = Rect(Offset(minOf(x(h), x(h + 2 * r)), minOf(y(h), y(h + 2 * r))), Size(2 * r, 2 * r))
            val start = when { !flipX && !flipY -> 180f; flipX && !flipY -> 0f; !flipX && flipY -> 180f; else -> 0f }
            val sweep = if (flipX == flipY) 90f else -90f
            p.arcTo(rect, start, sweep, false)
            p.lineTo(x(arm), y(h))
            drawPath(p, color, style = stroke)
        }
        corner(false, false); corner(true, false); corner(false, true); corner(true, true)
    })
}

@Composable
private fun Instruction(state: ScanUi, modifier: Modifier) {
    val text = when {
        !state.hint -> "Point at the QR tag or the VIN barcode."
        state.torch != Torch.NONE -> "Not reading? Turn on the light, or type it in."
        else -> "Not reading? Move closer, or type it in."
    }
    Row(
        modifier.widthIn(max = 360.dp).background(DepthTokens.overCamera, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.hint) { Icon(Icons.Outlined.Lightbulb, null, tint = Roles.warn.ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = InType.body, color = Palette.text)
    }
}

// Light: built like Home's signed-in chip. On: accent tint and accent ink, like the selected nav pill.
@Composable
private fun LightChip(on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (on) Icons.Rounded.FlashlightOn else Icons.Outlined.FlashlightOn, null, tint = if (on) Roles.accentInk else Palette.text2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Light", style = InType.label, color = if (on) Roles.accentInk else Palette.text)
        }
    }
    val m = modifier.height(48.dp).testTag("scan.light").semantics { selected = on }
    if (on) {
        Box(m.chipDepth(Roles.accentTint, 24.dp).clip(RoundedCornerShape(24.dp)).clickable(role = Role.Switch, onClick = onClick),
            contentAlignment = Alignment.Center) { content() }
    } else {
        ChipSurface(onClick, m) { Box(contentAlignment = Alignment.Center) { content() } }
    }
}

// ---------- Camera not allowed / couldn't start ----------

@Composable
private fun CameraProblem(issue: CameraIssue, onPermission: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.bg).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        val couldnt = issue == CameraIssue.COULDNT_START
        Box(Modifier.padding(bottom = 4.dp).size(72.dp).background(Roles.neutral.tint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (couldnt) Icons.Rounded.VideocamOff else Icons.Rounded.NoPhotography, null, tint = Roles.neutral.ink, modifier = Modifier.size(36.dp))
        }
        Text(if (couldnt) "The camera couldn't start" else "Camera isn't allowed", style = InType.section, color = Palette.text,
            textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(if (couldnt) "Type the tag ID instead, or try again." else "InScan needs the camera to read tags. You can still type the tag ID.",
            style = InType.body, color = Palette.text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 370.dp))
        val (label, tag) = when (issue) {
            CameraIssue.NOT_ALLOWED -> "Allow camera" to "scan.permission.allow"
            CameraIssue.NOT_ALLOWED_SETTINGS -> "Open settings" to "scan.permission.settings"
            CameraIssue.COULDNT_START -> "Try again" to "scan.camera.retry"
        }
        PrimaryButton(label, onPermission, Modifier.padding(top = 12.dp).fillMaxWidth().testTag(tag))
    }
}

// ---------- Type it ----------

@Composable
private fun TypeItForm(state: ScanUi, onTyped: (String) -> Unit, onLookUp: () -> Unit, onNoTag: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.mode, state.card == null) {
        if (state.mode == ScanMode.TYPE && state.card == null) runCatching { focus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().background(Palette.bg).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).well().padding(start = 16.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = state.typed,
                onValueChange = onTyped,
                // Shown in capitals; a whole tag typed by a Bluetooth scanner keeps its own case.
                visualTransformation = { t ->
                    if (t.text.startsWith("CATV1;")) TransformedText(t, OffsetMapping.Identity)
                    else TransformedText(AnnotatedString(t.text.uppercase()), OffsetMapping.Identity)
                },
                singleLine = true,
                textStyle = InType.fieldMono.copy(color = Palette.text, letterSpacing = 0.04.em),
                cursorBrush = SolidColor(Roles.accentInk),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onLookUp() }),
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("scan.type.field")
                    .semantics { contentDescription = "Tag ID or VIN" },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.typed.isEmpty()) Text("Tag ID or VIN", style = InType.body, color = Palette.text2)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(typedCount(state.typed), style = InType.meta, color = Palette.text2)
        }
        Box(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 16.dp).heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            val wrong = state.typedWrongLength
            if (wrong != null) {
                Row {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Roles.danger.ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tag IDs have 8 characters, VINs 17. You typed $wrong.", style = InType.meta, color = Palette.text)
                }
            } else {
                Text("The 8 characters after ID on the tag, or the 17‑character VIN.", style = InType.meta, color = Palette.text2)
            }
        }
        PrimaryButton(lookUpLabel(state.typed), onLookUp, Modifier.fillMaxWidth().testTag("scan.type.lookup"))
        TextAction("No tag or VIN? Check it in without one", onNoTag,
            Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally).testTag("scan.type.noTag"))
    }
}

// ---------- Bottom panel: mode switch over a Cancel bar that never moves ----------

@Composable
private fun Panel(state: ScanUi, cardUp: Boolean, safe: PaddingValues, onMode: (ScanMode) -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Palette.surface)
            .drawBehind { drawRect(Palette.divider, size = Size(size.width, 1.dp.toPx())) }
            // `safe` is safeDrawing, which already includes the keyboard; adding imePadding here too
            // counted the keyboard twice and squeezed the Type it form to nothing on a real phone.
            .padding(start = Space.gutter, end = Space.gutter, top = 12.dp, bottom = 16.dp + safe.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // When the result card is up, the mode switch hides and the card sits on top of Cancel.
        if (!cardUp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeTile("Camera", if (!state.typedView) Icons.Rounded.PhotoCamera else Icons.Outlined.PhotoCamera, !state.typedView, { onMode(ScanMode.CAMERA) }, Modifier.weight(1f).testTag("scan.mode.camera"))
            ModeTile("Type it", Icons.Rounded.Keyboard, state.typedView, { onMode(ScanMode.TYPE) }, Modifier.weight(1f).testTag("scan.mode.type"))
        }
        RaisedSurface(onCancel, Modifier.fillMaxWidth().height(64.dp).testTag("scan.cancel")) {
            Box(contentAlignment = Alignment.Center) { Text("Cancel", style = InType.tileLabel, color = Palette.text) }
        }
    }
}

@Composable
private fun ModeTile(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val content: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) Roles.accentInk else Palette.text2, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = InType.tileLabel, color = if (selected) Roles.accentInk else Palette.text)
        }
    }
    val m = modifier.height(64.dp).semantics { this.selected = selected }
    if (selected) {
        Box(m.chipDepth(Roles.accentTint, Shapes.radiusCard).clip(RoundedCornerShape(Shapes.radiusCard)).clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center) { content() }
    } else {
        RaisedSurface(onClick, m) { Box(contentAlignment = Alignment.Center) { content() } }
    }
}

// ---------- Result card ----------

private val BarcodeIcon: ImageVector = ImageVector.Builder("Barcode", 24.dp, 24.dp, 24f, 24f).apply {
    // Plain vertical bars, the "barcode" symbol (Material Icons has no barcode glyph).
    path(fill = SolidColor(Color.Black)) {
        listOf(2f to 2f, 5f to 1f, 7.5f to 2f, 11f to 1f, 13.5f to 2.5f, 17.5f to 1f, 20f to 2f).forEach { (x, w) ->
            moveTo(x, 5f); lineTo(x + w, 5f); lineTo(x + w, 19f); lineTo(x, 19f); close()
        }
    }
}.build()

private fun ReadKind.icon(): Pair<ImageVector, String> = when (this) {
    ReadKind.TAG -> Icons.Rounded.QrCode2 to "Read a QR tag"
    ReadKind.BARCODE -> BarcodeIcon to "Read a barcode"
    ReadKind.TYPED -> Icons.Rounded.Keyboard to "Typed"
}

private fun reduceMotion(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
private fun ResultCard(card: ScanCard, moving: Boolean, onCard: (CardAction) -> Unit, onOpenUnit: (String) -> Unit, modifier: Modifier) {
    // Taps in the card's first 250 ms are ignored, so a tap meant for what was under the finger
    // can't land on the primary.
    val openedAt = remember(card) { SystemClock.uptimeMillis() }
    fun guarded(f: () -> Unit) { if (SystemClock.uptimeMillis() - openedAt >= 250) f() }
    val head = remember(card) { FocusRequester() }
    LaunchedEffect(card) { runCatching { head.requestFocus() } }
    val reduce = reduceMotion(LocalContext.current)
    val shown = remember(card) { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        shown, modifier,
        enter = if (reduce) fadeIn(tween(120)) else slideInVertically(tween(Motion.sheetMs, easing = Motion.out)) { it },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .drawBehind {
                    val r = Shapes.radiusSheet.toPx()
                    drawRoundRect(Palette.surface, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), size = Size(size.width, size.height + r))
                    val p = Path().apply {
                        moveTo(0f, r); arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
                        lineTo(size.width - r, 0f); arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), 270f, 90f, false)
                    }
                    drawPath(p, Palette.divider, style = Stroke(1.dp.toPx()))
                }
                .clip(RoundedCornerShape(topStart = Shapes.radiusSheet, topEnd = Shapes.radiusSheet))
                .testTag("scan.result")
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The body scrolls; the buttons stay pinned. The card never grows above the title strip.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val (icon, spoken) = card.read.icon()
                Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, spoken, tint = Palette.text2, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(card.title, style = InType.section, color = Palette.text,
                        modifier = Modifier.focusRequester(head).focusable().semantics { heading() })
                }
                card.record?.let { rec ->
                    RecordCard(rec, { guarded { card.unitId?.let(onOpenUnit) } }, Modifier.testTag("scan.result.open"), imageWidth = 96.dp)
                }
                card.tag?.let { TagSummaryCard(it) }
                card.vin?.let { VinBlock(it) }
                if (card.facts.isNotEmpty()) FactsRow(card.facts)
                card.note?.let { Note(it) }
            }
            Actions(card, moving) { a -> guarded { onCard(a) } }
        }
    }
}

// The tag's own details when the unit isn't on this phone: the record card's look, not tappable.
@Composable
private fun TagSummaryCard(t: TagSummary) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .background(Palette.bg, RoundedCornerShape(Shapes.radiusCard))
            .border(1.dp, Palette.borderStrong, RoundedCornerShape(Shapes.radiusCard))
            .semantics(mergeDescendants = true) {}
            .padding(start = Space.card, top = Space.card, bottom = Space.card, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UnitTypeImage(t.typeWord, Modifier.width(96.dp).heightIn(min = 96.dp).fillMaxHeight())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            IdPlate(t.id, Modifier.padding(bottom = 2.dp))
            Text(t.name, style = InType.cardTitle, color = Palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(t.meta, style = InType.meta, color = Palette.text2)
        }
    }
}

@Composable
private fun VinBlock(vin: String) {
    Column(
        Modifier.fillMaxWidth().background(Palette.bg, RoundedCornerShape(12.dp)).border(1.dp, Palette.borderStrong, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
    ) {
        Text("VIN", style = InType.meta, color = Palette.text2)
        Spacer(Modifier.height(2.dp))
        Text(buildAnnotatedString { pushTtsAnnotation(VerbatimTtsAnnotation(vin)); append(vin); pop() },
            style = InType.fieldMono.copy(letterSpacing = 0.04.em), color = Palette.text)
    }
}

@Composable
private fun FactsRow(facts: List<Fact>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.forEach { f ->
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(f.label, style = InType.meta, color = Palette.text2)
                Text(f.value, style = if (f.mono) InType.id else InType.label, color = Palette.text)
            }
        }
        repeat(3 - facts.size.coerceAtMost(3)) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun Note(n: ScanNote) {
    if (n.warn) {
        Row(Modifier.fillMaxWidth().background(Roles.warn.tint, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Roles.warn.ink, modifier = Modifier.padding(top = 2.dp).size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(n.text, style = InType.body, color = Palette.text)
        }
    } else {
        Row(Modifier.fillMaxWidth()) {
            if (n.icon != null) {
                Icon(n.icon, null, tint = n.iconTint ?: Palette.text, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(n.text, style = InType.body, color = Palette.text)
        }
    }
}

// Scan-again left, primary right; one button spans the width. They stack (scan-again on top) when
// they don't fit side by side, as with text at 1.5x and up.
@Composable
private fun Actions(card: ScanCard, moving: Boolean, onCard: (CardAction) -> Unit) {
    @Composable
    fun button(b: CardButton, m: Modifier) {
        val tag = when {
            b.action == CardAction.CHECK_IN_ANYWAY -> "scan.result.checkInAnyway"
            b.action == CardAction.SCAN_AGAIN || b.action == CardAction.TYPE_AGAIN -> "scan.result.again"
            else -> "scan.result.primary"
        }
        if (b.primary) PrimaryButton(b.label, { onCard(b.action) }, m.testTag(tag), enabled = !(moving && b.action == CardAction.MOVE))
        else RaisedButton(b.label, { onCard(b.action) }, m.testTag(tag))
    }
    val stack = LocalDensity.current.fontScale >= 1.5f
    Box(Modifier.padding(top = 4.dp)) {
        when {
            card.buttons.size == 1 -> button(card.buttons[0], Modifier.fillMaxWidth())
            stack -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                card.buttons.forEach { button(it, Modifier.fillMaxWidth()) }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                button(card.buttons[0], Modifier)
                button(card.buttons[1], Modifier.weight(1f))
            }
        }
    }
}
