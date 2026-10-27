package com.example

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.InventoryListing
import com.example.data.InventoryViewModel
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
}
