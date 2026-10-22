package com.example

import com.countryatv.core.UnitStatus
import com.example.data.InventoryListing
import com.example.shop.LegacyPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPlanTest {
    private fun old(
        year: String = "2024", price: Double = 8499.99, type: String = "Dirt Bike", status: String = "In Stock",
        make: String = "Honda", vin: String = ""
    ) = InventoryListing(
        id = 3, barcode = "MAN-123", make = make, model = "CRF450R", year = year, color = "Red",
        vehicleType = type, vin = vin, price = price, notes = "", status = status
    )

    @Test
    fun `a complete old item becomes a new item with the steps to its status`() {
        val plan = LegacyPlan.of(old(status = "Sold"), newId = { "01J0000000000000000000000A" }) as LegacyPlan.Ready
        assertEquals(849_999, plan.unit.priceCents)
        assertEquals(2024, plan.unit.year)
        assertEquals("Dirt Bike", plan.unit.unitType)
        assertEquals(null, plan.unit.vin)
        assertEquals(listOf(UnitStatus.IN_STOCK, UnitStatus.SOLD), plan.statusSteps)
        assertEquals(emptyList<UnitStatus>(), (LegacyPlan.of(old(status = "Incoming")) as LegacyPlan.Ready).statusSteps)
    }

    @Test
    fun `an old item with gaps stays on the phone with every reason listed`() {
        val plan = LegacyPlan.of(old(year = "24ish", price = 0.0, type = "Golf Cart", make = " ")) as LegacyPlan.CantUpload
        assertEquals(4, plan.reasons.size)
        assertTrue(plan.reasons.any { it.contains("24ish") })
        assertTrue(plan.reasons.any { it.contains("Golf Cart") })
        assertTrue("no price" in plan.reasons)
        assertTrue("no make" in plan.reasons)
    }

    @Test
    fun `a blank year is allowed`() {
        assertEquals(null, (LegacyPlan.of(old(year = "")) as LegacyPlan.Ready).unit.year)
    }
}
