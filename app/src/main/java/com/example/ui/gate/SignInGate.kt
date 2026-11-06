package com.example.ui.gate

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import com.example.data.InventoryViewModel
import com.example.shop.ShopResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Holds the gate's state for an enrolled phone with nobody signed in, and talks to the shop
// system through the view model. Who → PIN → (locked | unreachable). Back on PIN returns to who;
// back on who leaves the app (the gate is its own root).
@Composable
fun SignInGate(vm: InventoryViewModel, modifier: Modifier = Modifier) {
    val connection by vm.connection.collectAsState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val safe = WindowInsets.safeDrawing.asPaddingValues()

    var people by remember { mutableStateOf<List<String>?>(null) }
    var listProblem by remember { mutableStateOf(false) }
    // The shop system's own reason when it gave no list of people.
    var whyEmpty by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var pin by remember { mutableStateOf<PinUi?>(null) }
    var unreachable by remember { mutableStateOf<GateUi.Unreachable?>(null) }
    // Held in memory only while "Can't reach the shop system" offers Try again; cleared after.
    var pendingPin by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf("") }
    val keyTimes = remember { ArrayDeque<Long>() }

    LaunchedEffect(reload) {
        people = null
        when (val r = vm.employeeNames()) {
            is ShopResult.Ok -> { people = r.value; listProblem = false; unreachable = null }
            is ShopResult.Unreachable -> { listProblem = true; unreachable = GateUi.Unreachable(null, now(), null) }
            // Refused or not enrolled: say so on the who screen in the shop system's own words.
            is ShopResult.Refused -> { people = emptyList(); listProblem = true; unreachable = null; whyEmpty = r.message }
            is ShopResult.NotSignedIn -> { people = emptyList(); listProblem = true; unreachable = null; whyEmpty = r.message }
        }
    }

    fun submit(name: String, code: String) {
        pin = pin?.copy(phase = PinPhase.CHECKING, line1 = null, line2 = null, slowCheck = false)
        scope.launch {
            val slow = launch { delay(300); pin = pin?.copy(slowCheck = true) }
            val r = vm.trySignIn(name, code)
            slow.cancel()
            when (r) {
                is ShopResult.Ok -> { pendingPin = null; entered = "" } // the shell takes over
                is ShopResult.Unreachable -> {
                    pendingPin = code
                    unreachable = GateUi.Unreachable(name, now(), null)
                }
                is ShopResult.NotSignedIn -> {
                    entered = ""
                    pin = pin?.copy(phase = PinPhase.ENTRY, digits = 0, line1 = r.message)
                }
                is ShopResult.Refused -> if (r.code == "PIN_LOCKED") {
                    entered = ""
                    pin = pin?.copy(phase = PinPhase.LOCKED, digits = 0, line1 = null, lockedUntil = null)
                } else {
                    // Wrong PIN: red rings, a short shake, a reject haptic; dots fade, boxes clear at 600 ms.
                    // The shop system doesn't say how many tries are left yet, so no second line.
                    entered = ""
                    val wrong = (pin?.shake ?: 0) + 1
                    pin = pin?.copy(phase = PinPhase.WRONG, digits = 4, line1 = if (r.code == "WRONG_PIN") "That PIN didn't match. Try again." else r.message,
                        line2 = null, shake = wrong)
                    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
                    delay(480)
                    if (pin?.shake == wrong && pin?.phase == PinPhase.WRONG) pin = pin?.copy(clearing = true)
                    delay(120)
                    if (pin?.shake == wrong && pin?.phase == PinPhase.WRONG) pin = pin?.copy(phase = PinPhase.ENTRY, digits = 0, clearing = false)
                }
            }
        }
    }

    fun key(d: Int) {
        val p = pin ?: return
        if (p.phase != PinPhase.ENTRY && p.phase != PinPhase.WRONG) return
        if (entered.length >= 4) return
        entered += d
        pin = p.copy(phase = PinPhase.ENTRY, digits = entered.length, line1 = null, line2 = null, clearing = false)
        // The 4th digit signs in, exactly once: the keypad is disabled until the answer comes back.
        if (entered.length == 4) submit(p.name, entered)
    }

    fun delete() {
        val p = pin ?: return
        if (p.phase != PinPhase.ENTRY && p.phase != PinPhase.WRONG) return
        entered = entered.dropLast(1)
        pin = p.copy(phase = PinPhase.ENTRY, digits = entered.length, clearing = false)
    }

    fun switchUser() { pin = null; entered = ""; pendingPin = null; unreachable = null }

    BackHandler(enabled = pin != null || (unreachable != null && !listProblem)) { switchUser() }

    val u = unreachable
    val p = pin
    when {
        u != null -> UnreachableScreen(
            u,
            onRetry = {
                val code = pendingPin
                val name = u.signingInAs
                if (name != null && code != null) {
                    unreachable = null
                    pin = PinUi(name, digits = 4)
                    entered = code
                    submit(name, code)
                } else {
                    reload++
                }
            },
            onLogBackIn = { u.stillSignedIn?.let { pin = PinUi(it, idle = true); unreachable = null } },
            safe = safe, modifier = modifier,
        )
        p != null -> {
            // Focus moves to the new title (who → PIN → locked); key events reach the screen from there.
            val titleFocus = remember { FocusRequester() }
            SecureWhileShown()
            LaunchedEffect(p.name, p.phase == PinPhase.LOCKED) { titleFocus.requestFocus() }
            PinScreen(
                p, onKey = ::key, onDelete = ::delete, onSwitchUser = ::switchUser, safe = safe, titleFocus = titleFocus,
                modifier = modifier.fillMaxSize().onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = digitOf(e.key)
                    if (digit == null && e.key != Key.Backspace) return@onPreviewKeyEvent false
                    // A burst of more than 4 keys in under 100 ms is a Bluetooth scanner, not a person:
                    // drop it so a scan can't burn PIN tries.
                    val t = SystemClock.uptimeMillis()
                    keyTimes.addLast(t)
                    while (keyTimes.isNotEmpty() && t - keyTimes.first() > 100) keyTimes.removeFirst()
                    if (keyTimes.size > 4) {
                        if (p.phase == PinPhase.ENTRY) { entered = ""; pin = p.copy(digits = 0) }
                        return@onPreviewKeyEvent true
                    }
                    if (digit != null) key(digit) else delete()
                    true
                },
            )
        }
        else -> WhoScreen(
            GateUi.Who(people, connection.phoneName, emptyMessage = whyEmpty ?: "No one is set up to sign in yet. The owner adds people on the shop system."),
            onPerson = { pin = PinUi(it); entered = "" },
            safe = safe, modifier = modifier,
        )
    }
}

private fun now(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)).lowercase()

private fun digitOf(k: Key): Int? = when (k) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

// No screenshots, recordings or casts of key presses while the PIN screen shows, and no recent-apps
// thumbnail of what's behind it (3d).
@Composable
private fun SecureWhileShown() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) activity?.setRecentsScreenshotEnabled(false)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= 33) activity?.setRecentsScreenshotEnabled(true)
        }
    }
}
