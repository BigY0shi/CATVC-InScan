package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import com.example.data.MoveResult
import com.example.data.ScanLookup
import com.example.data.ShopUnitEntity
import com.example.ui.inscan.InScanTheme
import com.example.ui.inscan.InType
import com.example.ui.inscan.Palette
import com.example.ui.scan.CameraIssue
import com.example.ui.scan.CardAction
import com.example.ui.scan.ReadKind
import com.example.ui.scan.ScanCard
import com.example.ui.scan.ScanMode
import com.example.ui.scan.ScanScreen
import com.example.ui.scan.ScanUi
import com.example.ui.scan.Torch
import com.example.ui.scan.cleanTyped
import com.example.ui.scan.lookUpLabel
import com.example.ui.scan.movedCard
import com.example.ui.scan.scanCard
import com.example.ui.scan.typedCount
import com.example.ui.scan.typedLengthProblem
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

// The Scan screen against the locked mockup (prototypes/scan.html), every result-card row of the
// spec's table (2c). The camera can't run here, so the picture is a plain placeholder. Units, tags
// and VINs are test fixtures, never shipped.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w402dp-h874dp-xhdpi")
class ScanScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("America/Los_Angeles")
    private val today = LocalDate.of(2026, 11, 5) // a Thursday
    private val safe = PaddingValues(top = 62.dp, bottom = 34.dp)

    // A unit whose tag ID (the last 8 characters) reads as given.
    private fun unit(short: String, daysAgo: Long, year: Int, make: String, model: String, type: String, status: String,
                     color: String = "", vin: String? = null, price: Long = 0): ShopUnitEntity {
        val at = ZonedDateTime.of(today.minusDays(daysAgo).atTime(10, 0), zone).toInstant().toEpochMilli()
        return ShopUnitEntity(id = ItemId.new(at).take(18) + short, vin = vin, vinCheck = "", year = year, make = make, model = model,
            subModel = "", color = color, unitType = type, priceCents = price, status = status, notes = "", version = 1, updatedAt = "", updatedBy = "")
    }

    private val rancher = unit("3HZW8RTB", 2, 2026, "Honda", "FourTrax Rancher 4x4", "ATV", "INCOMING", "Olive", "1HFTE35X5R4512094", 989_900)
    private val crf = unit("9QF3M2ZD", 22, 2025, "Honda", "CRF250F", "Dirt Bike", "IN_STOCK", "Red", "JH2ME1034RK733158", 749_900)
    private val vin = "4XARR45D9R8104217"

    private fun tagOf(u: ShopUnitEntity, color: String = u.color) = LabelPayload.encode(LabelContent(u.id, u.make, u.model, u.subModel,
        u.year.toString(), color, u.unitType, u.vin.orEmpty()))

    private fun card(lookup: ScanLookup, code: String, read: ReadKind, expect: String? = null) = scanCard(lookup, code, read, today, zone, expect)

    private fun shoot(name: String, state: ScanUi) {
        compose.setContent {
            InScanTheme {
                ScanScreen(
                    state, safe,
                    preview = {
                        Box(Modifier.fillMaxSize().background(Color(0xFF2B2C2F))) {
                            Text("Camera picture (placeholder)", style = InType.meta, color = Palette.text2, modifier = Modifier.padding(start = 16.dp, top = 128.dp))
                        }
                    },
                    onMode = {}, onCancel = {}, onLight = {}, onTyped = {}, onLookUp = {}, onNoTag = {}, onPermission = {}, onCard = {}, onOpenUnit = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/scan-$name.png")
    }

    private val base = ScanUi(firstName = "Dale", torch = Torch.OFF)
    private fun withCard(c: ScanCard, typed: String = "") = base.copy(card = c, typed = typed)

    // ---- Camera states ----
    @Test fun camera() = shoot("camera", base)
    @Test fun hint() = shoot("hint", base.copy(hint = true))
    @Test fun hintNoTorch() = shoot("hint-no-torch", base.copy(hint = true, torch = Torch.NONE))
    @Test fun lightOn() = shoot("light-on", base.copy(torch = Torch.ON))
    @Test fun permission() = shoot("perm", base.copy(camera = CameraIssue.NOT_ALLOWED))
    @Test fun permissionSettings() = shoot("perm-settings", base.copy(camera = CameraIssue.NOT_ALLOWED_SETTINGS))
    @Test fun couldntStart() = shoot("couldnt-start", base.copy(camera = CameraIssue.COULDNT_START))
    @Test fun typeIt() = shoot("type", base.copy(mode = ScanMode.TYPE, typed = "7KD2QX"))
    @Test fun typeWrongLength() = shoot("type-wrong-length", base.copy(mode = ScanMode.TYPE, typed = "7KD2QX4M12", typedWrongLength = 10))

    // ---- Result cards ----
    @Test fun found() = shoot("found", withCard(card(ScanLookup.Found(crf, tagOf(crf), false, true), tagOf(crf), ReadKind.TAG)))
    @Test fun incoming() = shoot("incoming", withCard(card(ScanLookup.Found(rancher, tagOf(rancher), false, true), tagOf(rancher), ReadKind.TAG)))
    @Test fun tagOutOfDate() = shoot("tag-out-of-date", withCard(card(ScanLookup.Found(rancher, tagOf(rancher, "Green"), true, true), tagOf(rancher, "Green"), ReadKind.TAG)))
    @Test fun differentTag() = shoot("different-tag", withCard(card(ScanLookup.Found(rancher, tagOf(rancher), false, true), tagOf(rancher), ReadKind.TAG, expect = crf.id)))
    @Test fun moved() = shoot("moved", withCard(movedCard(MoveResult.Moved(rancher.copy(status = "IN_STOCK"), sent = true, stillIncoming = 5), today, zone)))
    @Test fun movedOffline() = shoot("moved-offline", withCard(movedCard(MoveResult.Moved(rancher.copy(status = "IN_STOCK"), sent = false, stillIncoming = 5), today, zone)))
    @Test fun typedIncoming() = shoot("typed-incoming", withCard(card(ScanLookup.Found(rancher, null, false, true), "3HZW8RTB", ReadKind.TYPED), typed = "3HZW8RTB"))
    @Test fun vinNotFound() = shoot("notfound", withCard(card(ScanLookup.NotFound, vin, ReadKind.BARCODE)))
    @Test fun vinNotFoundBadCheck() = shoot("notfound-check-fails", withCard(card(ScanLookup.NotFound, "4XARR45D1R8104217", ReadKind.BARCODE)))
    @Test fun vinOffline() = shoot("offline", withCard(card(ScanLookup.Unreachable, vin, ReadKind.BARCODE)))
    @Test fun otherBarcode() = shoot("other-barcode", withCard(card(ScanLookup.NotFound, "0012345678905", ReadKind.BARCODE)))
    @Test fun tagOnly() = shoot("label", withCard(card(ScanLookup.TagOnly(LabelContent(ItemId.new().take(18) + "6MDQ1KXA", "Polaris", "RZR XP 1000", "", "2026", "Gray", "UTV")),
        "CATV1;id=x", ReadKind.TAG)))
    @Test fun typedNotFound() = shoot("typed-notfound", withCard(card(ScanLookup.NotFound, "7KD2QX4M", ReadKind.TYPED), typed = "7KD2QX4M"))
    @Test fun typedNotFoundOffline() = shoot("typed-notfound-offline", withCard(card(ScanLookup.Unreachable, "7KD2QX4M", ReadKind.TYPED), typed = "7KD2QX4M"))
    @Test fun clash() = shoot("clash", withCard(card(ScanLookup.ShortIdClash, "7KD2QX4M", ReadKind.TYPED), typed = "7KD2QX4M"))
    @Test fun signInAgain() = shoot("sign-in-again", withCard(card(ScanLookup.NeedsSignIn, tagOf(rancher), ReadKind.TAG)))
    @Test fun moving() = shoot("incoming-moving", withCard(card(ScanLookup.Found(rancher, tagOf(rancher), false, true), tagOf(rancher), ReadKind.TAG)).copy(moving = true))

    @Config(qualifiers = "w402dp-h874dp-xhdpi", fontScale = 2.0f)
    @Test fun incomingFont2() = shoot("incoming-font2", withCard(card(ScanLookup.Found(rancher, tagOf(rancher), false, true), tagOf(rancher), ReadKind.TAG)))

    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    @Test fun incomingSmallPhone() = shoot("incoming-360x640", withCard(card(ScanLookup.Found(rancher, tagOf(rancher), false, true), tagOf(rancher), ReadKind.TAG)))

    // ---- Behaviour (spec section 8) ----

    @Test
    fun `typing is cleaned before it counts`() {
        assertEquals("3HZW8RTB", cleanTyped("ID 3hzw8rtb"))
        assertEquals("70K21Q11", cleanTyped("7OK2IQLL"))
        assertEquals("6 of 8", typedCount("7KD2QX"))
        assertEquals("12 of 17", typedCount("4XARR45D9R81"))
        assertEquals("Look up tag ID", lookUpLabel("id 7kd2qx4m"))
        assertEquals("Look up VIN", lookUpLabel(vin))
        assertEquals("Look up", lookUpLabel("7KD"))
        assertEquals(10, typedLengthProblem("7KD2QX4M12"))
        assertNull(typedLengthProblem(tagOf(rancher)))
        // A whole tag from a Bluetooth scanner keeps its case so it still decodes.
        assertEquals(tagOf(rancher), cleanTyped(tagOf(rancher)))
    }

    @Test
    fun `a typed lookup never offers Move to In stock`() {
        val c = card(ScanLookup.Found(rancher, null, false, true), "3HZW8RTB", ReadKind.TYPED)
        assertFalse(c.buttons.any { it.action == CardAction.MOVE })
        assertEquals(listOf("Type another"), c.buttons.map { it.label })
        assertEquals("Scan the tag to move it to In stock.", c.note?.text)
    }

    @Test
    fun `only a read tag on an Incoming unit offers Move to In stock`() {
        val c = card(ScanLookup.Found(rancher, tagOf(rancher), false, true), tagOf(rancher), ReadKind.TAG)
        assertEquals(listOf(CardAction.SCAN_AGAIN, CardAction.MOVE), c.buttons.map { it.action })
        assertEquals(tagOf(rancher), c.tagPayload)
        assertEquals("The box tag reads. Check it matches the box: Honda FourTrax Rancher 4x4, Olive, VIN ending 512094.", c.note?.text)
        val stale = card(ScanLookup.Found(rancher, tagOf(rancher, "Green"), true, true), tagOf(rancher, "Green"), ReadKind.TAG)
        assertFalse(stale.buttons.any { it.action == CardAction.MOVE })
        val inStock = card(ScanLookup.Found(crf, tagOf(crf), false, true), tagOf(crf), ReadKind.TAG)
        assertFalse(inStock.buttons.any { it.action == CardAction.MOVE })
    }

    @Test
    fun `a maker barcode never offers Check in`() {
        val c = card(ScanLookup.NotFound, "0012345678905", ReadKind.BARCODE)
        assertEquals("This barcode can't identify a unit", c.title)
        assertTrue(c.buttons.none { it.action == CardAction.CHECK_IN || it.action == CardAction.CHECK_IN_ANYWAY })
        assertEquals(CardAction.CHECK_IN, card(ScanLookup.NotFound, vin, ReadKind.BARCODE).buttons.last().action)
    }

    @Test
    fun `cards carry no names`() {
        val c = card(ScanLookup.Found(crf, tagOf(crf), false, true), tagOf(crf), ReadKind.TAG)
        assertEquals("Two wheels, checked in Oct 14", c.record?.meta)
    }
}
