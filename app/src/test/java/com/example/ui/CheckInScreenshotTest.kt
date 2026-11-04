package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelSpec
import com.countryatv.core.UnitType
import com.example.data.ShopUnitEntity
import com.example.label.LabelRenderer
import com.example.ui.checkin.CheckInActions
import com.example.ui.checkin.CheckInDraft
import com.example.ui.checkin.CheckInScreen
import com.example.ui.checkin.CheckInSheet
import com.example.ui.checkin.CheckInStep
import com.example.ui.checkin.CheckInUi
import com.example.ui.checkin.ExistingUnit
import com.example.ui.checkin.Field
import com.example.ui.checkin.LookupOutcome
import com.example.ui.checkin.LookupResult
import com.example.ui.checkin.PT_36MM_TAPE
import com.example.ui.checkin.PrintPhase
import com.example.ui.checkin.VinAbsent
import com.example.ui.checkin.VinKind
import com.example.ui.checkin.VinResult
import com.example.ui.checkin.checkableVin
import com.example.ui.checkin.cleanVin
import com.example.ui.checkin.lookupNotFoundLine
import com.example.ui.checkin.mostUsed
import com.example.ui.checkin.next
import com.example.ui.checkin.nextProblem
import com.example.ui.checkin.tagPreview
import com.example.ui.checkin.toNewUnit
import com.example.ui.checkin.typeFromBodyClass
import com.example.ui.checkin.typed
import com.example.ui.checkin.vinResult
import com.example.ui.checkin.withLookup
import com.example.ui.checkin.withVinText
import com.example.ui.checkin.yearChips
import com.example.ui.home.recordCard
import com.example.ui.inscan.InScanTheme
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// Check in a unit and Print the tag against the mockup (prototypes/checkin.html), same frame and
// content. Signed in as Dale (Employee): no names on cards (D-H4). Everything here is a test fixture.
// The label-photo states (vin-photo, review-photo) wait on D-C10/D-C11 and aren't built. The
// "printer ready" state needs a printer driver, which InScan doesn't have yet, so the app always
// shows the printer-app route (print-none).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w402dp-h874dp-xhdpi")
class CheckInScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("America/Los_Angeles")
    private val today = LocalDate.of(2026, 10, 30)
    private val crockford = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private fun ulid(daysAgo: Long, short: String): String {
        var t = ZonedDateTime.of(today.minusDays(daysAgo).atTime(10, 0), zone).toInstant().toEpochMilli(); val head = CharArray(10)
        for (i in 9 downTo 0) { head[i] = crockford[(t and 31).toInt()]; t = t ushr 5 }
        return String(head) + "00000000" + short
    }

    private val vin = "4XARR45DXT8104217"
    private val id = ulid(0, "6MDQ1KXA")
    private val polaris = LookupResult(vin, LookupOutcome.FOUND, make = "Polaris", year = "2026")
    private val base = CheckInDraft(unitId = id)
    private val looked = base.withVinText(vin).withLookup(polaris)
    private val filled = looked.copy(type = UnitType.UTV, fromLookup = looked.fromLookup).typed(Field.MODEL) { copy(model = "RZR XP 1000") }
        .copy(colour = "Gray", price = "21,999")

    // The phone's own makes and colours, most used first, for the quick-pick chips.
    private val colours = List(6) { "Red" } + List(5) { "Black" } + List(4) { "Gray" } + List(3) { "White" } + List(2) { "Blue" } + "Green"
    private val makes = listOf("Honda", "Yamaha", "Polaris", "Kawasaki", "Can-Am", "KTM")

    private val existing = ShopUnitEntity(
        id = ulid(16, "9QF3M2ZD"), vin = "1HFTE40A2R4733158", vinCheck = "ok", year = 2025, make = "Honda", model = "CRF250F", subModel = "",
        color = "Red", unitType = "Dirt Bike", priceCents = 589_900, status = "IN_STOCK", notes = "", version = 3, updatedAt = "", updatedBy = "",
    )
    private val content = LabelContent(id, "Polaris", "RZR XP 1000", "", "2026", "Gray", "Side-by-Side (UTV)", vin)

    private fun ui(d: CheckInDraft, spec: LabelSpec = PT_36MM_TAPE) = CheckInUi(
        draft = d, signedInAs = "Dale Lewis", today = today,
        makeChips = makes, colourChips = mostUsed(colours),
        existingCard = d.existing?.let { recordCard(if (it.sold) existing.copy(status = "SOLD") else existing, today, zone) },
        tag = if (d.step == CheckInStep.PRINT) tagPreview(content, spec, d.copies) else null,
    )

    @Composable
    private fun Frame(start: CheckInDraft, keyboard: Boolean = false, spec: LabelSpec = PT_36MM_TAPE, onLeave: () -> Unit = {}) {
        var d by remember { mutableStateOf(start) }
        InScanTheme {
            Column(Modifier.fillMaxSize().background(Palette.bg)) {
                CheckInScreen(ui(d, spec), CheckInActions(onDraft = { d = it }, onLeave = onLeave), Modifier.weight(1f), autoFocus = false)
                if (keyboard) FakeKeyboard()
            }
        }
    }

    // Stands in for the system keyboard (the mockup draws one on Make and model): the screen's
    // bottom bar rides above it.
    @Composable
    private fun FakeKeyboard() {
        Column(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF2B2C2F)).padding(top = 8.dp, start = 4.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(10, 9, 7).forEachIndexed { r, n ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    if (r == 2) Box(Modifier.size(88.dp, 44.dp).background(Color(0xFF46474B), RoundedCornerShape(6.dp)))
                    repeat(n) { Box(Modifier.size(33.dp, 44.dp).background(Color(0xFF46474B), RoundedCornerShape(6.dp))) }
                    if (r == 2) Box(Modifier.size(88.dp, 44.dp).background(Color(0xFF46474B), RoundedCornerShape(6.dp)))
                }
            }
            Text("Test stand-in for the system keyboard", style = InType.meta, color = Palette.text2, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    private fun shoot(name: String, d: CheckInDraft, keyboard: Boolean = false, spec: LabelSpec = PT_36MM_TAPE, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) { Frame(d, keyboard, spec) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/checkin-$name.png")
    }

    // ---------- The mockup's states ----------

    @Test fun vinEmpty() = shoot("vin", base)
    @Test fun vinPasses() = shoot("vin-ok", looked)
    @Test fun vinShort() = shoot("vin-short", base.withVinText(vin.dropLast(1)).copy(vinShown = true))
    @Test fun vinFails() = shoot("vin-fail", base.withVinText("4XARR45D1T8104217"))
    @Test fun vinDuplicate() = shoot("vin-dupe", base.withVinText("1HFTE40A2R4733158").copy(existing = ExistingUnit(existing.id, sold = false)))
    @Test fun type() = shoot("type", looked.copy(step = CheckInStep.TYPE, type = UnitType.UTV))
    @Test fun makeKeyboard() = shoot("make", filled.copy(step = CheckInStep.MAKE), keyboard = true)
    @Test fun makeMissing() = shoot("make-miss", looked.copy(type = UnitType.UTV, step = CheckInStep.MAKE).next())
    @Test fun year() = shoot("year", filled.copy(step = CheckInStep.YEAR))
    @Test fun price() = shoot("price", filled.copy(step = CheckInStep.PRICE))
    @Test fun review() = shoot("review", filled.copy(step = CheckInStep.REVIEW))
    @Test fun editFromReview() = shoot("edit", filled.copy(step = CheckInStep.YEAR, fromReview = true))
    @Test fun printNone() = shoot("print-none", filled.copy(step = CheckInStep.PRINT, saved = true))
    @Test fun didPrint() = shoot("did-print", filled.copy(step = CheckInStep.PRINT, saved = true, phase = PrintPhase.ASK_DID_IT_PRINT))
    @Test fun printed() = shoot("printed", filled.copy(step = CheckInStep.PRINT, saved = true, phase = PrintPhase.PRINTED, recorded = true))
    @Test fun printLater() = shoot("print-later", filled.copy(step = CheckInStep.PRINT, saved = true, sheet = CheckInSheet.PRINT_LATER))
    @Test fun leave() = shoot("leave", filled.copy(step = CheckInStep.MAKE, sheet = CheckInSheet.LEAVE))

    // ---------- More states from the spec ----------

    @Test fun serial() = shoot("serial", base.copy(vinKind = VinKind.SERIAL).withVinText("JH2PE0700KK123"))
    @Test fun addItLater() = shoot("vin-later", base.copy(vinAbsent = VinAbsent.LATER))
    @Test fun vin18() = shoot("vin-18", base.withVinText("4XARR45DXT81042171"))
    @Test fun vinOffline() = shoot("vin-offline", base.withVinText(vin).withLookup(LookupResult(vin, LookupOutcome.OFFLINE)))
    @Test fun soldMatch() = shoot("vin-sold", base.withVinText("1HFTE40A2R4733158").copy(existing = ExistingUnit(existing.id, sold = true)))
    @Test fun whereIsTheVin() = shoot("where", base.copy(sheet = CheckInSheet.WHERE_VIN))
    @Test fun tooSmallForTheVin() = shoot("print-small", filled.copy(step = CheckInStep.PRINT, saved = true, copies = 2), spec = LabelSpec(30.0, 16.0, 300))
    @Test fun reviewLargeText() = shoot("review-2x", filled.copy(step = CheckInStep.REVIEW), fontScale = 2f)
    @Test fun typeLargeText() = shoot("type-1.3x", looked.copy(step = CheckInStep.TYPE, type = UnitType.UTV), fontScale = 1.3f)

    // ---------- Behaviour ----------

    @Test
    fun `Next with the model missing marks it and says why`() {
        compose.setContent { Frame(looked.copy(type = UnitType.UTV, step = CheckInStep.MAKE)) }
        compose.onNodeWithTag("checkin.next").performClick()
        compose.onNodeWithTag("checkin.model.missing").assertExists()
        compose.onNodeWithTag("checkin.step").assertExists()
    }

    @Test
    fun `Cancel asks before leaving, Keep editing stays, Leave leaves`() {
        var left = false
        compose.setContent { Frame(filled.copy(step = CheckInStep.MAKE), onLeave = { left = true }) }
        compose.onNodeWithTag("checkin.cancel").performClick()
        compose.onNodeWithTag("checkin.leave.keep").performClick()
        compose.onNodeWithTag("checkin.sheet").assertDoesNotExist()
        compose.onNodeWithTag("checkin.cancel").performClick()
        compose.onNodeWithTag("checkin.leave.go").performClick()
        assertTrue(left)
    }

    @Test
    fun `editing from Review returns to Review`() {
        compose.setContent { Frame(filled.copy(step = CheckInStep.REVIEW)) }
        compose.onNodeWithTag("checkin.review.edit.year").performClick()
        compose.onNodeWithTag("checkin.next").performClick()
        compose.onNodeWithTag("checkin.save").assertExists()
    }

    @Test
    fun `copies stop at 1 and 5`() {
        compose.setContent { Frame(filled.copy(step = CheckInStep.PRINT, saved = true)) }
        compose.onNodeWithTag("checkin.print.copies").assertTextEquals("1")
        repeat(6) { compose.onNodeWithContentDescription("One more copy").performClick() }
        compose.onNodeWithTag("checkin.print.copies").assertTextEquals("5")
    }

    @Test
    fun `save is disabled while saving`() {
        compose.setContent { Frame(filled.copy(step = CheckInStep.REVIEW, saving = true)) }
        compose.onNodeWithTag("checkin.save").assertIsNotEnabled()
    }

    @Test
    fun `VINs are cleaned before any check, O I and Q included`() {
        assertEquals("4XARR45DXT8104217", cleanVin("4xarr45dxt81o4217", VinKind.VIN))
        assertEquals("1HFTE40A2R4733158", cleanVin("IHFTE-4OA2R 4733158", VinKind.VIN))
        // An 18-character read starting with I whose last 17 pass is taken as those 17.
        assertEquals(vin, cleanVin("I$vin", VinKind.VIN))
        // A serial keeps its letters.
        assertEquals("JH2PE0700KKOIQ", cleanVin("jh2pe0700kk oiq", VinKind.SERIAL))
    }

    @Test
    fun `a 16 or 18 character VIN is never taken as a serial without the switch`() {
        val short = base.withVinText(vin.dropLast(1))
        assertEquals(VinResult.WrongLength(16), vinResult(short))
        assertEquals(Field.VIN, nextProblem(short)?.first)
        assertEquals(CheckInStep.VIN, short.next().step)
        val long = base.withVinText(vin + "9")
        assertEquals(VinResult.WrongLength(18), vinResult(long))
        val serial = base.copy(vinKind = VinKind.SERIAL).withVinText(vin.dropLast(1))
        assertNull(nextProblem(serial))
        assertNull(checkableVin(serial))
    }

    @Test
    fun `a failing VIN can't go on or be looked up without the tick`() {
        val bad = base.withVinText("4XARR45D1T8104217")
        assertEquals(VinResult.CheckFails, vinResult(bad))
        assertEquals(Field.FRAME_MATCH, nextProblem(bad)?.first)
        assertNull(checkableVin(bad))
        val ticked = bad.copy(frameMatch = true)
        assertNull(nextProblem(ticked))
        assertTrue(toNewUnit(ticked.copy(type = UnitType.ATV, make = "Polaris", model = "Sportsman", price = "9000")).vinFrameVerified)
    }

    @Test
    fun `the lookup fills empty fields only, never the VIN or notes, and drops a result for an old VIN`() {
        val typed = base.withVinText(vin).copy(make = "Polaris Industries", notes = "Dent on the crate")
        val r = LookupResult(vin, LookupOutcome.FOUND, make = "Polaris", model = "RZR", year = "2026", type = UnitType.UTV)
        val d = typed.withLookup(r)
        assertEquals("Polaris Industries", d.make)
        assertEquals("RZR", d.model)
        assertEquals("Dent on the crate", d.notes)
        assertEquals(vin, d.vinText)
        assertEquals(setOf(Field.MODEL, Field.YEAR, Field.TYPE), d.fromLookup)
        // The VIN changed while the lookup ran: nothing changes.
        val moved = base.withVinText("1HFTE40A2R4733158")
        assertEquals(moved, moved.withLookup(r))
        // A typed change takes the "From the VIN" marker away.
        assertFalse(Field.MODEL in d.typed(Field.MODEL) { copy(model = "RZR XP 1000") }.fromLookup)
    }

    @Test
    fun `what the lookup didn't find is said in words`() {
        assertEquals("Not found: model and type. You'll fill those in next.", lookupNotFoundLine(polaris))
        assertNull(lookupNotFoundLine(polaris.copy(model = "RZR", type = UnitType.UTV)))
    }

    @Test
    fun `vPIC body classes map to a type only when clear`() {
        assertEquals(UnitType.UTV, typeFromBodyClass("Off-Road Vehicle - Recreational Off-Highway Vehicles (ROV)"))
        assertEquals(UnitType.ATV, typeFromBodyClass("Off-Road Vehicle - All Terrain Vehicle (ATV) (Motorcycle-type)"))
        assertEquals(UnitType.DIRT_BIKE, typeFromBodyClass("Off-Road Vehicle - Dirt Bike / Motocross"))
        assertNull(typeFromBodyClass("Motorcycle - Standard"))
        assertNull(typeFromBodyClass(null))
    }

    @Test
    fun `price is required and never saved as zero`() {
        val d = filled.copy(step = CheckInStep.PRICE, price = "")
        assertEquals(Field.PRICE, nextProblem(d)?.first)
        assertEquals(Field.PRICE, nextProblem(d.copy(price = "0"))?.first)
        assertEquals(2_199_900L, toNewUnit(filled).priceCents)
    }

    @Test
    fun `Add it later and It has none save no VIN`() {
        val later = base.copy(vinAbsent = VinAbsent.LATER)
        assertNull(nextProblem(later))
        assertNull(toNewUnit(later.copy(type = UnitType.ATV, make = "Honda", model = "Rancher", price = "7299")).vin)
    }

    @Test
    fun `the model years run ahead of the calendar from July`() {
        assertEquals(listOf("2027", "2026", "2025"), yearChips(today))
        assertEquals(listOf("2026", "2025", "2024"), yearChips(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun `two copies print Box 1 of 2 and Box 2 of 2, and a small label drops the VIN row`() {
        val spec = PT_36MM_TAPE
        val rows1 = LabelRenderer.printedRows(content, spec, "Box 1 of 2")!!
        assertTrue(rows1.contains("Box 1 of 2"))
        assertEquals("ID 6MDQ1KXA", rows1.last())
        assertTrue(LabelRenderer.printedRows(content, spec, "Box 2 of 2")!!.contains("Box 2 of 2"))
        assertTrue(tagPreview(content, spec, 1).vinFits)
        assertFalse(tagPreview(content, LabelSpec(30.0, 16.0, 300), 2).vinFits)
    }
}
