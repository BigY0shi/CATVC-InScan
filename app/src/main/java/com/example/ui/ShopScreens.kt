package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.InventoryListing
import com.example.data.InventoryViewModel
import com.example.data.OutboxEntry
import com.example.shop.LegacyPlan
import com.example.shop.ShopResult
import com.example.shop.ShopSync
import com.example.ui.theme.BoneWhite
import com.example.ui.theme.CinematicBlack
import com.example.ui.theme.CinematicCharcoalLight
import com.example.ui.theme.CountryAmber
import com.example.ui.theme.CountryOrange
import com.example.ui.theme.MutedBone
import com.example.ui.theme.SmoothRed
import kotlinx.coroutines.launch

// Shown until this phone is connected to the shop system and an employee has signed in.
@Composable
fun ShopGate(viewModel: InventoryViewModel, modifier: Modifier = Modifier) {
    val connection by viewModel.connection.collectAsState()
    var reconnecting by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CinematicBlack)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Country ATV InScan", style = MaterialTheme.typography.headlineSmall, color = BoneWhite, fontWeight = FontWeight.Bold)
        if (!connection.enrolled || reconnecting) {
            ConnectForm(viewModel, onConnected = { reconnecting = false })
        } else {
            SignInForm(viewModel, onUseDifferentServer = { reconnecting = true })
        }
    }
}

@Composable
private fun ConnectForm(viewModel: InventoryViewModel, onConnected: () -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("https://") }
    var code by remember { mutableStateOf("") }
    var phoneName by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val addressProblem = if (address.length > "https://".length) viewModel.addressProblem(address) else null

    Text("Connect this phone to the shop system", style = MaterialTheme.typography.titleMedium, color = BoneWhite)
    Text(
        "The owner gets the address and a one-time setup code from the shop server " +
            "(run: inscan-server setup-code). Each code works once.",
        style = MaterialTheme.typography.bodyMedium, color = MutedBone
    )
    OutlinedTextField(
        value = address, onValueChange = { address = it; error = null },
        label = { Text("Shop system address") }, singleLine = true,
        isError = addressProblem != null,
        supportingText = { addressProblem?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().testTag("connect_address")
    )
    OutlinedTextField(
        value = code, onValueChange = { code = it; error = null },
        label = { Text("Setup code") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("connect_code")
    )
    OutlinedTextField(
        value = phoneName, onValueChange = { phoneName = it; error = null },
        label = { Text("Name for this phone (e.g. Front counter)") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("connect_phone_name")
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("connect_error")) }
    Button(
        onClick = {
            working = true
            scope.launch {
                error = viewModel.connect(address, code, phoneName)
                working = false
                if (error == null) onConnected()
            }
        },
        enabled = !working && addressProblem == null && code.isNotBlank() && phoneName.isNotBlank(),
        modifier = Modifier.fillMaxWidth().testTag("connect_button")
    ) {
        if (working) CircularProgressIndicator(modifier = Modifier.height(18.dp)) else Text("Connect")
    }
}

@Composable
private fun SignInForm(viewModel: InventoryViewModel, onUseDifferentServer: () -> Unit) {
    val scope = rememberCoroutineScope()
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadProblem by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        loading = true
        when (val r = viewModel.employeeNames()) {
            is ShopResult.Ok -> { names = r.value; loadProblem = null }
            is ShopResult.Refused -> loadProblem = r.message
            is ShopResult.NotSignedIn -> loadProblem = r.message
            is ShopResult.Unreachable -> loadProblem = r.message
        }
        loading = false
    }

    Text("Sign in", style = MaterialTheme.typography.titleMedium, color = BoneWhite)
    when {
        loading -> CircularProgressIndicator()
        loadProblem != null -> {
            Text(loadProblem!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("signin_load_problem"))
            OutlinedButton(onClick = { reloadKey++ }) { Text("Try again") }
        }
        names.isEmpty() -> Text(
            "No employees are set up yet. The owner adds them on the shop server (run: inscan-server add-employee NAME).",
            color = MutedBone
        )
        else -> {
            Text("Who's using this phone?", color = MutedBone)
            names.forEach { name ->
                FilterChip(
                    selected = chosen == name, onClick = { chosen = name; error = null },
                    label = { Text(name) }, modifier = Modifier.testTag("signin_name_$name")
                )
            }
            OutlinedTextField(
                value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it; error = null },
                label = { Text("PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().testTag("signin_pin")
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("signin_error")) }
            Button(
                onClick = {
                    val name = chosen ?: return@Button
                    working = true
                    scope.launch {
                        error = viewModel.signIn(name, pin)
                        pin = ""
                        working = false
                    }
                },
                enabled = !working && chosen != null && pin.length >= 4,
                modifier = Modifier.fillMaxWidth().testTag("signin_button")
            ) { Text("Sign in") }
        }
    }
    TextButton(onClick = onUseDifferentServer) { Text("Connect to a different shop system", color = CountryAmber) }
}

// One line per thing the employee should know about the shop system. Nothing shown when all is well.
@Composable
fun ShopStatusBanner(viewModel: InventoryViewModel, onOpenAttention: () -> Unit, onOpenUpload: () -> Unit) {
    val outbox by viewModel.outbox.collectAsState()
    val problem by viewModel.syncProblem.collectAsState()
    val legacy by viewModel.legacyRows.collectAsState()
    val waiting = outbox.count { it.state == OutboxEntry.STATE_PENDING }
    val attention = outbox.count { it.state == OutboxEntry.STATE_NEEDS_ATTENTION }
    val notUploaded = legacy.count { it.queuedAs == null }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (problem != null) {
            BannerRow(
                text = if (waiting > 0) "Shop system offline — $waiting ${if (waiting == 1) "change" else "changes"} waiting on this phone. $problem"
                else "Shop system offline. $problem",
                color = CountryAmber, action = "Try now", onClick = { viewModel.syncSoon() }, tag = "banner_offline"
            )
        } else if (waiting > 0) {
            BannerRow("Sending $waiting ${if (waiting == 1) "change" else "changes"} to the shop system…", MutedBone, null, null, "banner_sending")
        }
        if (attention > 0) {
            BannerRow(
                "$attention ${if (attention == 1) "change needs" else "changes need"} your attention — the shop system didn't accept ${if (attention == 1) "it" else "them"}.",
                SmoothRed, "Review", onOpenAttention, "banner_attention"
            )
        }
        if (notUploaded > 0) {
            BannerRow(
                "$notUploaded ${if (notUploaded == 1) "item" else "items"} from before the shop system ${if (notUploaded == 1) "is" else "are"} only on this phone.",
                CountryOrange, "Upload", onOpenUpload, "banner_upload"
            )
        }
    }
}

@Composable
private fun BannerRow(text: String, color: androidx.compose.ui.graphics.Color, action: String?, onClick: (() -> Unit)?, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.18f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text, color = BoneWhite, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun NeedsAttentionDialog(viewModel: InventoryViewModel, onDismiss: () -> Unit) {
    val outbox by viewModel.outbox.collectAsState()
    val refused = outbox.filter { it.state == OutboxEntry.STATE_NEEDS_ATTENTION }
    var confirmDiscard by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Changes the shop system didn't accept") },
        text = {
            if (refused.isEmpty()) {
                Text("Nothing needs attention.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(refused, key = { it.seq }) { entry ->
                        Column(modifier = Modifier.testTag("attention_${entry.seq}")) {
                            Text("${describeKind(entry)}: ${viewModel.itemName(entry.itemId)}", fontWeight = FontWeight.Bold)
                            Text(entry.problemMessage.orEmpty())
                            entry.problemDetails?.let { Text(it, color = MutedBone, style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { viewModel.retryChange(entry) }) { Text("Try again") }
                                if (entry.problemCode == "VIN_NOT_CONFIRMED") {
                                    TextButton(onClick = { viewModel.retryWithVinMatchingFrame(entry) }) { Text("VIN matches the frame — send") }
                                }
                            }
                            if (confirmDiscard == entry.seq) {
                                Text(
                                    if (entry.kind == ShopSync.KIND_CREATE) "Throw this item away? It never reached the shop system, so it will be gone from this phone."
                                    else "Throw this change away? The shop system keeps its own details.",
                                    color = SmoothRed
                                )
                                Row {
                                    TextButton(onClick = { viewModel.discardChange(entry); confirmDiscard = null }) { Text("Yes, throw it away", color = SmoothRed) }
                                    TextButton(onClick = { confirmDiscard = null }) { Text("Keep it") }
                                }
                            } else {
                                TextButton(onClick = { confirmDiscard = entry.seq }) { Text("Throw this change away", color = SmoothRed) }
                            }
                            HorizontalDivider(color = CinematicCharcoalLight)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun describeKind(entry: OutboxEntry) = when (entry.kind) {
    ShopSync.KIND_CREATE -> "New item"
    ShopSync.KIND_UPDATE -> "Changed details"
    ShopSync.KIND_STATUS -> "Status change"
    ShopSync.KIND_TAG_PRINT -> "Printed label"
    ShopSync.KIND_TAG_CONFIRM -> "Label check"
    else -> entry.kind
}

@Composable
fun UploadFromPhoneDialog(viewModel: InventoryViewModel, onFixInForm: (InventoryListing) -> Unit, onDismiss: () -> Unit) {
    val rows by viewModel.legacyRows.collectAsState()
    val outbox by viewModel.outbox.collectAsState()
    val ready = rows.count { it.queuedAs == null && it.plan is LegacyPlan.Ready }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload items from this phone") },
        text = {
            Column {
                Text(
                    "These were logged on this phone before the shop system. Uploading copies them into the shop system; " +
                        "nothing is deleted from this phone. Items with gaps stay here until they're completed.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rows, key = { it.old.id }) { row ->
                        val old = row.old
                        Column(modifier = Modifier.testTag("legacy_${old.id}")) {
                            Text(listOf(old.year, old.make, old.model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Item ${old.barcode}" }, fontWeight = FontWeight.Bold)
                            val queued = row.queuedAs
                            when {
                                queued != null -> {
                                    val entries = outbox.filter { it.itemId == queued }
                                    Text(
                                        when {
                                            entries.any { it.state == OutboxEntry.STATE_NEEDS_ATTENTION } -> "Needs attention — see the red banner."
                                            entries.isNotEmpty() -> "Waiting to send."
                                            else -> "In the shop system."
                                        },
                                        color = MutedBone, style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                row.plan is LegacyPlan.Ready -> Text("Ready to upload.", color = MutedBone, style = MaterialTheme.typography.bodySmall)
                                row.plan is LegacyPlan.CantUpload -> {
                                    Text("Can't upload yet: ${row.plan.reasons.joinToString("; ")}.", color = CountryAmber, style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { onFixInForm(old) }) { Text("Complete it in the form") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.uploadLegacyItems() }, enabled = ready > 0, modifier = Modifier.testTag("legacy_upload_button")) {
                Text(if (ready > 0) "Upload $ready ${if (ready == 1) "item" else "items"}" else "Nothing ready")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// What "Delete" means for an item depends on whether it ever reached the shop system.
@Composable
fun RemoveListingDialog(viewModel: InventoryViewModel, listing: InventoryListing, onDone: (String?) -> Unit) {
    val name = "${listing.year} ${listing.make} ${listing.model}".trim()
    when (viewModel.removalFor(listing)) {
        InventoryViewModel.Removal.NOT_SENT_YET -> AlertDialog(
            onDismissRequest = { onDone(null) },
            title = { Text("Remove $name?") },
            text = { Text("It hasn't reached the shop system yet, so it will be gone from this phone.") },
            confirmButton = { TextButton(onClick = { viewModel.removeUnsentListing(listing); onDone("Removed $name.") }) { Text("Remove", color = SmoothRed) } },
            dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Keep it") } }
        )
        InventoryViewModel.Removal.NEEDS_OWNER -> AlertDialog(
            onDismissRequest = { onDone(null) },
            title = { Text("Only the owner can remove this") },
            text = { Text("$name is in the shop system. Ask the owner to retire it; they'll need to give a reason.") },
            confirmButton = { TextButton(onClick = { onDone(null) }) { Text("OK") } }
        )
        InventoryViewModel.Removal.OWNER_RETIRES -> {
            var reason by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { onDone(null) },
                title = { Text("Retire $name?") },
                text = {
                    Column {
                        Text("It leaves the listing file but stays in the shop's records as Retired.")
                        OutlinedTextField(
                            value = reason, onValueChange = { reason = it }, label = { Text("Reason (required)") },
                            modifier = Modifier.fillMaxWidth().testTag("retire_reason")
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.retireListing(listing, reason); onDone("Retired $name.") }, enabled = reason.isNotBlank()) {
                        Text("Retire", color = SmoothRed)
                    }
                },
                dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } }
            )
        }
    }
}
