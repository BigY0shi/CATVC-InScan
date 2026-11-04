package com.example.ui.checkin

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import com.countryatv.core.LabelSpec
import com.countryatv.core.LabelTooSmall
import com.countryatv.core.UnitStatus
import com.example.data.InventoryViewModel
import com.example.data.ShopUnitEntity
import com.example.label.LabelRenderer
import com.example.label.LabelShare
import com.example.ui.CameraPreviewScanner
import com.example.ui.LabelSizeDialog
import com.example.ui.home.recordCard
import com.example.ui.inscan.DepthTokens
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.example.ui.inscan.PrimaryButton
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.Space
import com.example.ui.inscan.TextAction
import com.example.ui.inscan.statusOf
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// The PT-P910BT on 36 mm tape: a 70 mm tag with a 32 mm print area, at 360 dpi.
val PT_36MM_TAPE = LabelSpec(70.0, 32.0, 360)

private fun mm(v: Double) = if (v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)

fun tagCaption(spec: LabelSpec): String =
    if (spec == PT_36MM_TAPE) "Actual proportions: 70 × 32 mm print area on 36 mm tape."
    else "Actual proportions: ${mm(spec.widthMm)} × ${mm(spec.heightMm)} mm label."

fun labelSizeLine(spec: LabelSpec): String =
    if (spec == PT_36MM_TAPE) "70 mm long, this phone's setting"
    else "${mm(spec.widthMm)} × ${mm(spec.heightMm)} mm, this phone's setting"

// What the tag preview shows for a saved unit: the real render, and whether the VIN row fit.
fun tagPreview(content: LabelContent, spec: LabelSpec, copies: Int): TagPreview {
    val box = if (copies > 1) "Box 1 of $copies" else null
    val rendered = LabelRenderer.render(content, spec, box)
    val short = ItemId.shortForm(content.id)
    val rows = LabelRenderer.printedRows(content, spec, box)
    val words = listOf(listOf(content.year, content.make, content.model, content.subModel).filter { it.isNotBlank() }.joinToString(" "), content.color, content.unitType)
        .filter { it.isNotBlank() }
    val spoken = "Tag preview: " + (words + listOfNotNull(content.vin.takeIf { it.isNotBlank() }?.let { "V I N ${it.toList().joinToString(" ")}" }) +
        "I D ${short.toList().joinToString(" ")}").joinToString(", ")
    return TagPreview(
        image = rendered.getOrNull()?.asImageBitmap(),
        problem = rendered.exceptionOrNull()?.let { (it as? LabelTooSmall)?.problem?.message ?: "This tag couldn't be drawn at this label size. Set another label size." },
        caption = tagCaption(spec), sizeLine = labelSizeLine(spec),
        vinFits = content.vin.isBlank() || rows == null || rows.any { it.startsWith("VIN ") },
        shortId = short, spoken = spoken,
    )
}

fun newDraft(prefillVin: String?): CheckInDraft =
    CheckInDraft(unitId = ItemId.new()).let { if (prefillVin.isNullOrBlank()) it else it.withScannedVin(prefillVin) }

private fun ShopUnitEntity.isGone() = statusOf(status).let { it == UnitStatus.SOLD || it == UnitStatus.RETIRED }

// Check in a unit, with its state and side effects: the VIN lookup and duplicate check, saving
// (Incoming, queued to send, offline-first), and sending the tag to the printer's own app.
@Composable
fun CheckInFlow(
    vm: InventoryViewModel,
    signedInAs: String,
    prefillVin: String?,
    onClose: () -> Unit,
    onOpenUnit: (String) -> Unit,
    onScanTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf(newDraft(prefillVin)) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var sizing by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val units by vm.units.collectAsState()
    val spec by vm.labelSpec.collectAsState()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The duplicate check, then the VIN lookup: once per VIN, and a result for an old VIN is dropped.
    val checkVin = checkableVin(draft).takeIf { draft.vinKind == VinKind.VIN && draft.vinShown && !draft.saved }
    LaunchedEffect(checkVin) {
        val vin = checkVin ?: return@LaunchedEffect
        val existing = vm.unitWithVin(vin)?.takeIf { it.id != draft.unitId }
        if (existing != null) {
            if (checkableVin(draft) == vin) draft = draft.copy(existing = ExistingUnit(existing.id, existing.isGone()))
            return@LaunchedEffect
        }
        val cached = draft.lookup
        if (cached != null && cached.vin == vin && cached.outcome != LookupOutcome.RUNNING) return@LaunchedEffect
        draft = draft.copy(lookup = LookupResult(vin, LookupOutcome.RUNNING))
        val result = when (val r = vm.lookUpVin(vin)) {
            is InventoryViewModel.VinLookup.Found -> LookupResult(vin, LookupOutcome.FOUND, r.make, r.model, r.year, typeFromBodyClass(r.bodyClass))
            InventoryViewModel.VinLookup.Nothing -> LookupResult(vin, LookupOutcome.NOTHING)
            InventoryViewModel.VinLookup.Offline -> LookupResult(vin, LookupOutcome.OFFLINE)
            InventoryViewModel.VinLookup.Failed -> LookupResult(vin, LookupOutcome.FAILED)
        }
        draft = draft.withLookup(result)
    }

    val unit = units.firstOrNull { it.id == draft.unitId }.takeIf { draft.saved }
    val content = unit?.let { vm.labelContentFor(it) }
    val tag = remember(content, spec, draft.copies) { content?.let { tagPreview(it, spec, draft.copies) } }

    // Records the tag once per send. A second send from "Print again" is recorded as a reprint.
    fun record() {
        val u = unit ?: return
        if (draft.recorded) return
        vm.recordLabelShared(u, if (draft.again) "Printed again at check-in" else "")
        draft = draft.copy(recorded = true)
    }

    // A share records only once the person picks an app in the chooser (a cancelled share records nothing).
    val action = "${context.packageName}.TAG_SHARE_PICKED"
    val onPicked by rememberUpdatedState { id: String -> if (id == draft.unitId) record() }
    DisposableEffect(action) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { i.getStringExtra("itemId")?.let { onPicked(it) } }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun send() {
        val u = unit ?: return
        val c = content ?: return
        val images = (1..draft.copies).map { i -> LabelRenderer.render(c, spec, if (draft.copies > 1) "Box $i of ${draft.copies}" else null).getOrNull() }
        if (images.any { it == null }) { problem = "This tag doesn't fit the label size set for this phone. Set a larger label size."; return }
        // Printing again starts a new record.
        if (draft.phase == PrintPhase.SEND && draft.recorded) draft = draft.copy(recorded = false, again = true)
        val picked = PendingIntent.getBroadcast(
            context, u.id.hashCode(), Intent(action).setPackage(context.packageName).putExtra("itemId", u.id),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )
        runCatching { LabelShare.intentFor(context, images.map { it!! }, u.id, picked.intentSender) }
            .onSuccess { context.startActivity(it); draft = draft.copy(phase = PrintPhase.ASK_DID_IT_PRINT, sheet = null) }
            .onFailure { problem = "Couldn't save the tag image to share it. Free up some space on the phone and try again." }
    }

    fun save() {
        if (draft.saving || draft.saved) return
        // Every step is checked again; the first one with a problem opens, marked.
        val bad = listOf(CheckInStep.VIN, CheckInStep.TYPE, CheckInStep.MAKE, CheckInStep.YEAR, CheckInStep.PRICE)
            .firstOrNull { nextProblem(draft.copy(step = it)) != null }
        if (bad != null) { draft = draft.copy(step = bad, fromReview = true).next(); return }
        draft = draft.copy(saving = true)
        scope.launch {
            val r = runCatching { vm.checkIn(toNewUnit(draft)) }
            r.onFailure {
                android.util.Log.e("CheckInFlow", "Saving the check-in failed", it)
                draft = draft.copy(saving = false)
                problem = "Couldn't save this unit on the phone. Nothing was saved. Try again; if it keeps failing, restart the app."
            }
            when (val s = r.getOrNull()) {
                is InventoryViewModel.CheckInSave.Saved -> draft = draft.copy(saving = false, saved = true, step = CheckInStep.PRINT,
                    phase = PrintPhase.SEND, fromReview = false, sheet = null)
                is InventoryViewModel.CheckInSave.Duplicate -> draft = draft.copy(saving = false, step = CheckInStep.VIN, fromReview = false,
                    vinShown = true, existing = ExistingUnit(s.unit.id, s.unit.isGone()))
                null -> Unit
            }
        }
    }

    val ui = CheckInUi(
        draft = draft, signedInAs = signedInAs, today = today,
        makeChips = remember(units) { mostUsed(units.map { it.make }) },
        colourChips = remember(units) { mostUsed(units.map { it.color.trim().replaceFirstChar { c -> c.titlecase() } }) },
        existingCard = draft.existing?.let { e -> units.firstOrNull { it.id == e.unitId }?.let { recordCard(it, today, zone) } },
        tag = tag,
    )
    val actions = CheckInActions(
        onDraft = { draft = it },
        onScanVin = { scanning = true },
        onOpenExisting = onOpenUnit,
        onSave = ::save,
        onSend = ::send,
        onYesPrinted = { record(); draft = draft.copy(phase = PrintPhase.PRINTED, recorded = true) },
        onLabelSize = { sizing = true },
        onScanTag = { onScanTag(draft.unitId) },
        onLeave = { if (draft.saved) onOpenUnit(draft.unitId) else onClose() },
        onAnother = { draft = CheckInDraft(unitId = ItemId.new()) },
    )

    Box(modifier.fillMaxSize()) {
        CheckInScreen(ui, actions, statusBarSpace = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
        if (scanning) VinScanner(
            onRead = { raw -> scanning = false; draft = draft.withScannedVin(raw) },
            onCancel = { scanning = false },
        )
    }
    if (sizing) LabelSizeDialog(spec, onSave = { vm.saveLabelSpec(it); sizing = false }, onDismiss = { sizing = false })
    problem?.let { msg ->
        AlertDialog(onDismissRequest = { problem = null }, confirmButton = { TextAction("OK", { problem = null }) },
            title = { Text("Something went wrong", style = InType.appTitle) }, text = { Text(msg, style = InType.body) })
    }
}

// Scan, in VIN mode: the camera reads the VIN barcode and comes back with it. Our own QR tags are
// skipped (they aren't a VIN). The camera runs only while this is open.
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun VinScanner(onRead: (String) -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    val camera = rememberPermissionState(Manifest.permission.CAMERA)
    var asked by rememberSaveable { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Palette.bg).testTag("checkin.vinScan")) {
        if (camera.status.isGranted && !failed) {
            CameraPreviewScanner(
                onBarcodeDetected = { raw -> if (LabelPayload.decode(raw) == null && raw.isNotBlank()) onRead(raw) },
                onFailure = { failed = true },
            )
        }
        Column(Modifier.fillMaxWidth().background(DepthTokens.overCamera).windowInsetsPadding(WindowInsets.statusBars).padding(Space.gutter)) {
            Text("Point the camera at the VIN barcode", style = InType.section, color = Palette.text)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(DepthTokens.overCamera).navigationBarsPadding().padding(Space.gutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                failed -> Text("The camera didn't start. Close other apps using it and try again, or type the VIN.", style = InType.body, color = Palette.text)
                !camera.status.isGranted -> {
                    Text("InScan needs the camera to read the VIN barcode. It's only on while this screen is open.", style = InType.body, color = Palette.text)
                    if (asked && !camera.status.shouldShowRationale) {
                        PrimaryButton("Open settings", {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }, Modifier.fillMaxWidth())
                    } else {
                        PrimaryButton("Allow camera", { asked = true; camera.launchPermissionRequest() }, Modifier.fillMaxWidth().testTag("checkin.vinScan.allow"))
                    }
                }
            }
            RaisedButton(if (camera.status.isGranted && !failed) "Cancel" else "Type it instead", onCancel, Modifier.fillMaxWidth().testTag("checkin.vinScan.cancel"))
        }
    }
}
