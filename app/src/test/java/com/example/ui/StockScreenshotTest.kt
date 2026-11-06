package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.countryatv.core.LabelSpec
import com.countryatv.core.UnitStatus
import com.example.data.ShopUnitEntity
import com.example.label.LabelRenderer
import com.example.ui.inscan.InScanTheme
import com.example.ui.inscan.Palette
import com.example.ui.shell.InBottomBar
import com.example.ui.shell.Tab
import com.example.ui.stock.CheckedIn
import com.example.ui.stock.FindSheet
import com.example.ui.stock.PhoneCopy
import com.example.ui.stock.StockFilters
import com.example.ui.stock.StockQuery
import com.example.ui.stock.StockScreen
import com.example.ui.stock.StockSheet
import com.example.ui.stock.StockSheets
import com.example.ui.stock.StockSort
import com.example.ui.stock.StockStatus
import com.example.ui.stock.StockUiState
import com.example.ui.stock.TagScreen
import com.example.ui.stock.UnitScreen
import com.example.ui.stock.exactUnit
import com.example.ui.stock.findResults
import com.example.ui.stock.filterSheetModel
import com.example.ui.stock.matchUnit
import com.example.ui.stock.statusActions
import com.example.ui.stock.stockCard
import com.example.ui.stock.stockView
import com.example.label.LabelShare
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

// The Stock tab, the search sheet and the unit screen against the locked mockup
// (prototypes/stock.html): same frame, same content where the phone's copy can carry it.
// Signed in as Dale (Employee): no names on cards (D-H4). The units are test fixtures, never shipped.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w402dp-h874dp-xhdpi")
class StockScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("America/Los_Angeles")
    // Fri 30 Oct 2026, 9:38 am, as in the mockup.
    private val now: Instant = ZonedDateTime.of(2026, 10, 30, 9, 38, 0, 0, zone).toInstant()

    private val crockford = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    // A ULID made at [at] whose printed tag ID (its last 8 characters) is [short].
    private fun ulid(at: Instant, short: String): String {
        var t = at.toEpochMilli(); val head = CharArray(10)
        for (i in 9 downTo 0) { head[i] = crockford[(t and 31).toInt()]; t = t ushr 5 }
        return String(head) + "00000000" + short
    }

    private var seq = 0
    private fun unit(
        short: String?, year: Int?, make: String, model: String, type: String, color: String, status: UnitStatus,
        price: Long = 0, checkedDaysAgo: Long = 20, checkedAt: Pair<Int, Int> = 10 to 0, inStockDaysAgo: Long? = null, vin: String? = "auto",
    ): ShopUnitEntity {
        val at = ZonedDateTime.of(now.atZone(zone).toLocalDate().minusDays(checkedDaysAgo).atTime(checkedAt.first, checkedAt.second), zone).toInstant()
        seq++
        val s = short ?: ("T" + seq.toString().padStart(7, '0')).replace('I', '1').replace('L', '1').replace('O', '0')
        return ShopUnitEntity(
            id = ulid(at, s), vin = if (vin == "auto") "1XYZ${(100000000000L + seq * 7919L)}9" else vin, vinCheck = "ok",
            year = year, make = make, model = model, subModel = "", color = color, unitType = type, priceCents = price * 100,
            status = status.name, notes = "", version = 1, updatedAt = at.plusSeconds(seq.toLong() * 60).toString(), updatedBy = "",
            inStockSince = inStockDaysAgo?.let { now.minusSeconds(it * 86_400 + 3600).toString() },
        )
    }

    private val inStock = listOf(
        unit("5NVA9HQC", 2026, "Polaris", "Sportsman 570", "ATV", "Grey", UnitStatus.IN_STOCK, 9_499, 3, inStockDaysAgo = 1),
        unit("H2CW8VKD", 2026, "Honda", "CRF450R", "Dirt Bike", "Black", UnitStatus.IN_STOCK, 9_799, 4, inStockDaysAgo = 2, vin = null),
        unit("R4KX9BWN", 2026, "Can-Am", "Maverick R", "Side-by-Side (UTV)", "Orange", UnitStatus.IN_STOCK, 34_999, 5, inStockDaysAgo = 3),
        unit("J8TP3HMC", 2026, "Honda", "Talon 1000X", "Side-by-Side (UTV)", "White", UnitStatus.IN_STOCK, 23_499, 6, inStockDaysAgo = 4),
        unit("N4QW6TKB", 2026, "Kawasaki", "KX250", "Dirt Bike", "Green", UnitStatus.IN_STOCK, 8_399, 8, inStockDaysAgo = 6, vin = null),
        unit("W6DN2QFA", 2026, "Sea-Doo", "GTI SE 170", "Jet Ski", "White", UnitStatus.IN_STOCK, 14_299, 11, inStockDaysAgo = 9),
        unit("Q7MV3XRP", 2026, "Polaris", "RZR Trail S 1000", "Side-by-Side (UTV)", "Blue", UnitStatus.IN_STOCK, 17_999, 12, inStockDaysAgo = 10),
        unit("D2HN8WKE", 2025, "Polaris", "Sportsman 450 H.O.", "ATV", "Green", UnitStatus.IN_STOCK, 6_999, 63, inStockDaysAgo = 61),
    ) + List(6) { unit(null, 2026, "Polaris", "Ranger 570", "Side-by-Side (UTV)", "Sage", UnitStatus.IN_STOCK, 15_999, 80L + it, inStockDaysAgo = 70L + it) } +
        List(23) {
            val mk = listOf("Honda" to "Pioneer 520", "Can-Am" to "Outlander 700", "Yamaha" to "Grizzly EPS", "Kawasaki" to "Brute Force 750", "Ski-Doo" to "MXZ 600", "Yamaha" to "YZ125")[it % 6]
            val tp = listOf("Side-by-Side (UTV)", "ATV", "ATV", "ATV", "Snowmobile", "Dirt Bike")[it % 6]
            unit(null, if (it % 5 == 0) 2025 else 2026, mk.first, mk.second, tp, "Red", UnitStatus.IN_STOCK, 9_000L + it * 100, 90L + it, inStockDaysAgo = 85L + it)
        }

    private val incoming = listOf(
        unit("B4TN7GWS", 2026, "Yamaha", "YZ250F", "Dirt Bike", "Blue", UnitStatus.INCOMING, 8_999, 4),
        unit("3HZW8RTB", 2026, "Honda", "FourTrax Rancher 4x4", "ATV", "Red", UnitStatus.INCOMING, 7_299, 3, 9 to 5),
        unit("6MDQ1KXA", 2026, "Polaris", "RZR XP 1000", "Side-by-Side (UTV)", "Grey", UnitStatus.INCOMING, 22_999, 3, 14 to 20),
        unit("P5TJ7KCX", 2026, "Yamaha", "Wolverine RMAX2", "Side-by-Side (UTV)", "Olive", UnitStatus.INCOMING, 21_499, 2),
        unit("K7RW2PXD", 2026, "Kawasaki", "KX112", "Dirt Bike", "Green", UnitStatus.INCOMING, 5_499, 0, 8 to 44),
        unit("M9PQ4TZE", 2026, "Polaris", "Ranger 1000", "Side-by-Side (UTV)", "Sage", UnitStatus.INCOMING, 18_999, 0, 9 to 21),
    )
    private val pending = listOf(
        unit("G3XT5NWA", 2026, "Polaris", "General XP 1000", "Side-by-Side (UTV)", "Black", UnitStatus.SALE_PENDING, 26_999, 30, inStockDaysAgo = 20),
        unit("V8KM2RDQ", 2026, "Yamaha", "Wolverine X4", "Side-by-Side (UTV)", "Black", UnitStatus.SALE_PENDING, 18_999, 40, inStockDaysAgo = 30),
    )
    private val sold = List(4) { unit(null, 2025, "Polaris", "Sportsman 850", "ATV", "Blue", UnitStatus.SOLD, 11_999, 120L + it, inStockDaysAgo = 100) } +
        List(2) { unit(null, 2025, "Sea-Doo", "Spark", "Jet Ski", "White", UnitStatus.SOLD, 7_999, 150L + it, inStockDaysAgo = 140) }
    private val units = inStock + incoming + pending + sold

    private val online = PhoneCopy(offline = false, asOf = "9:36 am", stale = false)

    @Composable
    private fun StockFrame(start: StockUiState, copy: PhoneCopy = online) {
        var state by remember { mutableStateOf(start) }
        InScanTheme {
            Box(Modifier.fillMaxSize().background(Palette.bg)) {
                Column(Modifier.fillMaxSize()) {
                    StockScreen(state, units, copy, "Dale Lewis", now, zone, { state = it }, {}, {}, Modifier.weight(1f))
                    InBottomBar(Tab.STOCK, {}, {}, moreNeedsAttention = false)
                }
                StockSheets(state, units, now, zone, { state = it })
            }
        }
    }

    private fun shoot(name: String, state: StockUiState, copy: PhoneCopy = online) {
        compose.setContent { StockFrame(state, copy) }
        compose.onRoot().captureRoboImage("src/test/screenshots/stock-$name.png")
    }

    private fun q(status: StockStatus = StockStatus.IN_STOCK, text: String = "", filters: StockFilters = StockFilters()) =
        StockUiState(StockQuery(status = status, text = text, filters = filters))

    @Test fun inStock() = shoot("in-stock", q())
    @Test fun incomingFromHome() = shoot("incoming", q(StockStatus.INCOMING))
    @Test fun search() = shoot("search", q(text = "POLARIS"))
    @Test fun noMatchHere() = shoot("no-match", q(text = "WOLVERINE"))
    @Test fun noMatchAnywhere() = shoot("no-match-anywhere", q(text = "RZR 2019"))
    @Test fun filtered() = shoot("filtered", q(filters = StockFilters(noVin = true, types = setOf("Two wheels"))))
    @Test fun sortSheet() = shoot("sort", q().copy(sheet = StockSheet.Sort))
    @Test fun filterSheet() = shoot("sheet", q().copy(sheet = StockSheet.Filter(StockFilters(types = setOf("Two wheels")))))
    @Test fun filterSheetNothing() = shoot("sheet-nothing", q().copy(sheet = StockSheet.Filter(StockFilters(types = setOf("Water"), noVin = true))))
    @Test fun offline() = shoot("offline", q(), PhoneCopy(offline = true, asOf = "9:02 am", stale = true))
    @Test fun offlineNoMatch() = shoot("offline-no-match", q(text = "RZR 2019"), PhoneCopy(offline = true, asOf = "9:02 am", stale = true))

    @Test
    fun `tapping Sort opens the sort sheet and a row applies and closes`() {
        compose.setContent { StockFrame(q()) }
        compose.onNodeWithTag("stock.sort").performClick()
        compose.onNodeWithTag("sort.PRICE_HIGH").performClick()
        compose.onNodeWithTag("stock.sort").assertExists()
        compose.onNodeWithTag("stock.unit.R4KX9BWN").assertExists()
    }

    private fun shootFind(name: String, text: String) {
        compose.setContent {
            InScanTheme { FindSheet(units, now, zone, onOpenUnit = {}, onCancel = {}, initialText = text, autoFocus = false) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/find-$name.png")
    }

    @Test fun findBeforeTyping() = shootFind("before", "")
    @Test fun findResults() = shootFind("results", "RZR")
    @Test fun findNothing() = shootFind("nothing", "RZR 2019")

    private fun shootUnit(name: String, short: String, admin: Boolean = false, problem: String? = null, waiting: Int = 0) {
        val u = units.first { it.id.endsWith(short) }
        compose.setContent {
            InScanTheme {
                Column(Modifier.fillMaxSize().background(Palette.bg)) {
                    UnitScreen(u, "Dale Lewis", admin, waiting, problem, now, zone, {}, {}, {}, { _, _ -> }, Modifier.weight(1f))
                    InBottomBar(Tab.STOCK, {}, {}, moreNeedsAttention = false)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/unit-$name.png")
    }

    @Test fun unitInStock() = shootUnit("in-stock", "5NVA9HQC")
    @Test fun unitIncomingOffline() = shootUnit("incoming", "3HZW8RTB", problem = "No signal.", waiting = 1)
    @Test fun unitSalePending() = shootUnit("sale-pending", "G3XT5NWA")
    @Test fun unitAdmin() = shootUnit("admin", "H2CW8VKD", admin = true)

    @Test
    fun tagScreen() {
        val u = units.first { it.id.endsWith("5NVA9HQC") }
        val spec = LabelSpec(50.0, 30.0, 203)
        val content = com.countryatv.core.LabelContent(u.id, u.make, u.model, u.subModel, u.year.toString(), u.color, u.unitType, u.vin.orEmpty())
        compose.setContent {
            InScanTheme {
                Column(Modifier.fillMaxSize().background(Palette.bg)) {
                    TagScreen(u, "Dale Lewis", spec, LabelRenderer.render(content, spec), {}, {}, {}, {}, Modifier.weight(1f))
                    InBottomBar(Tab.STOCK, {}, {}, moreNeedsAttention = false)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/unit-tag.png")
    }

    // ---------- Behaviour ----------

    @Test
    fun `chip counts match Home and All means not sold`() {
        val v = stockView(units, StockQuery(), now, zone)
        assertEquals(listOf("All" to 45, "In stock" to 37, "Incoming" to 6, "Sale pending" to 2, "Sold" to null),
            v.chips.map { it.first.word to it.second })
        assertEquals("37 units", v.resultsLine)
    }

    @Test
    fun `incoming lists the oldest check-in first and says how long each has waited`() {
        val v = stockView(units, StockQuery(status = StockStatus.INCOMING), now, zone)
        assertEquals("B4TN7GWS", v.results.first().id.takeLast(8))
        assertEquals("6 units, waiting longest first", v.resultsLine)
        val first = stockCard(v.results.first(), now, zone).parts.map { it.text }
        assertEquals(listOf("Two wheels", "Blue", "checked in Mon", "waiting 4 days"), first)
        val today = stockCard(v.results.last(), now, zone).parts.map { it.text }
        assertTrue(today.contains("waiting since 9:21 am"))
    }

    @Test
    fun `in stock cards show colour, price and days in stock, and no names`() {
        val c = stockCard(inStock[0], now, zone)
        assertEquals(listOf("Four wheels", "Grey", "$9,499", "1 day in stock"), c.parts.map { it.text })
        assertEquals("2026 Polaris Sportsman 570, tag 5 N V A 9 H Q C, grey, $9,499, 1 day in stock, in stock.", c.spoken)
        val gap = stockCard(inStock[1], now, zone).parts.first { it.gap }
        assertEquals("no VIN", gap.text)
    }

    @Test
    fun `a search stays in its status and the chips count matches elsewhere`() {
        val v = stockView(units, StockQuery(text = "polaris"), now, zone)
        assertEquals(listOf(12, 9, 2, 1, 4), v.chips.map { it.second })
        val w = stockView(units, StockQuery(text = "wolverine"), now, zone)
        assertTrue(w.results.isEmpty())
        assertEquals("0 units in In stock", w.resultsLine)
        assertNotNull(w.empty)
    }

    @Test
    fun `tag IDs match in any case, with or without ID, and O for 0 and I or L for 1`() {
        val u = incoming.first { it.id.endsWith("3HZW8RTB") }
        listOf("ID 3HZW8RTB", "3hzw8rtb", "3HZW8RTB").forEach { assertNotNull(it, matchUnit(u, it)) }
        val zero = inStock.first { it.id.endsWith("W6DN2QFA") }
        assertNotNull(matchUnit(zero, "w6dn2qfa"))
        val zeros = units.first { it.id.endsWith("T0000009") }
        assertNotNull(matchUnit(zeros, "tOOOOOO9"))
        val one = incoming.first { it.id.endsWith("6MDQ1KXA") }
        listOf("6MDQLKXA", "6mdqikxa").forEach { assertNotNull(it, matchUnit(one, it)) }
    }

    @Test
    fun `words match across punctuation and spacing, and grey is gray`() {
        val canAm = inStock.first { it.id.endsWith("R4KX9BWN") }
        listOf("can am", "canam", "Can-Am", "maverick").forEach { assertNotNull(it, matchUnit(canAm, it)) }
        assertNotNull(matchUnit(inStock.first { it.id.endsWith("W6DN2QFA") }, "seadoo"))
        assertNotNull(matchUnit(inStock[0], "gray"))
        // "KAWASAKI" is a valid 8-character ID too, but it still finds Kawasakis.
        val v = stockView(units, StockQuery(status = StockStatus.ALL, text = "KAWASAKI"), now, zone)
        assertTrue(v.results.isNotEmpty() && v.results.all { it.make == "Kawasaki" })
        assertNull(matchUnit(inStock[0], "%"))
    }

    @Test
    fun `the search sheet jumps only on an exact tag ID or VIN`() {
        assertEquals(incoming[1].id, exactUnit(units, "ID 3HZW8RTB")?.id)
        val vin = inStock[0].vin!!
        assertEquals(inStock[0].id, exactUnit(units, vin.lowercase())?.id)
        assertNull(exactUnit(units, "POLARIS"))
        assertNull(exactUnit(units, "3HZW8RT"))
        assertTrue(findResults(units, "RZR").isNotEmpty())
    }

    @Test
    fun `filter counts ignore their own group, so choosing a type doesn't zero the others`() {
        val m = filterSheetModel(units, StockStatus.IN_STOCK, "", StockFilters(types = setOf("Two wheels")), now, zone, false)
        assertTrue(m.types.first { it.label == "Four wheels" }.count!! > 0)
        assertEquals(m.types.first { it.label == "Two wheels" }.count, m.total)
        val none = filterSheetModel(units, StockStatus.IN_STOCK, "", StockFilters(types = setOf("Water"), noVin = true), now, zone, false)
        assertEquals(0, none.total)
        assertEquals("No VIN + Water match nothing.", none.nothingBecause)
        val week = stockView(units, StockQuery(status = StockStatus.INCOMING, filters = StockFilters(checkedIn = CheckedIn.TODAY)), now, zone)
        assertEquals(2, week.results.size)
    }

    @Test
    fun `price sort puts empty prices last and never repeats a unit`() {
        val v = stockView(units, StockQuery(sort = StockSort.PRICE_HIGH), now, zone)
        assertEquals("R4KX9BWN", v.results.first().id.takeLast(8))
        assertEquals(v.results.size, v.results.map { it.id }.toSet().size)
    }

    @Test
    fun `the unit screen offers only the status changes the shop's rules allow`() {
        assertTrue(statusActions(UnitStatus.INCOMING, isAdmin = false).isEmpty())
        assertTrue(statusActions(UnitStatus.IN_STOCK, isAdmin = false).isEmpty())
        assertEquals(listOf(UnitStatus.IN_STOCK), statusActions(UnitStatus.SALE_PENDING, isAdmin = false).map { it.next })
        assertEquals(listOf(UnitStatus.RETIRED), statusActions(UnitStatus.IN_STOCK, isAdmin = true).map { it.next })
        assertEquals(listOf(UnitStatus.IN_STOCK), statusActions(UnitStatus.SOLD, isAdmin = true).map { it.next })
        statusActions(UnitStatus.SALE_PENDING, true).forEach { assertTrue(UnitStatus.SALE_PENDING.canMoveTo(it.next)) }
    }

    @Suppress("unused") private val keep = LabelShare
}
