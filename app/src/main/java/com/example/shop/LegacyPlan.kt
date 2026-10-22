package com.example.shop

import com.countryatv.core.ItemId
import com.countryatv.core.UnitStatus
import com.countryatv.core.UnitType
import com.example.data.InventoryListing
import kotlin.math.roundToLong

// What uploading one item from before the shop system would do: either the new item and the
// status steps to reach the status it had on this phone, or the plain reasons it can't go yet.
sealed class LegacyPlan {
    data class Ready(val unit: NewUnit, val statusSteps: List<UnitStatus>) : LegacyPlan()
    data class CantUpload(val reasons: List<String>) : LegacyPlan()

    companion object {
        fun of(old: InventoryListing, newId: () -> String = { ItemId.new() }): LegacyPlan {
            val reasons = mutableListOf<String>()
            if (old.make.isBlank()) reasons += "no make"
            if (old.model.isBlank()) reasons += "no model"
            val cents = if (old.price.isFinite()) (old.price * 100).roundToLong() else 0L
            if (cents <= 0) reasons += "no price"
            val year = old.year.trim().takeIf { it.isNotEmpty() }?.let { typed ->
                typed.toIntOrNull()?.takeIf { it in 1950..2100 }
                    ?: run { reasons += "year \"$typed\" isn't a 4-digit model year"; null }
            }
            val type = UnitType.fromLabel(old.vehicleType)
            if (type == null) reasons += "vehicle type \"${old.vehicleType}\" isn't one the shop system knows"
            val steps = when (old.status.trim().lowercase()) {
                "incoming" -> emptyList()
                "in stock" -> listOf(UnitStatus.IN_STOCK)
                "sold" -> listOf(UnitStatus.IN_STOCK, UnitStatus.SOLD)
                else -> { reasons += "status \"${old.status}\" isn't one the shop system knows"; emptyList() }
            }
            if (reasons.isNotEmpty()) return CantUpload(reasons)
            return Ready(
                NewUnit(
                    id = newId(), make = old.make.trim(), model = old.model.trim(), year = year,
                    color = old.color.trim(), unitType = type!!.label, vin = old.vin.trim().ifEmpty { null },
                    priceCents = cents, notes = old.notes.trim()
                ),
                steps
            )
        }
    }
}
