package com.example.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.example.ui.inscan.Roles
import com.example.ui.inscan.chipDepth
import com.example.ui.inscan.navLabelSize
import com.example.ui.inscan.scanButton

enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("home", "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    STOCK("stock", "Stock", Icons.Outlined.Inventory2, Icons.Rounded.Inventory2),
    TICKETS("tickets", "Tickets", Icons.Outlined.Build, Icons.Rounded.Build),
    MORE("more", "More", Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz),
}

// Home · Stock · [Scan] · Tickets · More (locked order). A custom Row, not NavigationBar, because
// NavigationBar clips its children and would cut off the raised Scan button (4c build note).
@Composable
fun InBottomBar(selected: Tab, onTab: (Tab) -> Unit, onScan: () -> Unit, moreNeedsAttention: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Palette.surface).windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.divider))
        Row(Modifier.fillMaxWidth().height(80.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
            TabItem(Tab.HOME, selected == Tab.HOME, onTab, Modifier.weight(1f))
            TabItem(Tab.STOCK, selected == Tab.STOCK, onTab, Modifier.weight(1f))
            ScanItem(onScan, Modifier.weight(1f))
            TabItem(Tab.TICKETS, selected == Tab.TICKETS, onTab, Modifier.weight(1f))
            TabItem(Tab.MORE, selected == Tab.MORE, onTab, Modifier.weight(1f), dot = moreNeedsAttention)
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, onTab: (Tab) -> Unit, modifier: Modifier, dot: Boolean = false) {
    Column(
        modifier.height(80.dp)
            .selectable(selected, role = Role.Tab, onClick = { onTab(tab) })
            .testTag("nav.${tab.route}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(15.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.width(64.dp).height(32.dp)
                    .then(if (selected) Modifier.chipDepth(Roles.accentTint, 16.dp) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (selected) tab.selectedIcon else tab.icon, null, tint = if (selected) Roles.accentInk else Palette.text2, modifier = Modifier.size(24.dp))
            }
            if (dot) Box(Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 2.dp).size(8.dp).background(Roles.danger.ink, CircleShape))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            tab.label,
            style = InType.tag.copy(fontSize = navLabelSize(), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) Palette.text else Palette.text2,
            maxLines = 1,
        )
    }
}

// Scan is an action, not a tab: a 64 dp emerald circle raised 16 dp above the bar edge.
@Composable
private fun ScanItem(onScan: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        modifier.height(80.dp)
            .clickable(source, indication = null, role = Role.Button, onClick = onScan)
            .testTag("nav.scan"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The circle rides 17 dp above the bar edge; the label lines up with the other tabs (51 dp down).
        Box(Modifier.height(51.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.offset(y = (-17).dp).size(64.dp).scale(if (pressed) 0.96f else 1f).scanButton(pressed),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.QrCodeScanner, null, tint = Roles.onAccent, modifier = Modifier.size(30.dp))
            }
        }
        Text("Scan", style = InType.tag.copy(fontSize = navLabelSize(), fontWeight = FontWeight.SemiBold), color = Palette.text)
    }
}
