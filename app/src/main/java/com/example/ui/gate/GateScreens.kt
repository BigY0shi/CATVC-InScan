package com.example.ui.gate

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.inscan.DepthTokens
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.example.ui.inscan.PrimaryButton
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.Roles
import com.example.ui.inscan.Space
import com.example.ui.inscan.TextAction
import com.example.ui.inscan.initialsOf
import com.example.ui.inscan.raised
import com.example.ui.inscan.well
import kotlin.math.min

// The sign-in gate (scan-pin-ui-spec.md section 3, prototypes/pin.html; LOCKED 2026-10-30).
// Every screen here is stateless: it draws a state object, so each row of the spec's state table
// is a screenshot test. SignInGate.kt holds the state and talks to the shop system.

sealed interface GateUi {
    // "Who's using this phone?" [people] is null while the list loads.
    data class Who(val people: List<String>?, val phoneName: String?, val emptyMessage: String? = null) : GateUi

    data class Pin(val pin: PinUi) : GateUi

    // Can't reach the shop system. [signingInAs] is null when even the list of people couldn't load.
    // [stillSignedIn] is someone with a valid session on this phone; only then do "Log back in" and the
    // meta line show.
    data class Unreachable(val signingInAs: String?, val lastTried: String?, val stillSignedIn: String?) : GateUi
}

enum class PinPhase { ENTRY, CHECKING, WRONG, LOCKED }

data class PinUi(
    val name: String,
    val digits: Int = 0,
    val phase: PinPhase = PinPhase.ENTRY,
    // Message line: [line1] in danger ink for a wrong PIN, [line2] in text.
    val line1: String? = null,
    val line2: String? = null,
    // "Checking…" with a spinner, only once checking takes over 300 ms.
    val slowCheck: Boolean = false,
    // Dots fade out over 120 ms before the boxes clear.
    val clearing: Boolean = false,
    // "2:19 pm" when the shop system says until when. Without it, today's copy is used.
    val lockedUntil: String? = null,
    val idle: Boolean = false,
    // Bumped on each wrong PIN to play the shake once.
    val shake: Int = 0,
)

// The shop photo goes here once Yoshi supplies it, under the 80% scrim. Until then the gate shows
// plain charcoal (never a stock photo, never a texture).
@Composable
private fun GateBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Palette.bg)) { Box(Modifier.fillMaxSize().background(DepthTokens.overCamera)) }
}

private fun firstName(name: String) = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

// ---------- Who's using this phone ----------

@Composable
fun WhoScreen(state: GateUi.Who, onPerson: (String) -> Unit, safe: PaddingValues, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        GateBackdrop()
        Column(
            Modifier.fillMaxSize().padding(safe).padding(start = Space.gutter, end = Space.gutter, bottom = 72.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        ) {
            Text("Who's using this phone?", style = InType.screenTitle, color = Palette.text, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
            when {
                state.people == null -> Spinner(Modifier.size(24.dp))
                state.people.isEmpty() -> Text(state.emptyMessage.orEmpty(), style = InType.body, color = Palette.text, textAlign = TextAlign.Center)
                else -> PeopleGrid(state.people.sortedBy { it.lowercase() }, onPerson)
            }
        }
        if (state.phoneName != null) {
            Text(
                "This phone: ${state.phoneName}", style = InType.meta, color = Palette.text2, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(safe).padding(bottom = 8.dp).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PeopleGrid(people: List<String>, onPerson: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        people.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { Person(it, onPerson) }
            }
        }
    }
}

@Composable
private fun Person(name: String, onPerson: (String) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        Modifier.width(112.dp).clip(RoundedCornerShape(16.dp))
            .clickable(source, indication = null, role = Role.Button) { onPerson(name) }
            .semantics(mergeDescendants = true) {}
            .testTag("who.person.$name")
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(88.dp).scale(if (pressed) 0.97f else 1f).raised(pressed, 44.dp).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) { Text(initialsOf(name), style = InType.bigNumber, color = Palette.text) }
        Text(firstName(name), style = InType.tileLabel, color = Palette.text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Spinner(modifier: Modifier = Modifier.size(16.dp)) {
    CircularProgressIndicator(modifier, color = Palette.text2, trackColor = Palette.divider, strokeWidth = 2.dp)
}

// ---------- PIN entry ----------

@Composable
fun PinScreen(
    state: PinUi,
    onKey: (Int) -> Unit,
    onDelete: () -> Unit,
    onSwitchUser: () -> Unit,
    safe: PaddingValues,
    modifier: Modifier = Modifier,
    titleFocus: FocusRequester = remember { FocusRequester() },
) {
    val locked = state.phase == PinPhase.LOCKED
    val first = firstName(state.name)
    BoxWithConstraints(modifier.fillMaxSize().background(Palette.bg).padding(safe)) {
        // Below about 720 dp of height (a 360 x 640 phone) the avatar hides and keys may shrink to 56.
        val short = maxHeight + safe.calculateTopPadding() + safe.calculateBottomPadding() < 720.dp
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, start = Space.gutter, end = Space.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!short) {
                    Box(
                        Modifier.size(72.dp).background(Palette.surfaceRaised, CircleShape).border(1.dp, Palette.divider, CircleShape).clearAndSetSemantics {},
                        contentAlignment = Alignment.Center,
                    ) { Text(initialsOf(state.name), style = InType.appTitle, color = Palette.text) }
                    Spacer(Modifier.height(8.dp))
                }
                Text(first, style = InType.screenTitle, color = Palette.text, textAlign = TextAlign.Center,
                    modifier = Modifier.focusRequester(titleFocus).focusable().semantics { heading() })
                if (!locked) {
                    TextAction("Not $first? Switch user", onSwitchUser, Modifier.offset(y = (-4).dp).testTag("pin.switchUser"))
                    Spacer(Modifier.height(12.dp))
                    Text("Enter your PIN", style = InType.body, color = Palette.text2)
                    if (state.idle) {
                        Text(
                            buildAnnotatedString {
                                appendInlineContent("icon", " ")
                                append("Locked after 30 minutes idle. You'll pick up where you left off.")
                            },
                            style = InType.meta, color = Palette.text2, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp).widthIn(max = 300.dp),
                            inlineContent = mapOf("icon" to InlineTextContent(Placeholder(20.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)) {
                                Icon(Icons.Rounded.LockClock, null, tint = Palette.text2, modifier = Modifier.size(16.dp))
                            }),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    PinBoxes(state)
                    MessageLine(state)
                }
            }
            if (locked) {
                Spacer(Modifier.weight(1f))
                LockedNotice(first, state.lockedUntil, onSwitchUser)
            } else {
                Keypad(enabled = state.phase == PinPhase.ENTRY || state.phase == PinPhase.WRONG, minKey = if (short) 56.dp else 64.dp,
                    onKey = onKey, onDelete = onDelete, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PinBoxes(state: PinUi) {
    val bad = state.phase == PinPhase.WRONG
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.shake) {
        if (state.shake > 0) shake.animateTo(0f, keyframes {
            durationMillis = 240
            -6f at 40; 6f at 80; -6f at 120; 6f at 160; -6f at 200; 0f at 240
        })
    }
    val dotAlpha by animateFloatAsState(if (state.clearing) 0f else 1f, tween(120), label = "dots")
    Row(
        Modifier.offset(x = shake.value.dp)
            .clearAndSetSemantics { contentDescription = "PIN, ${state.digits} of 4 digits entered" }
            .testTag("pin.boxes"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (i in 0 until 4) {
            val ring = when {
                bad -> Roles.danger.ink
                i == state.digits && state.phase == PinPhase.ENTRY -> Roles.accentInk
                else -> null
            }
            Box(
                Modifier.size(60.dp, 68.dp)
                    // The current box: a 2 dp ring outside its 1 dp border (the mockup's box-shadow ring).
                    .drawBehind {
                        if (ring != null) {
                            val w = 2.dp.toPx()
                            drawRoundRect(ring, topLeft = Offset(-w / 2, -w / 2), size = Size(size.width + w, size.height + w),
                                cornerRadius = CornerRadius(12.dp.toPx() + w / 2), style = Stroke(w))
                        }
                    }
                    .well(radius = 12.dp)
                    .then(if (ring != null) Modifier.border(1.dp, ring, RoundedCornerShape(12.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (i < state.digits) Box(Modifier.size(14.dp).alpha(dotAlpha).background(Palette.text, CircleShape))
            }
        }
    }
}

@Composable
private fun MessageLine(state: PinUi) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("pin.message"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            state.phase == PinPhase.CHECKING && state.slowCheck -> Row(verticalAlignment = Alignment.CenterVertically) {
                Spinner()
                Spacer(Modifier.width(6.dp))
                Text("Checking…", style = InType.label, color = Palette.text)
            }
            state.line1 != null -> {
                Text(state.line1, style = InType.label, color = if (state.phase == PinPhase.WRONG) Roles.danger.ink else Palette.text, textAlign = TextAlign.Center)
                if (state.line2 != null) Text(state.line2, style = InType.label, color = Palette.text, textAlign = TextAlign.Center)
            }
        }
        if (state.phase == PinPhase.ENTRY && state.digits > 0) {
            // Hidden polite count for screen readers; the digits themselves are never read back.
            Box(Modifier.size(0.dp).semantics { contentDescription = "${state.digits} of 4 entered" })
        }
    }
}

// 3 x 4 raised keys, 8 dp gaps, rows share the space down to the bottom. No letters; the 4th digit signs in.
@Composable
private fun Keypad(enabled: Boolean, minKey: Dp, onKey: (Int) -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2))
    Column(
        modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = 4.dp, bottom = 8.dp)
            .semantics { contentDescription = "PIN keypad" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f).heightIn(min = minKey), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val m = Modifier.weight(1f).fillMaxHeight()
                    when (k) {
                        -1 -> Spacer(m)
                        -2 -> Key(enabled, onDelete, m.testTag("pin.key.delete"), spoken = "Delete last digit") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Outlined.Backspace, null, tint = Palette.text2, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Delete", style = InType.label, color = if (enabled) Palette.text else Palette.text2)
                            }
                        }
                        else -> Key(enabled, { onKey(k) }, m.testTag("pin.key.$k"), spoken = "$k") {
                            Text("$k", style = InType.key.copy(fontSize = keyDigitSize()), color = if (enabled) Palette.text else Palette.text2)
                        }
                    }
                }
            }
        }
    }
}

// Keypad digit, 44/48; it stops growing at 1.3x so the rows can shrink first.
@Composable
private fun keyDigitSize(): androidx.compose.ui.unit.TextUnit {
    val d = LocalDensity.current
    return with(d) { (44f * min(d.fontScale, 1.3f)).dp.toSp() }
}

@Composable
private fun Key(enabled: Boolean, onClick: () -> Unit, modifier: Modifier, spoken: String, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .scale(if (pressed && enabled) 0.97f else 1f)
            .raised(pressed && enabled, 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.clearAndSetSemantics {}) { content() } }
}

// Locked: a notice card takes the keypad's place in the bottom band, with Switch user.
@Composable
private fun LockedNotice(first: String, until: String?, onSwitchUser: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, bottom = 8.dp)
            .background(Palette.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Palette.borderStrong, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = Roles.danger.ink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (until != null) "$first is locked until $until" else "$first is locked", style = InType.section, color = Palette.text,
                modifier = Modifier.semantics { heading() })
        }
        Text(
            if (until != null) "Too many wrong PINs. Try again then, or ask the owner to reset the PIN."
            else "Too many wrong PINs. Wait 5 minutes, then try again, or ask the owner to reset the PIN.",
            style = InType.body, color = Palette.text,
        )
        Spacer(Modifier.height(4.dp))
        RaisedButton("Switch user", onSwitchUser, Modifier.fillMaxWidth().testTag("pin.locked.switch"))
    }
}

// ---------- Can't reach the shop system ----------

@Composable
fun UnreachableScreen(state: GateUi.Unreachable, onRetry: () -> Unit, onLogBackIn: () -> Unit, safe: PaddingValues, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        GateBackdrop()
        Column(Modifier.fillMaxSize().padding(safe).padding(start = Space.gutter, end = Space.gutter, bottom = 8.dp)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                CenteredMessage(state)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Try again", onRetry, Modifier.fillMaxWidth().testTag("gate.unreachable.retry"))
                if (state.stillSignedIn != null) {
                    RaisedButton("Log back in", onLogBackIn, Modifier.fillMaxWidth().testTag("gate.unreachable.back"))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CenteredMessage(state: GateUi.Unreachable) {
    Box(Modifier.padding(bottom = 4.dp).size(72.dp).background(Roles.warn.tint, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.CloudOff, null, tint = Roles.warn.ink, modifier = Modifier.size(36.dp))
    }
    Text("Can't reach the shop system", style = InType.screenTitle, color = Palette.text, textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() })
    val who = state.signingInAs?.let { "Signing in as ${firstName(it)} needs the shop system." } ?: "Signing in needs the shop system."
    Text("$who Check the shop Wi-Fi, then try again.", style = InType.body, color = Palette.text, textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 320.dp))
    if (state.stillSignedIn != null) {
        val tried = state.lastTried?.let { "Last tried $it. " }.orEmpty()
        Text("$tried${firstName(state.stillSignedIn)} is still signed in on this phone.", style = InType.meta, color = Palette.text2,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 290.dp))
    }
}
