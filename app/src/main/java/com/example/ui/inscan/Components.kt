package com.example.ui.inscan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.VerbatimTtsAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------- Tactile controls ----------

// Any raised tactile control. Pressing it presses in (flat fill + inner shadow).
@Composable
fun RaisedSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    radius: Dp = Shapes.radiusCard,
    role: Role = Role.Button,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .raised(pressed, radius, enabled)
            .clip(RoundedCornerShape(radius))
            .clickable(source, indication = null, enabled = enabled, role = role, onClick = onClick),
        propagateMinConstraints = true,
    ) { content() }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .heightIn(min = Space.touchPrimary)
            .scale(if (pressed && enabled) 0.98f else 1f)
            .primaryFill(pressed, enabled = enabled)
            .clip(RoundedCornerShape(Shapes.radiusCard))
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val c = if (enabled) Roles.onAccent else Palette.text2
        if (icon != null) { Icon(icon, null, tint = c, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = InType.tileLabel, color = c, textAlign = TextAlign.Center)
    }
}

// Neutral raised button (notice cards, secondary actions). Never emerald: Scan stays the only emerald fill.
@Composable
fun RaisedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    RaisedSurface(onClick, modifier.heightIn(min = Space.touchPrimary), enabled) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            val c = if (enabled) Palette.text else Palette.text2
            if (icon != null) { Icon(icon, null, tint = if (enabled) Roles.accentInk else c, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = InType.tileLabel, color = c, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .heightIn(min = Space.touch)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = InType.label, color = Roles.accentInk)
        if (icon != null) { Spacer(Modifier.width(6.dp)); Icon(icon, null, tint = Roles.accentInk, modifier = Modifier.size(20.dp)) }
    }
}

// ---------- Information (flat) ----------

@Composable
fun StatusTag(look: StatusLook, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(look.icon, null, tint = look.tone.ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(look.word, style = InType.tag, color = look.tone.ink)
    }
}

@Composable
fun IconBadge(icon: ImageVector, tone: Tone, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier.size(size).background(tone.tint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tone.ink, modifier = Modifier.size(size * 0.6f))
    }
}

// The 8-character ID as printed on the box tag: a light plate, mono bold. Read character by character.
@Composable
fun IdPlate(id: String, modifier: Modifier = Modifier) {
    val spoken = buildAnnotatedString {
        pushTtsAnnotation(VerbatimTtsAnnotation(id)); append(id); pop()
    }
    Text(
        spoken, style = InType.idPlate, color = Palette.bg,
        modifier = modifier.background(Palette.text, RoundedCornerShape(Shapes.radiusPlate)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
// The header row is as tall as its title (as in the mockup); a trailing action keeps its 48 dp
// touch target by overflowing the row instead of pushing the section down.
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = InType.section, color = Palette.text, modifier = Modifier.weight(1f))
        if (action != null) Box(Modifier.height(28.dp).wrapContentHeight(unbounded = true), contentAlignment = Alignment.CenterEnd) { action() }
    }
}

// Unit-type image slot. Real images are supplied by the shop; until a type has one, the frame
// says which type it is, exactly as the mockups draw it. Never a drawn vehicle.
@Composable
fun UnitTypeImage(typeWord: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Palette.surfaceRaised, RoundedCornerShape(Shapes.radiusImg))
            .drawBehind {
                drawRoundRect(Palette.borderStrong, topLeft = Offset(0.5f, 0.5f), size = Size(size.width - 1f, size.height - 1f),
                    cornerRadius = CornerRadius(Shapes.radiusImg.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) { Text("$typeWord image", style = InType.meta, color = Palette.text2, textAlign = TextAlign.Center) }
}

data class RecordCardData(
    val id: String,
    val name: String,
    val meta: String,
    val status: StatusLook,
    val typeWord: String,
    val spoken: String,
)

// Record card (flat): opens a detail screen, so it gets a chevron instead of depth. One element for TalkBack.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordCard(data: RecordCardData, onClick: () -> Unit, modifier: Modifier = Modifier, imageWidth: Dp = 128.dp) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(Shapes.radiusCard))
            .background(if (pressed) Palette.surface else Palette.bg)
            .border(1.dp, Palette.borderStrong, RoundedCornerShape(Shapes.radiusCard))
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = data.spoken }
            .padding(start = Space.card, top = Space.card, bottom = Space.card, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UnitTypeImage(data.typeWord, Modifier.width(imageWidth).heightIn(min = 96.dp).fillMaxHeight())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlowRow(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IdPlate(data.id)
                StatusTag(data.status, Modifier.align(Alignment.CenterVertically))
            }
            Text(data.name, style = InType.cardTitle, color = Palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(data.meta, style = InType.meta, color = Palette.text2)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Palette.text2, modifier = Modifier.size(24.dp).align(Alignment.CenterVertically))
    }
}

// Status banner: tinted fill, text-colour message, ink icon. Amber = offline, ocean = sending.
@Composable
fun StatusBanner(icon: ImageVector, tone: Tone, title: String, body: String?, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).background(tone.tint).padding(horizontal = Space.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tone.ink, modifier = Modifier.size(20.dp).align(Alignment.Top).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = InType.tileLabel, color = Palette.text)
            if (body != null) Text(body, style = InType.meta, color = Palette.text)
        }
        action?.invoke()
    }
}

// Notice card: something someone has to act on. Flat surface, strong border, neutral raised button.
@Composable
fun NoticeCard(icon: ImageVector, tone: Tone, title: String, body: String, button: String?, onButton: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth()
            .background(Palette.surface, RoundedCornerShape(Shapes.radiusNotice))
            .border(1.dp, Palette.borderStrong, RoundedCornerShape(Shapes.radiusNotice))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tone.ink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = InType.noticeTitle, color = Palette.text)
        }
        Text(body, style = InType.body, color = Palette.text)
        if (button != null) RaisedButton(button, onButton, Modifier.fillMaxWidth())
    }
}

// ---------- Top bar ----------

// Interactive chip (Find, signed-in chip, filter chips): raised fill, strong border, chip edges.
@Composable
fun ChipSurface(onClick: () -> Unit, modifier: Modifier = Modifier, fill: androidx.compose.ui.graphics.Color = Palette.surfaceRaised, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .scale(if (pressed) 0.985f else 1f)
            .then(if (pressed) Modifier.raised(true, 24.dp) else Modifier.chipDepth(fill, 24.dp, bordered = true))
            .clip(RoundedCornerShape(24.dp))
            .clickable(source, indication = null, role = Role.Button, onClick = onClick),
        propagateMinConstraints = true,
    ) { content() }
}

@Composable
fun FindButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ChipSurface(onClick, modifier.height(48.dp)) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = Roles.accentInk, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text("Find", style = InType.label, color = Palette.text)
        }
    }
}

fun initialsOf(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    .let { parts -> if (parts.size >= 2) "${parts[0][0]}${parts[1][0]}" else parts.firstOrNull()?.take(2) ?: "?" }.uppercase()

@Composable
fun SignedInChip(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ChipSurface(onClick, modifier.height(48.dp)) {
        Row(Modifier.padding(start = 7.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(Roles.accentTint, CircleShape), contentAlignment = Alignment.Center) {
                Text(initialsOf(name), style = InType.tag.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Roles.accentInk)
            }
            Spacer(Modifier.width(8.dp))
            Text(name.trim().split(" ").first(), style = InType.label, color = Palette.text)
            Icon(Icons.Rounded.ExpandMore, null, tint = Palette.text2, modifier = Modifier.size(20.dp))
        }
    }
}

// Top app bar: flat surface, 1 dp bottom divider, title left, actions right.
@Composable
fun InTopBar(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth().background(Palette.surface)) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = Space.gutter, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = InType.appTitle, color = Palette.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
    }
}

// Screen body for simple screens (More, placeholders): screen title + content in the gutters.
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(Space.item), content = content)
}

fun Modifier.testTagIf(tag: String?): Modifier = if (tag != null) testTag(tag) else this
