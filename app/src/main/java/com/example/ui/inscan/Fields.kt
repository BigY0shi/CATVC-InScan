package com.example.ui.inscan

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable

// The Find field: an inset well at least 56 dp tall, a search icon, typed text in capitals,
// no autocorrect, and an accent-ink Clear once something is typed. Used by Stock and the search sheet.
@Composable
fun FindField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onSearch: () -> Unit = {},
    onClear: () -> Unit = { onValueChange("") },
    tag: String = "find",
) {
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (focused) Modifier.focusRing(Shapes.radiusCard) else Modifier)
            .well(Shapes.radiusCard)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Palette.text2, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).heightIn(min = 54.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Find a unit", style = InType.body, color = Palette.text2)
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.uppercase()) },
                singleLine = true,
                textStyle = InType.body.copy(color = Palette.text),
                cursorBrush = SolidColor(Roles.accentInk),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSearch() }),
                modifier = Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "Find a unit" }
                    .testTag("$tag.field"),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onClear)
                    .padding(horizontal = 12.dp)
                    .testTag("$tag.clear"),
                contentAlignment = Alignment.Center,
            ) { Text("Clear", style = InType.label, color = Roles.accentInk) }
        }
    }
}

// The 3 dp accent-ink focus outline, 2 dp outside the control.
fun Modifier.focusRing(radius: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    val gap = 2.dp.toPx(); val w = 3.dp.toPx()
    drawRoundRect(
        Roles.accentInk, topLeft = Offset(-gap - w / 2, -gap - w / 2), size = Size(size.width + 2 * gap + w, size.height + 2 * gap + w),
        cornerRadius = CornerRadius(radius.toPx() + gap + w / 2), style = Stroke(w),
    )
}

enum class ChipKind { RADIO, CHECKBOX, SWITCH, BUTTON }

// Interactive pill chip (status chips, filter options, applied filters): 48 dp tall, raised,
// press feedback (pressed layer, 0.985 scale). Selected: a check, weight 600 and accent tint.
@Composable
fun InChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    kind: ChipKind = ChipKind.RADIO,
    enabled: Boolean = true,
    raisedFill: Boolean = false,
    trailing: ImageVector? = null,
    // A leading icon in the chip's ink (check-in's "Add it later"); a selected chip shows its check instead.
    leading: ImageVector? = null,
    spoken: String = if (count != null) "$label, $count" else label,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(24.dp)
    val ink = when { !enabled -> Palette.text2; selected -> Roles.accentInk; else -> Palette.text }
    val action = when (kind) {
        ChipKind.RADIO -> Modifier.selectable(selected, interactionSource = source, indication = null, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        ChipKind.CHECKBOX -> Modifier.toggleable(selected, interactionSource = source, indication = null, enabled = enabled, role = Role.Checkbox) { onClick() }
        ChipKind.SWITCH -> Modifier.toggleable(selected, interactionSource = source, indication = null, enabled = enabled, role = Role.Switch) { onClick() }
        ChipKind.BUTTON -> Modifier.clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .scale(if (pressed && enabled) 0.985f else 1f)
            .then(
                when {
                    !enabled -> Modifier.chipFill(Palette.surface, Palette.divider)
                    pressed -> Modifier.raised(true, 24.dp)
                    selected -> Modifier.chipDepth(Roles.accentTint, 24.dp).chipBorder(Roles.accentInk)
                    raisedFill -> Modifier.raised(false, 24.dp)
                    else -> Modifier.chipDepth(Palette.surfaceRaised, 24.dp, bordered = true)
                }
            )
            .clip(shape)
            .then(action)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(start = if ((selected && kind != ChipKind.BUTTON) || leading != null) 10.dp else 14.dp, end = if (trailing != null) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected && kind != ChipKind.BUTTON) { Icon(Icons.Rounded.Check, null, tint = Roles.accentInk, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        else if (leading != null) { Icon(leading, null, tint = ink, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = InType.label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium), color = ink)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = InType.label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                color = if (selected) Roles.accentInk else Palette.text2)
        }
        if (trailing != null) { Spacer(Modifier.width(6.dp)); Icon(trailing, null, tint = Roles.accentInk, modifier = Modifier.size(20.dp)) }
    }
}

// A chip built like Find (Sort, Filter, Export): accent-ink icons, padding 0 14 0 10, gap 6.
@Composable
fun ToolChip(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: ImageVector? = null, spoken: String = label) {
    ChipSurface(onClick, modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Row(Modifier.padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Roles.accentInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = InType.label, color = Palette.text)
            if (trailing != null) { Spacer(Modifier.width(6.dp)); Icon(trailing, null, tint = Roles.accentInk, modifier = Modifier.size(20.dp)) }
        }
    }
}

private fun Modifier.chipBorder(color: Color): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRoundRect(color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(24.dp.toPx()), style = Stroke(w))
}

private fun Modifier.chipFill(fill: Color, border: Color): Modifier = drawBehind {
    drawRoundRect(fill, cornerRadius = CornerRadius(24.dp.toPx()))
    val w = 1.dp.toPx()
    drawRoundRect(border, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = CornerRadius(24.dp.toPx()), style = Stroke(w))
}
