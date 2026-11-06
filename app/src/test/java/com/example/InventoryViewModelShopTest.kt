package com.example

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.InventoryListing
import com.example.data.InventoryViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// The screens' own entry points (the ViewModel) against the real shop server.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InventoryViewModelShopTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var shop: TestShopServer
    private lateinit var vm: InventoryViewModel

    @Before
    fun setUp() {
        AppDatabase.resetForTests()
        shop = TestShopServer()
        shop.auth.createEmployee("Sam", "employee", "1234")
        vm = InventoryViewModel(app)
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
        shop.close()
    }

    // Lets the app's background work run until [check] holds, or fails the test.
    private fun waitFor(what: String, check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!check()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for: $what")
            Thread.sleep(20)
        }
    }

    private fun connectAndSignIn() {
        runBlocking {
            assertNull(vm.connect(shop.baseUrl, shop.auth.newSetupCode("test"), "Front counter"))
            assertNull(vm.signIn("Sam", "1234"))
        }
        assertEquals("Sam", vm.connection.value.signedInAs)
    }

    private fun scan(code: String) {
        vm.onBarcodeScanned(code)
        waitFor("scan lookup of $code") {
            vm.scannedBarcode.value == code &&
                (vm.scannedItem.value != null || vm.labelOnly.value != null || vm.lookupNote.value != null || vm.vehicleTypeInput.value == "Other")
        }
    }

    @Test
    fun `a wrong setup code or PIN is refused in plain words`() {
        runBlocking {
            val badCode = vm.connect(shop.baseUrl, "NOT-A-CODE", "Front counter")
            assertNotNull(badCode)
            assertFalse(vm.connection.value.enrolled)
            assertNull(vm.connect(shop.baseUrl, shop.auth.newSetupCode("test"), "Front counter"))
            assertNotNull(vm.signIn("Sam", "0000"))
            assertNull(vm.connection.value.signedInAs)
        }
    }

    @Test
    fun `logging, labelling, editing and selling an item from the form lands in the shop system`() {
        connectAndSignIn()

        scan("MAN-100")
        vm.makeInput.value = "Honda"
        vm.modelInput.value = "CRF450R"
        vm.yearInput.value = "2025"
        vm.vehicleTypeInput.value = "Dirt Bike"
        vm.priceInput.value = "8499.00"
        // The 9th character should be X: this VIN fails the check digit.
        vm.vinInput.value = "1M8GDM9A1KP042788"
        assertFalse(vm.addCurrentToInventoryListing())
        assertNotNull(vm.formError.value)
        vm.vinMatchesFrame.value = true
        assertTrue(vm.addCurrentToInventoryListing())

        // A new item arrives Incoming and its label sheet opens.
        waitFor("item to reach the shop system as Incoming") {
            shop.value("SELECT status FROM units WHERE vin = ?", "1M8GDM9A1KP042788") == "INCOMING"
        }
        val id = shop.value("SELECT id FROM units")!!
        assertEquals("849900", shop.value("SELECT price_cents FROM units WHERE id = ?", id))
        assertNotNull(shop.value("SELECT vin_frame_verified_by FROM units WHERE id = ?", id))
        waitFor("label sheet to open") { vm.labelItem.value?.id == id }

        // Shared to the printer's app, then the label on the box is scanned: now In Stock.
        val unit = vm.labelItem.value!!
        val payload = com.countryatv.core.LabelPayload.encode(vm.labelContentFor(unit))
        vm.recordLabelShared(unit, reprintReason = "")
        vm.closeLabelSheet()
        waitFor("label print to be recorded") { shop.count("SELECT COUNT(*) FROM tags WHERE unit_id = ? AND printed_via = 'shared'", id) == 1 }
        assertEquals("INCOMING", shop.value("SELECT status FROM units WHERE id = ?", id))
        scan(payload)
        assertEquals(payload, vm.confirmablePayload.value)
        vm.confirmScannedLabel()
        waitFor("label check to move the item to In Stock") { shop.value("SELECT status FROM units WHERE id = ?", id) == "IN_STOCK" }
        assertNotNull(shop.value("SELECT confirmed_at FROM tags WHERE unit_id = ?", id))
        // Scanning the label again offers no second confirm.
        scan(payload)
        assertNull(vm.confirmablePayload.value)
        // The 8-character ID printed on the label finds it too.
        scan(id.takeLast(8).lowercase())
        assertEquals(id, vm.scannedItem.value?.id)

        scan(id)
        assertEquals(id, vm.scannedItem.value?.id)
        assertEquals("Honda", vm.makeInput.value)
        vm.colorInput.value = "Red"
        assertTrue(vm.addCurrentToInventoryListing())
        waitFor("colour change to reach the shop system") { shop.value("SELECT color FROM units WHERE id = ?", id) == "Red" }
        assertEquals(1, shop.count("SELECT COUNT(*) FROM units"))

        scan(id)
        assertTrue(vm.prepareSaleFromCurrentScan())
        vm.completeSale()
        waitFor("sale to reach the shop system") { shop.value("SELECT status FROM units WHERE id = ?", id) == "SOLD" }
        assertEquals(1, shop.count("SELECT COUNT(*) FROM units"))
    }

    @Test
    fun `old items upload once, keep their status, and items with gaps stay on the phone`() {
        val dao = AppDatabase.getDatabase(app).barcodeDao()
        runBlocking {
            dao.insertListing(
                InventoryListing(barcode = "MAN-1", make = "Yamaha", model = "YZ250F", year = "2023", color = "Blue",
                    vehicleType = "Dirt Bike", vin = "", price = 7299.0, notes = "", status = "Sold")
            )
            dao.insertListing(
                InventoryListing(barcode = "MAN-2", make = "Polaris", model = "RZR", year = "", color = "",
                    vehicleType = "Side-by-Side (UTV)", vin = "", price = 0.0, notes = "", status = "In Stock")
            )
        }
        connectAndSignIn()
        waitFor("old items to be listed") { vm.legacyRows.value.size == 2 }

        vm.uploadLegacyItems()
        waitFor("old sold item to reach the shop system") {
            shop.value("SELECT status FROM units WHERE make = 'Yamaha'") == "SOLD"
        }
        waitFor("upload to be recorded") { vm.legacyRows.value.count { it.queuedAs != null } == 1 }
        vm.uploadLegacyItems()
        vm.syncSoon()
        waitFor("outbox to empty") { vm.outbox.value.isEmpty() }
        assertEquals(1, shop.count("SELECT COUNT(*) FROM units"))
        assertEquals(0, shop.count("SELECT COUNT(*) FROM units WHERE make = 'Polaris'"))
        // Nothing is deleted from the phone's old table.
        assertEquals(2, vm.legacyRows.value.size)

        // The item with gaps is completed in the form, then counts as uploaded.
        vm.openLegacyInForm(vm.legacyRows.value.first { it.old.make == "Polaris" }.old)
        vm.priceInput.value = "18999"
        assertTrue(vm.addCurrentToInventoryListing())
        waitFor("completed old item to reach the shop system") {
            shop.value("SELECT status FROM units WHERE make = 'Polaris'") == "IN_STOCK"
        }
        waitFor("both old items recorded as uploaded") { vm.legacyRows.value.all { it.queuedAs != null } }
    }

    @Test
    fun `an employee can't remove an item that is in the shop system`() {
        connectAndSignIn()
        scan("MAN-5")
        vm.makeInput.value = "Honda"
        vm.modelInput.value = "Pioneer"
        vm.vehicleTypeInput.value = "Side-by-Side (UTV)"
        vm.priceInput.value = "15000"
        assertTrue(vm.addCurrentToInventoryListing())
        waitFor("item to reach the shop system") { shop.count("SELECT COUNT(*) FROM units WHERE status = 'INCOMING'") == 1 }
        val local = runBlocking { AppDatabase.getDatabase(app).shopDao().allUnitsOnce().single() }
        val listing = InventoryListing(id = local.localId, barcode = local.id, make = "Honda", model = "Pioneer", year = "",
            color = "", vehicleType = "Side-by-Side (UTV)", vin = "", price = 15000.0, notes = "")
        // Once this phone's copy shows the item reached the shop system, only the owner can remove it.
        waitFor("removal to need the owner") { vm.removalFor(listing) == InventoryViewModel.Removal.NEEDS_OWNER }
    }

    @Test
    fun `a label for an item this phone doesn't know is shown, marked as possibly out of date, and can't be logged again`() {
        connectAndSignIn()
        val stranger = com.countryatv.core.LabelContent(id = com.countryatv.core.ItemId.new(), make = "Yamaha", model = "Grizzly")
        val payload = com.countryatv.core.LabelPayload.encode(stranger)
        scan(payload)
        assertEquals(stranger, vm.labelOnly.value)
        assertNull(vm.scannedItem.value)
        vm.priceInput.value = "9999"
        assertFalse(vm.addCurrentToInventoryListing())
        assertNotNull(vm.formError.value)
        assertEquals(0, shop.count("SELECT COUNT(*) FROM units"))
    }

    // Runs a suspend call off the main thread while letting the main looper run, so the view
    // model's own background rounds (which resume on the main thread) can finish meanwhile.
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun <T> onIo(block: suspend () -> T): T {
        val d = kotlinx.coroutines.GlobalScope.async(kotlinx.coroutines.Dispatchers.IO) { block() }
        waitFor("call to finish") { d.isCompleted }
        return runBlocking { d.await() }
    }

    // The rebuilt Scan screen and sign-in gate (scan-pin-ui-spec.md sections 2 and 3) against the real server.
    @Test
    fun `scan lookups find a unit from its tag, its typed ID and its VIN, and Move to In stock is queued once`() {
        assertNull(onIo { vm.connect(shop.baseUrl, shop.auth.newSetupCode("test"), "Counter phone") })
        assertEquals("Counter phone", vm.connection.value.phoneName)
        val wrong = onIo { vm.trySignIn("Sam", "0000") }
        assertTrue(wrong is com.example.shop.ShopResult.Refused && wrong.code == "WRONG_PIN")
        assertTrue(onIo { vm.trySignIn("Sam", "1234") } is com.example.shop.ShopResult.Ok)
        scan("MAN-200")
        vm.makeInput.value = "Polaris"
        vm.modelInput.value = "RZR XP 1000"
        vm.yearInput.value = "2026"
        vm.colorInput.value = "Gray"
        vm.vehicleTypeInput.value = "Side-by-Side (UTV)"
        vm.priceInput.value = "21999"
        vm.vinInput.value = "4XARR45D9R8104217"
        vm.vinMatchesFrame.value = true
        assertTrue(vm.addCurrentToInventoryListing())
        waitFor("unit to reach the shop system") { shop.value("SELECT status FROM units") == "INCOMING" && vm.outbox.value.isEmpty() }
        val id = shop.value("SELECT id FROM units")!!
        val unit = onIo { AppDatabase.getDatabase(app).shopDao().unit(id)!! }
        val payload = com.countryatv.core.LabelPayload.encode(vm.labelContentFor(unit))
        vm.recordLabelShared(unit, reprintReason = "")
        waitFor("label print to be recorded") { shop.count("SELECT COUNT(*) FROM tags WHERE unit_id = ?", id) == 1 && vm.outbox.value.isEmpty() }

        val byTag = onIo { vm.lookUpForScan(payload) } as com.example.data.ScanLookup.Found
        assertEquals(payload, byTag.tagPayload)
        assertFalse(byTag.tagOutOfDate)
        assertTrue(byTag.checkedOnline)
        // A tag printed before an edit no longer matches the unit.
        val old = com.countryatv.core.LabelPayload.encode(vm.labelContentFor(unit.copy(color = "Green")))
        assertTrue((onIo { vm.lookUpForScan(old) } as com.example.data.ScanLookup.Found).tagOutOfDate)
        // Typed: the 8 characters with O, I, L read as 0, 1, 1 still find it, but never carry a tag.
        val typed = onIo { vm.lookUpForScan(com.example.ui.scan.cleanTyped("id " + id.takeLast(8).lowercase())) } as com.example.data.ScanLookup.Found
        assertEquals(id, typed.unit.id)
        assertNull(typed.tagPayload)
        assertEquals(id, (onIo { vm.lookUpForScan("4XARR45D9R8104217") } as com.example.data.ScanLookup.Found).unit.id)
        assertEquals(com.example.data.ScanLookup.NotFound, onIo { vm.lookUpForScan("1M8GDM9AXKP042788") })
        assertEquals(com.example.data.ScanLookup.NotFound, onIo { vm.lookUpForScan("0012345678905") })

        // Two taps on Move to In stock: one confirm goes out.
        val first = onIo { vm.moveToInStock(id, payload) } as com.example.data.MoveResult.Moved
        assertTrue(first.sent)
        assertEquals("IN_STOCK", first.unit.status)
        assertEquals(0, first.stillIncoming)
        onIo { vm.moveToInStock(id, payload) }
        assertEquals("IN_STOCK", shop.value("SELECT status FROM units WHERE id = ?", id))
        assertEquals(1, shop.count("SELECT COUNT(*) FROM tags WHERE unit_id = ? AND confirmed_at IS NOT NULL", id))
    }
}
