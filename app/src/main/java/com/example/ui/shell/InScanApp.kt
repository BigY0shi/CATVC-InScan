package com.example.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.countryatv.core.UnitStatus
import com.example.data.InventoryViewModel
import com.example.ui.MainScreen
import com.example.ui.NeedsAttentionDialog
import com.example.ui.ShopGate
import com.example.ui.gate.SignInGate
import com.example.ui.scan.ScanRoute
import com.example.ui.home.HomeScreen
import com.example.ui.home.ShopStatus
import com.example.ui.home.homeModel
import com.example.ui.inscan.InTopBar
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.example.ui.inscan.RaisedButton
import com.example.ui.inscan.ScreenColumn
import com.example.ui.inscan.Space
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// What sits over the shell, full screen, with the bottom bar hidden.
sealed interface Overlay {
    // Screens still on the earlier layout, reached through More until each is rebuilt.
    data object Classic : Overlay

    // The Scan screen (scan-pin-ui-spec.md section 2). Cancel returns to where it was opened.
    data object Scan : Overlay
}

@Composable
fun InScanApp(viewModel: InventoryViewModel, modifier: Modifier = Modifier, autoSync: Boolean = true) {
    val connection by viewModel.connection.collectAsState()
    Box(modifier.fillMaxSize().background(Palette.bg)) {
        // Nothing else is usable until this phone is connected and someone signs in. Connecting
        // (address, setup code, phone name) still uses the earlier form; signing in is the new gate.
        if (!connection.enrolled) {
            ShopGate(viewModel, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars))
        } else if (connection.signedInAs == null) {
            SignInGate(viewModel)
        } else {
            Shell(viewModel, connection.signedInAs!!, autoSync)
        }
    }
}

@Composable
private fun Shell(vm: InventoryViewModel, signedInAs: String, autoSync: Boolean) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var showAttention by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // While the app is on screen: send and fetch now, then every minute.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, autoSync) {
        if (!autoSync) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { vm.runSync(); delay(60_000) }
        }
    }
    val notice by vm.notice.collectAsState()
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); vm.consumeNotice() } }

    val units by vm.units.collectAsState()
    val outbox by vm.outbox.collectAsState()
    val problem by vm.syncProblem.collectAsState()
    val lastSynced by vm.lastSyncedAt.collectAsState()
    val zone = ZoneId.systemDefault()
    val refused = outbox.count { it.state == "needs_attention" }
    val status = ShopStatus(
        upToDateAt = lastSynced?.atZone(zone)?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))?.lowercase(),
        stale = lastSynced?.let { Duration.between(it, Instant.now()) > Duration.ofMinutes(15) } ?: false,
        offline = problem != null,
        waiting = outbox.size - refused,
        sending = false,
        refused = refused,
    )

    if (showAttention) NeedsAttentionDialog(vm, onDismiss = { showAttention = false })

    // System Back on any tab root goes to Home; Back on Home leaves the app.
    BackHandler(enabled = overlay == null && tab != Tab.HOME) { tab = Tab.HOME }
    BackHandler(enabled = overlay != null) { overlay = null }

    Box(Modifier.fillMaxSize()) {
        when (val o = overlay) {
            Overlay.Classic -> ClassicScreens(vm) { overlay = null }
            // Unit detail, check-in and the tag screen aren't rebuilt yet: they open in Classic screens.
            Overlay.Scan -> ScanRoute(
                vm, signedInAs,
                onClose = { overlay = null },
                onOpenUnit = { overlay = Overlay.Classic },
                onCheckIn = { vin -> if (vin != null) vm.onBarcodeScanned(vin); overlay = Overlay.Classic },
                onReprint = { id -> vm.openLabelSheet(id); overlay = Overlay.Classic },
            )
            null -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            model = homeModel(units, LocalDate.now(zone), zone),
                            status = status,
                            signedInAs = signedInAs,
                            onFind = { overlay = Overlay.Classic },
                            onAccount = { tab = Tab.MORE },
                            onCheckIn = { overlay = Overlay.Classic },
                            onReprint = { overlay = Overlay.Classic },
                            onOpenUnit = { overlay = Overlay.Classic },
                            onOpenStock = { _: UnitStatus? -> tab = Tab.STOCK },
                            onReviewRefused = { showAttention = true },
                        )
                        Tab.STOCK -> NotRebuiltYet("Stock", "The new Stock list is being built. Until it lands, the earlier list is in Classic screens.") { overlay = Overlay.Classic }
                        Tab.TICKETS -> WorkTickets()
                        Tab.MORE -> MoreScreen(vm, signedInAs, refused, onAttention = { showAttention = true }, onClassic = { overlay = Overlay.Classic })
                    }
                }
                InBottomBar(tab, { tab = it }, onScan = { overlay = Overlay.Scan }, moreNeedsAttention = refused > 0)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

// Tickets (TBA), first release: a plain screen. No buttons, no badge, no sample data (4c).
@Composable
private fun WorkTickets() {
    Column(Modifier.fillMaxSize()) {
        InTopBar("Work tickets")
        ScreenColumn(Modifier.padding(top = Space.section)) {
            Text("Work tickets aren't built yet. Repairs, prep and parts requests will live here.",
                style = InType.body, color = Palette.text, modifier = Modifier.testTag("tickets.tba"))
        }
    }
}

@Composable
private fun NotRebuiltYet(title: String, body: String, onClassic: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        InTopBar(title)
        ScreenColumn(Modifier.padding(top = Space.section)) {
            Text(body, style = InType.body, color = Palette.text)
            RaisedButton("Open Classic screens", onClassic, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MoreScreen(vm: InventoryViewModel, signedInAs: String, refused: Int, onAttention: () -> Unit, onClassic: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        InTopBar("More")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.section),
            verticalArrangement = Arrangement.spacedBy(Space.item)) {
            Text("Signed in as $signedInAs", style = InType.meta, color = Palette.text2)
            if (refused > 0) RaisedButton("Needs attention ($refused)", onAttention, Modifier.fillMaxWidth().testTag("more.attention"))
            RaisedButton("Send changes now", { vm.syncSoon() }, Modifier.fillMaxWidth().testTag("more.sync"))
            RaisedButton("Classic screens", onClassic, Modifier.fillMaxWidth().testTag("more.classic"))
            Text("Documents, listings and tools from the earlier layout, until each one moves here.", style = InType.meta, color = Palette.text2)
            RaisedButton("Sign out", { vm.signOut() }, Modifier.fillMaxWidth().testTag("more.signout"))
        }
    }
}

// The earlier layout, full screen with its own Close, so nothing the shop relies on goes missing
// while screens move over one by one.
@Composable
private fun ClassicScreens(vm: InventoryViewModel, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        InTopBar("Classic screens") {
            IconButton(onClose, Modifier.testTag("classic.close")) { Icon(Icons.Rounded.Close, "Close", tint = Palette.text) }
        }
        // The earlier screens keep their own theme until they're rebuilt.
        Box(Modifier.weight(1f)) { com.example.ui.theme.MyApplicationTheme { MainScreen(viewModel = vm, autoSync = false) } }
    }
}
