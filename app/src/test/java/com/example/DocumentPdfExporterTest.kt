package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DealerProfile
import com.example.data.InventoryListing
import com.example.utils.BuyerInfo
import com.example.utils.DocumentKind
import com.example.utils.DocumentPdfExporter
import com.example.utils.DocumentResult
import com.example.utils.FinanceTerms
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentPdfExporterTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val dealer = DealerProfile(legalName = "Test Dealer LLC", dealerNumber = "DLR-1", address = "1 Main St")
  private val listing = InventoryListing(
    barcode = "B1", make = "Make", model = "Model", year = "2025", color = "Red",
    vehicleType = "ATV", vin = "VIN123", price = 8499.0, notes = "", status = "In Stock"
  )
  private val buyer = BuyerInfo(name = "Pat Buyer", phone = "541-000-0000", address = "2 Oak St")

  @Test
  fun `refuses without dealer details and names what is missing`() {
    val result = DocumentPdfExporter.generate(context, DocumentKind.BILL_OF_SALE, DealerProfile(), listing, buyer, "2026-10-21")
    assertTrue(result is DocumentResult.Refused)
    val msg = (result as DocumentResult.Refused).message
    assertTrue(msg, msg.contains("dealership legal name"))
    assertTrue(msg, msg.contains("Oregon dealer number"))
  }

  @Test
  fun `refuses a blank buyer instead of printing N-A`() {
    val result = DocumentPdfExporter.generate(context, DocumentKind.BILL_OF_SALE, dealer, listing, BuyerInfo("", "", ""), "2026-10-21")
    val msg = (result as DocumentResult.Refused).message
    assertTrue(msg, msg.contains("buyer's full name"))
    assertTrue(msg, msg.contains("buyer's address"))
    assertTrue(msg, msg.contains("buyer's phone"))
  }

  @Test
  fun `refuses finance summary without typed terms`() {
    val result = DocumentPdfExporter.generate(context, DocumentKind.FINANCE_SUMMARY, dealer, listing, buyer, "2026-10-21", FinanceTerms("", "", ""))
    val msg = (result as DocumentResult.Refused).message
    assertTrue(msg, msg.contains("down payment") && msg.contains("interest rate") && msg.contains("loan term"))
  }

  // Writing the PDF itself needs Android's native PDF code, which Robolectric lacks.
  // That check lives in androidTest/DocumentPdfExporterDeviceTest and runs on a phone or emulator.
}
