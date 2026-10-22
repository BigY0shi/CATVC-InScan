package com.example

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.InventoryViewModel
import com.example.ui.MainScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// Logging an item opens its label; sharing hands a real PNG to the printer's app and records the print.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LabelSheetUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var shop: TestShopServer

    @Before
    fun setUp() {
        AppDatabase.resetForTests()
        shop = TestShopServer()
        shop.auth.createEmployee("Sam", "employee", "1234")
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
        shop.close()
    }

    // Lets the app's background work (which resumes on the main looper) run until [check] holds.
    private fun waitFor(what: String, check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!check()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(20)
        }
    }

    @Test
    fun `sharing a label sends a PNG of it through the app's file provider and records the print`() {
        val vm = InventoryViewModel(app)
        runBlocking {
            vm.connect(shop.baseUrl, shop.auth.newSetupCode("test"), "Front counter")
            vm.signIn("Sam", "1234")
        }
        compose.setContent { MainScreen(viewModel = vm, autoSync = false) }

        vm.onBarcodeScanned("MAN-7")
        waitFor("scan lookup") { vm.vehicleTypeInput.value == "Other" }
        vm.makeInput.value = "Honda"
        vm.modelInput.value = "CRF450R"
        vm.vehicleTypeInput.value = "Dirt Bike"
        vm.priceInput.value = "8499"
        assertTrue(vm.addCurrentToInventoryListing())

        waitFor("label sheet") { vm.labelItem.value != null }
        compose.waitForIdle()
        compose.onNodeWithTag("label_preview").assertExists()
        compose.onNodeWithTag("label_share_button").performClick()
        compose.waitForIdle()

        val started = shadowOf(app).nextStartedActivity ?: throw AssertionError("No share sheet was opened")
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        @Suppress("DEPRECATION")
        val send = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/png", send.type)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("com.example.fileprovider", uri.authority)
        val id = vm.outbox.value.firstOrNull()?.itemId ?: runBlocking { AppDatabase.getDatabase(app).shopDao().allUnitsOnce().single().id }
        val file = File(File(app.cacheDir, "labels"), "tag-$id.png")
        assertTrue("label image was saved", file.length() > 0)

        waitFor("print to be recorded in the shop system") {
            shop.count("SELECT COUNT(*) FROM tags WHERE unit_id = ? AND printed_via = 'shared'", id) == 1
        }
        assertNull(vm.labelItem.value)
        assertEquals("INCOMING", shop.value("SELECT status FROM units WHERE id = ?", id))
    }
}
