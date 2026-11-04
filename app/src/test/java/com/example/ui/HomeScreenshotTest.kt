package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.countryatv.core.ItemId
import com.example.data.ShopUnitEntity
import com.example.ui.home.HomeScreen
import com.example.ui.home.ShopStatus
import com.example.ui.home.homeModel
import com.example.ui.inscan.InScanTheme
import com.example.ui.inscan.Palette
import com.example.ui.shell.InBottomBar
import com.example.ui.shell.Tab
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// Home against the locked mockup (prototypes/home-a2b1c2.html): same frame size, same content.
// The units here are test fixtures, never shipped.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w402dp-h874dp-xhdpi")
class HomeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("America/Los_Angeles")
    private val today = LocalDate.of(2026, 11, 5) // a Thursday

    private fun unit(daysAgo: Long, make: String, model: String, type: String, status: String, year: Int = 2026): ShopUnitEntity {
        val at = ZonedDateTime.of(today.minusDays(daysAgo).atTime(10, 0), zone).toInstant().toEpochMilli()
        return ShopUnitEntity(id = ItemId.new(at), vin = null, vinCheck = "", year = year, make = make, model = model, subModel = "",
            color = "", unitType = type, priceCents = 0, status = status, notes = "", version = 1, updatedAt = "", updatedBy = "")
    }

    private val units = listOf(
        unit(2, "Honda", "FourTrax Rancher 4x4", "ATV", "INCOMING"),
        unit(4, "Polaris", "RZR Trail S 1000", "UTV", "INCOMING"),
        unit(9, "Yamaha", "YZ250F", "Dirt Bike", "INCOMING"),
        unit(1, "Can-Am", "Outlander 700", "ATV", "INCOMING"),
    ) + List(5) { unit(20L + it, "Honda", "Pioneer 520", "UTV", "IN_STOCK") } +
        List(2) { unit(30L + it, "Kawasaki", "KX112", "Dirt Bike", "SALE_PENDING") }

    private fun shoot(name: String, status: ShopStatus) {
        compose.setContent {
            InScanTheme {
                Column(Modifier.fillMaxSize().background(Palette.bg)) {
                    HomeScreen(homeModel(units, today, zone), status, "Dale Lewis", {}, {}, {}, {}, {}, {}, {}, Modifier.weight(1f))
                    InBottomBar(Tab.HOME, {}, {}, moreNeedsAttention = status.refused > 0)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/home-$name.png")
    }

    @Test fun normal() = shoot("normal", ShopStatus("2:14 pm", stale = false, offline = false, waiting = 0, sending = false, refused = 0))

    @Test fun offline() = shoot("offline", ShopStatus("9:02 am", stale = true, offline = true, waiting = 3, sending = false, refused = 0))

    @Test fun refused() = shoot("refused", ShopStatus("2:14 pm", stale = false, offline = false, waiting = 0, sending = false, refused = 1))

    @Test
    fun `incoming shows the oldest check-in first, with no names, and the right counts`() {
        val m = homeModel(units, today, zone)
        assertEquals(4, m.incomingCount); assertEquals(5, m.inStockCount); assertEquals(2, m.salePendingCount)
        assertEquals(listOf("Oct 27", "Sun", "Tue"), m.incoming.map { it.meta.substringAfter("checked in ") })
        assertEquals("Two wheels, checked in Oct 27", m.incoming.first().meta)
    }
}
