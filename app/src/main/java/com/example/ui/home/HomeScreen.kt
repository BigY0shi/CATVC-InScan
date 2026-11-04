package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.countryatv.core.UnitStatus
import com.example.ui.inscan.FindButton
import com.example.ui.inscan.IconBadge
import com.example.ui.inscan.InTopBar
import com.example.ui.inscan.InType
import com.example.ui.inscan.NoticeCard
import com.example.ui.inscan.Palette
import com.example.ui.inscan.RaisedSurface
import com.example.ui.inscan.RecordCard
import com.example.ui.inscan.Roles
import com.example.ui.inscan.SectionHeader
import com.example.ui.inscan.SignedInChip
import com.example.ui.inscan.Space
import com.example.ui.inscan.StatusBanner
import com.example.ui.inscan.TextAction
import com.example.ui.inscan.look

// The shop status zone (4a item 2). Offline never blocks work; these only explain.
data class ShopStatus(
    val upToDateAt: String?,      // "2:14 pm" when this phone last sent and received
    val stale: Boolean,           // more than 15 minutes old
    val offline: Boolean,
    val waiting: Int,             // changes saved on this phone, not yet sent
    val sending: Boolean,
    val refused: Int,             // changes the shop system turned down
)

@Composable
fun HomeScreen(
    model: HomeModel,
    status: ShopStatus,
    signedInAs: String,
    onFind: () -> Unit,
    onAccount: () -> Unit,
    onCheckIn: () -> Unit,
    onReprint: () -> Unit,
    onOpenUnit: (String) -> Unit,
    onOpenStock: (UnitStatus?) -> Unit,
    onReviewRefused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        InTopBar("Country ATV") {
            FindButton(onFind, Modifier.testTag("home.find"))
            SignedInChip(signedInAs, onAccount, Modifier.testTag("home.account"))
        }
        StatusZone(status, onReviewRefused)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Space.gutter, end = Space.gutter, top = Space.item, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(Space.section),
        ) {
            // Quick jobs: two equal tiles.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.item)) {
                QuickJob("Check in a unit", Icons.Outlined.AddBox, onCheckIn, Modifier.weight(1f).fillMaxHeight().testTag("home.checkin"))
                QuickJob("Reprint a tag", Icons.Outlined.Print, onReprint, Modifier.weight(1f).fillMaxHeight().testTag("home.reprint"))
            }
            // Stock at a glance, in the owner's order: first.
            Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                SectionHeader("Stock at a glance")
                if (status.stale && status.upToDateAt != null) Text("Last updated ${status.upToDateAt}", style = InType.meta, color = Palette.text2)
                val large = LocalDensity.current.fontScale >= 1.3f
                val incoming = CountSpec(UnitStatus.INCOMING, model.incomingCount, "Not scanned yet", "Nothing to confirm")
                val inStock = CountSpec(UnitStatus.IN_STOCK, model.inStockCount, "Ready to sell", "Nothing ready to sell")
                val pending = CountSpec(UnitStatus.SALE_PENDING, model.salePendingCount, "Sale started, not finished", "No sales in progress")
                if (large) {
                    listOf(incoming, inStock, pending).forEach { CountRow(it, onOpenStock) }
                } else {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.item)) {
                        CountCard(incoming, onOpenStock, Modifier.weight(1f).fillMaxHeight())
                        CountCard(inStock, onOpenStock, Modifier.weight(1f).fillMaxHeight())
                    }
                    CountCard(pending, onOpenStock, Modifier.fillMaxWidth())
                }
            }
            // Incoming to confirm (first release of the My tickets slot).
            Column(verticalArrangement = Arrangement.spacedBy(Space.item)) {
                SectionHeader("Incoming to confirm") {
                    if (model.incomingCount > 0) TextAction("See all ${model.incomingCount}", { onOpenStock(UnitStatus.INCOMING) }, Modifier.testTag("home.incoming.all"))
                }
                if (model.incoming.isEmpty()) {
                    Text("Nothing waiting to be confirmed.", style = InType.body, color = Palette.text2)
                } else {
                    Text("Scan the box tag to confirm it.", style = InType.meta, color = Palette.text2)
                    model.incoming.forEach { card ->
                        RecordCard(card, { onOpenUnit(card.id) }, Modifier.testTag("home.incoming.${card.id}"))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatusZone(s: ShopStatus, onReviewRefused: () -> Unit) {
    when {
        s.offline -> StatusBanner(
            Icons.Rounded.CloudOff, Roles.warn, "Shop system offline",
            if (s.waiting > 0) "${s.waiting} ${if (s.waiting == 1) "change is" else "changes are"} saved on this phone. They'll send when the shop system is back."
            else "Everything on this phone is up to date. Changes you make will send when it's back.",
            Modifier.testTag("home.banner.offline"),
        )
        s.sending -> StatusBanner(Icons.Rounded.CloudSync, Roles.info, "Sending ${s.waiting} ${if (s.waiting == 1) "change" else "changes"} to the shop system…", null, Modifier.testTag("home.banner.sending"))
        else -> Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).background(Palette.bg).padding(horizontal = Space.gutter).testTag("home.strip"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Check, null, tint = Roles.good.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    s.upToDateAt == null -> "Not updated yet on this phone"
                    s.stale -> "Last updated ${s.upToDateAt}"
                    else -> "Up to date at ${s.upToDateAt}"
                },
                style = InType.meta, color = Palette.text2,
            )
        }
    }
    if (s.refused > 0) {
        Box(Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.item)) {
            NoticeCard(
                Icons.Rounded.ErrorOutline, Roles.danger,
                if (s.refused == 1) "1 change wasn't accepted" else "${s.refused} changes weren't accepted",
                "The shop system turned down a change made on this phone. Review it to fix or throw it away.",
                "Review the change", onReviewRefused, Modifier.testTag("home.notice.refused"),
            )
        }
    }
}

@Composable
private fun QuickJob(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    RaisedSurface(onClick, modifier.heightIn(min = 96.dp)) {
        Column(Modifier.padding(Space.card), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = Roles.accentInk, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = InType.tileLabel, color = Palette.text)
        }
    }
}

private data class CountSpec(val status: UnitStatus, val count: Int, val context: String, val zero: String)

@Composable
private fun CountCard(c: CountSpec, onOpen: (UnitStatus?) -> Unit, modifier: Modifier) {
    val look = c.status.look()
    RaisedSurface({ onOpen(c.status) }, modifier.testTag("home.count.${c.status.name}")) {
        Column(Modifier.padding(Space.card).semantics(mergeDescendants = true) { contentDescription = "${c.count} ${look.word}. ${if (c.count == 0) c.zero else c.context}" },
            verticalArrangement = Arrangement.Top) {
            IconBadge(look.icon, look.tone)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${c.count}", style = InType.bigNumber.copy(lineHeight = 30.sp), color = look.tone.ink)
                Spacer(Modifier.width(8.dp))
                Text(look.word, style = InType.tileLabel, color = Palette.text, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(2.dp))
            Text(if (c.count == 0) c.zero else c.context, style = InType.meta, color = Palette.text2)
        }
    }
}

@Composable
private fun CountRow(c: CountSpec, onOpen: (UnitStatus?) -> Unit) {
    val look = c.status.look()
    RaisedSurface({ onOpen(c.status) }, Modifier.fillMaxWidth().testTag("home.count.${c.status.name}")) {
        Row(Modifier.padding(Space.card), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(look.icon, look.tone)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(look.word, style = InType.tileLabel, color = Palette.text)
                Text(if (c.count == 0) c.zero else c.context, style = InType.meta, color = Palette.text2)
            }
            Text("${c.count}", style = InType.bigNumber, color = look.tone.ink)
        }
    }
}

