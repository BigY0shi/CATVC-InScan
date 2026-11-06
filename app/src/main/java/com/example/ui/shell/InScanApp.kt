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
import com.countryatv.core.ItemId
import com.countryatv.core.UnitStatus
import com.example.data.OutboxEntry
import com.example.data.ShopUnitEntity
import com.example.label.LabelRenderer
import com.example.label.LabelShare
import com.example.ui.LabelSizeDialog
import com.example.ui.inscan.TextAction
import com.example.ui.stock.FindSheet
import com.example.ui.stock.PhoneCopy
import com.example.ui.stock.StockQuery
import com.example.ui.stock.StockScreen
import com.example.ui.stock.StockSheets
import com.example.ui.stock.StockStatus
import com.example.ui.stock.StockUiState
import com.example.ui.stock.TagScreen
import com.example.ui.stock.UnitScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
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
    // The search sheet, opened by Find in Home's top bar (4a item 3).
    data object Find : Overlay
}

// The Stock tab's own back stack: stock, stock/{unitId}, stock/{unitId}/label.
sealed interface StockRoute {
    data object List : StockRoute
    data class Unit(val id: String) : StockRoute
    data class Label(val id: String) : StockRoute
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
    val connectionNow by vm.connection.collectAsState()
    var stock by remember { mutableStateOf(StockUiState()) }
    var stockStack by remember { mutableStateOf(listOf<StockRoute>(StockRoute.List)) }
    // Opening a unit (from a card, the search sheet or Home) puts it on the Stock tab's stack.
    fun openUnit(id: String) { overlay = null; tab = Tab.STOCK; stockStack = listOf(StockRoute.List, StockRoute.Unit(id)) }
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
    BackHandler(enabled = overlay == null && tab == Tab.STOCK && stockStack.size > 1) { stockStack = stockStack.dropLast(1) }
    // The clock the lists read ("waiting 3 days"), ticking once a minute.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = Instant.now() } }
    val copy = PhoneCopy(offline = status.offline, asOf = status.upToDateAt, stale = status.stale)

    Box(Modifier.fillMaxSize()) {
        when (val o = overlay) {
            Overlay.Classic -> ClassicScreens(vm) { overlay = null }
            // Check-in isn't rebuilt on this branch yet: it opens in Classic screens.
            Overlay.Scan -> ScanRoute(
                vm, signedInAs,
                onClose = { overlay = null },
                onOpenUnit = { id -> openUnit(id) },
                onCheckIn = { vin -> if (vin != null) vm.onBarcodeScanned(vin); overlay = Overlay.Classic },
                onReprint = { id -> overlay = null; tab = Tab.STOCK; stockStack = listOf(StockRoute.List, StockRoute.Unit(id), StockRoute.Label(id)) },
            )
            Overlay.Find -> FindSheet(units, now, zone, onOpenUnit = { openUnit(it) }, onCancel = { overlay = null },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
            null -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            model = homeModel(units, LocalDate.now(zone), zone),
                            status = status,
                            signedInAs = signedInAs,
                            onFind = { overlay = Overlay.Find },
                            onAccount = { tab = Tab.MORE },
                            onCheckIn = { overlay = Overlay.Classic },
                            onReprint = { overlay = Overlay.Classic },
                            // Home's cards carry the tag ID; it names one unit on this phone.
                            onOpenUnit = { short -> units.filter { ItemId.shortForm(it.id) == short }.singleOrNull()?.let { openUnit(it.id) } },
                            // The route carries the status, so the tab's remembered state never overrides it.
                            onOpenStock = { s: UnitStatus? ->
                                stock = StockUiState(StockQuery(status = if (s == null) StockStatus.IN_STOCK else StockStatus.of(s)))
                                stockStack = listOf(StockRoute.List)
                                tab = Tab.STOCK
                            },
                            onReviewRefused = { showAttention = true },
                        )
                        Tab.STOCK -> StockTab(vm, stockStack.last(), stock, { stock = it }, units, copy, signedInAs, connectionNow.role == "admin",
                            outbox, now, zone, onPush = { stockStack = stockStack + it }, onPop = { stockStack = stockStack.dropLast(1) },
                            onAccount = { tab = Tab.MORE })
                        Tab.TICKETS -> WorkTickets()
                        Tab.MORE -> MoreScreen(vm, signedInAs, refused, onAttention = { showAttention = true }, onClassic = { overlay = Overlay.Classic })
                    }
                }
                InBottomBar(tab, {
                    // Tapping the active Stock tab again pops it to its root.
                    if (it == Tab.STOCK && tab == Tab.STOCK) stockStack = listOf(StockRoute.List)
                    tab = it
                }, onScan = { overlay = Overlay.Scan }, moreNeedsAttention = refused > 0)
            }
        }
        // Stock's Sort and Filter sheets cover the whole screen, bottom bar included.
        if (overlay == null && tab == Tab.STOCK && stockStack.last() == StockRoute.List) {
            StockSheets(stock, units, now, zone, onState = { stock = it })
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
private fun StockTab(
    vm: InventoryViewModel,
    route: StockRoute,
    state: StockUiState,
    onState: (StockUiState) -> Unit,
    units: List<ShopUnitEntity>,
    copy: PhoneCopy,
    signedInAs: String,
    isAdmin: Boolean,
    outbox: List<OutboxEntry>,
    now: Instant,
    zone: ZoneId,
    onPush: (StockRoute) -> Unit,
    onPop: () -> Unit,
    onAccount: () -> Unit,
) {
    when (route) {
        StockRoute.List -> StockScreen(state, units, copy, signedInAs, now, zone, onState,
            onOpenUnit = { onPush(StockRoute.Unit(it)) }, onAccount = onAccount)
        is StockRoute.Unit -> {
            // Opening a unit always fetches it fresh; the phone's copy shows meanwhile.
            var problem by remember(route.id) { mutableStateOf<String?>(null) }
            LaunchedEffect(route.id) { problem = vm.refreshUnit(route.id) }
            UnitScreen(
                unit = units.firstOrNull { it.id == route.id }, signedInAs = signedInAs, isAdmin = isAdmin,
                waiting = outbox.count { it.itemId == route.id }, checkProblem = problem, now = now, zone = zone,
                onBack = onPop, onAccount = onAccount, onTag = { onPush(StockRoute.Label(route.id)) },
                onChangeStatus = { next, reason -> vm.changeStatus(route.id, next, reason) },
            )
        }
        is StockRoute.Label -> {
            val unit = units.firstOrNull { it.id == route.id }
            val spec by vm.labelSpec.collectAsState()
            val context = LocalContext.current
            var sizing by remember { mutableStateOf(false) }
            var shareProblem by remember { mutableStateOf<String?>(null) }
            val rendered = remember(unit, spec) { unit?.let { LabelRenderer.render(vm.labelContentFor(it), spec) } }
            if (sizing) LabelSizeDialog(spec, onSave = { vm.saveLabelSpec(it); sizing = false }, onDismiss = { sizing = false })
            shareProblem?.let { msg ->
                AlertDialog(onDismissRequest = { shareProblem = null }, confirmButton = { TextAction("OK", { shareProblem = null }) },
                    title = { Text("Couldn't share the tag", style = InType.appTitle) }, text = { Text(msg, style = InType.body) })
            }
            TagScreen(unit, signedInAs, spec, rendered, onBack = onPop, onAccount = onAccount, onSize = { sizing = true },
                onShare = { reason ->
                    val bitmap = rendered?.getOrNull() ?: return@TagScreen
                    val u = unit ?: return@TagScreen
                    runCatching { LabelShare.intentFor(context, bitmap, u.id) }
                        .onSuccess { context.startActivity(it); vm.recordLabelShared(u, reason) }
                        .onFailure { shareProblem = "Couldn't save the tag image to share it. Free up some space on the phone and try again." }
                })
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
