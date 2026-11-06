package com.example.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.InventoryViewModel
import com.example.ui.CameraPreviewScanner
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// The Scan overlay with its state: camera permission, the reader, typed lookups, Bluetooth
// scanners, and Move to In stock. Opened full screen over the shell; Cancel returns to where it was
// opened. [expectId] is set when opened from check-in's Print, to check the tag just printed.
@Composable
fun ScanRoute(
    vm: InventoryViewModel,
    signedInAs: String,
    onClose: () -> Unit,
    onOpenUnit: (String) -> Unit,
    onCheckIn: (vin: String?) -> Unit,
    onReprint: (String) -> Unit,
    expectId: String? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val prefs = remember { context.getSharedPreferences("scan_screen", Context.MODE_PRIVATE) }

    var ui by remember { mutableStateOf(ScanUi(firstName = signedInAs.trim().split(Regex("\\s+")).first())) }
    var lastRead by remember { mutableStateOf<String?>(null) }
    var cameraRetry by remember { mutableIntStateOf(0) }
    var torchWanted by remember { mutableStateOf(false) }
    var confirmAnyway by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // ---- Camera permission (2f) ----
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    fun permissionIssue(): CameraIssue? {
        if (granted()) return null
        val asked = prefs.getBoolean(KEY_ASKED, false)
        val rationale = context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
        // Open settings when the system won't ask again: denied, no rationale, and we've asked before.
        return if (asked && !rationale) CameraIssue.NOT_ALLOWED_SETTINGS else CameraIssue.NOT_ALLOWED
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        ui = ui.copy(camera = permissionIssue())
    }
    fun askNow() { prefs.edit().putBoolean(KEY_ASKED, true).apply(); ask.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(Unit) {
        ui = ui.copy(camera = permissionIssue())
        if (!granted() && !prefs.getBoolean(KEY_ASKED, false)) askNow()
        view.announceForAccessibility("Scanning. Point the camera at a tag.")
    }
    // Re-check on every resume: an "Only this time" grant runs out.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && ui.camera != CameraIssue.COULDNT_START) ui = ui.copy(camera = permissionIssue())
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // ---- No read after 8 s: the instruction changes. Resets on the next read. ----
    LaunchedEffect(ui.mode, ui.camera, ui.card == null) {
        if (ui.mode == ScanMode.CAMERA && ui.camera == null && ui.card == null) {
            ui = ui.copy(hint = false)
            delay(8_000)
            ui = ui.copy(hint = true)
        }
    }

    // ---- Reads ----
    fun look(code: String, read: ReadKind) {
        if (busy || ui.card != null) return
        busy = true
        lastRead = code
        scope.launch {
            val lookup = vm.lookUpForScan(code)
            ui = ui.copy(card = scanCard(lookup, code, read, LocalDate.now(zone), zone, expectId), hint = false, typedWrongLength = null)
            busy = false
        }
    }
    fun cameraRead(raw: String) {
        if (ui.card != null || busy) return // one read at a time; a read while the card is up is ignored
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        look(raw.trim(), readKindOf(raw, typed = false))
    }
    fun typedLookUp() {
        val problem = typedLengthProblem(ui.typed)
        if (problem != null) { ui = ui.copy(typedWrongLength = problem); return }
        val code = cleanTyped(ui.typed)
        // A whole tag typed by a Bluetooth scanner is treated as a read.
        if (code.startsWith("CATV1;")) look(code, ReadKind.TAG) else look(code, ReadKind.TYPED)
    }

    fun resume(mode: ScanMode) {
        ui = ui.copy(card = null, mode = mode, moving = false, typed = if (mode == ScanMode.TYPE) "" else ui.typed, typedWrongLength = null)
    }

    fun onCard(a: CardAction) {
        val card = ui.card ?: return
        when (a) {
            CardAction.SCAN_AGAIN, CardAction.SCAN_NEXT, CardAction.SCAN_QR -> resume(ScanMode.CAMERA)
            CardAction.TYPE_AGAIN -> resume(ScanMode.TYPE)
            CardAction.MOVE -> {
                val id = card.unitId ?: return
                val payload = card.tagPayload ?: return
                if (ui.moving) return
                ui = ui.copy(moving = true)
                scope.launch {
                    val result = vm.moveToInStock(id, payload)
                    ui = ui.copy(card = movedCard(result, LocalDate.now(zone), zone), moving = false)
                }
            }
            CardAction.REPRINT -> card.unitId?.let(onReprint)
            CardAction.CHECK_IN -> onCheckIn(card.vin)
            CardAction.CHECK_IN_ANYWAY -> confirmAnyway = true
            CardAction.SIGN_IN -> vm.signOut()
        }
    }

    // Back, per state (2d): card = Scan another; Type it = back to Camera; otherwise = Cancel.
    BackHandler {
        when {
            ui.card != null -> onCard(if (ui.card?.read == ReadKind.TYPED) CardAction.TYPE_AGAIN else CardAction.SCAN_AGAIN)
            ui.mode == ScanMode.TYPE -> resume(ScanMode.CAMERA)
            else -> onClose()
        }
    }

    if (confirmAnyway) {
        AlertDialog(
            onDismissRequest = { confirmAnyway = false },
            title = { Text("Check it in anyway?", style = InType.section, color = Palette.text) },
            text = { Text("If another phone already checked this unit in, this makes a second copy of it.", style = InType.body, color = Palette.text) },
            confirmButton = { TextButton({ confirmAnyway = false; onCheckIn(ui.card?.vin) }) { Text("Check in anyway", style = InType.label) } },
            dismissButton = { TextButton({ confirmAnyway = false }) { Text("Go back", style = InType.label) } },
        )
    }

    // Bluetooth scanners type the code and press Enter, faster than a person types. Outside Type it,
    // a burst like that counts as a read (in Type it, the field's Search key runs the lookup).
    val wedge = remember { StringBuilder() }
    var wedgeAt by remember { mutableStateOf(0L) }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(ui.mode) { if (ui.mode == ScanMode.CAMERA) runCatching { rootFocus.requestFocus() } }

    ScanScreen(
        state = ui,
        safe = WindowInsets.safeDrawing.asPaddingValues(),
        preview = {
            key(cameraRetry) {
                CameraPreviewScanner(
                    onBarcodeDetected = ::cameraRead,
                    onFailure = { ui = ui.copy(camera = CameraIssue.COULDNT_START) },
                    paused = ui.card != null || busy,
                    ignoreUntilGone = lastRead.takeIf { ui.card == null },
                    onePerFrame = true,
                    torchOn = torchWanted,
                    onTorch = { available, on ->
                        ui = ui.copy(torch = if (!available) Torch.NONE else if (on) Torch.ON else Torch.OFF)
                        torchWanted = on
                    },
                )
            }
        },
        onMode = { m -> if (m != ui.mode) resume(m) },
        onCancel = onClose,
        onLight = { torchWanted = ui.torch != Torch.ON },
        onTyped = { ui = ui.copy(typed = it, typedWrongLength = null) },
        onLookUp = ::typedLookUp,
        onNoTag = { onCheckIn(null) },
        onPermission = {
            when (ui.camera) {
                CameraIssue.NOT_ALLOWED -> askNow()
                CameraIssue.NOT_ALLOWED_SETTINGS -> context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                CameraIssue.COULDNT_START -> { cameraRetry++; ui = ui.copy(camera = permissionIssue()) }
                null -> Unit
            }
        },
        onCard = ::onCard,
        onOpenUnit = onOpenUnit,
        modifier = Modifier.focusRequester(rootFocus).focusable().onPreviewKeyEvent { e ->
            if (ui.mode == ScanMode.TYPE || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val now = SystemClock.uptimeMillis()
            if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                val code = wedge.toString()
                wedge.clear()
                if (code.length >= 8 && now - wedgeAt < 400) { cameraRead(code); return@onPreviewKeyEvent true }
                return@onPreviewKeyEvent false
            }
            val ch = e.utf16CodePoint
            if (ch <= 0x20) return@onPreviewKeyEvent false
            if (now - wedgeAt > 80) wedge.clear()
            wedge.append(ch.toChar())
            wedgeAt = now
            true
        },
    )
}

private const val KEY_ASKED = "camera_asked"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
