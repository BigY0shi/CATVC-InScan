package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.DealerProfile
import com.example.data.InventoryListing
import com.example.utils.BuyerInfo
import com.example.utils.DocumentKind
import com.example.utils.DocumentPdfExporter
import com.example.utils.DocumentResult
import com.example.utils.FinanceTerms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Runs on a real phone or emulator: ./gradlew connectedDebugAndroidTest
@RunWith(AndroidJUnit4::class)
class DocumentPdfExporterDeviceTest {

  @Test
  fun writesARealPdfIntoAppPrivateStorage() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val dealer = DealerProfile(legalName = "Test Dealer LLC", dealerNumber = "DLR-1", address = "1 Main St")
    val listing = InventoryListing(
      barcode = "B1", make = "Make", model = "Model", year = "2025", color = "Red",
      vehicleType = "ATV", vin = "VIN123", price = 8499.0, notes = "", status = "In Stock"
    )
    val buyer = BuyerInfo(name = "Pat Buyer", phone = "541-000-0000", address = "2 Oak St")

    DocumentKind.entries.forEach { kind ->
      val result = DocumentPdfExporter.generate(context, kind, dealer, listing, buyer, "2026-10-21", FinanceTerms("1000", "6", "36"))
      assertTrue("$kind: $result", result is DocumentResult.Ready)
      val file = (result as DocumentResult.Ready).file
      assertTrue(file.exists() && file.length() > 0)
      assertEquals("%PDF", file.readBytes().copyOfRange(0, 4).toString(Charsets.US_ASCII))
      assertTrue(file.canonicalPath.startsWith(context.filesDir.canonicalPath))
    }
  }
}
