package com.example.ui.inscan

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.ui.graphics.vector.ImageVector
import com.countryatv.core.UnitStatus
import com.countryatv.core.UnitType

// Status mapping (section 6): every status is icon + word + colour, never colour alone.
data class StatusLook(val word: String, val icon: ImageVector, val tone: Tone)

fun UnitStatus.look(): StatusLook = when (this) {
    UnitStatus.INCOMING -> StatusLook("Incoming", Icons.Outlined.Inventory2, Roles.warn)
    UnitStatus.IN_STOCK -> StatusLook("In stock", Icons.Outlined.CheckCircle, Roles.good)
    UnitStatus.SALE_PENDING -> StatusLook("Sale pending", Icons.Outlined.HourglassEmpty, Roles.info)
    UnitStatus.SOLD -> StatusLook("Sold", Icons.Outlined.Sell, Roles.neutral)
    UnitStatus.RETIRED -> StatusLook("Retired", Icons.Outlined.Archive, Roles.neutral)
}

// The phone stores status as the enum name ("IN_STOCK"); older rows may hold the label.
fun statusOf(stored: String): UnitStatus? =
    UnitStatus.entries.firstOrNull { it.name == stored } ?: UnitStatus.entries.firstOrNull { it.label.equals(stored, ignoreCase = true) }

// The words the shop sees for each unit type (landing-nav-ui-prompt.md section 5). The owner will
// manage this list later; until then it maps the types the shop system already stores.
fun UnitType.shopWord(): String = when (this) {
    UnitType.ATV -> "Four wheels"
    UnitType.UTV -> "Side-by-side"
    UnitType.DIRT_BIKE -> "Two wheels"
    UnitType.SNOWMOBILE -> "Snow"
    UnitType.PWC -> "Water"
    UnitType.OTHER -> "Other"
}

fun unitTypeWord(stored: String): String = UnitType.fromLabel(stored)?.shopWord() ?: "Other"
