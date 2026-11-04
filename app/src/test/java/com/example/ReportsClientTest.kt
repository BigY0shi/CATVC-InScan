package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import com.countryatv.core.reports.ExportRequest
import com.countryatv.core.reports.ReportKeys
import com.example.shop.NewUnit
import com.example.shop.Permissions
import com.example.shop.RangeQuery
import com.example.shop.ShopClient
import com.example.shop.ShopResult
import com.example.shop.TagConfirm
import com.example.shop.TagPrinted
import com.example.ui.reports.Presets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

// The Reports and My history calls against the real shop server (TestShopServer): a report loads,
// every drill-down list has exactly its row's count, the CSV downloads, and permissions hold.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReportsClientTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var shop: TestShopServer

    @Before fun start() {
        shop = TestShopServer()
        shop.auth.createEmployee("Dale", "employee", "1234")
        shop.auth.createEmployee("Mia", "management", "5678")
    }

    @After fun stop() = shop.close()

    private fun phone(name: String, who: String, pin: String): ShopClient = runBlocking {
        val c = ShopClient(context, allowPlainHttp = true, prefsName = "reports_$name")
        assertTrue(c.enroll(shop.baseUrl, shop.auth.newSetupCode("test"), "Test phone") is ShopResult.Ok)
        assertTrue(c.signIn(who, pin) is ShopResult.Ok)
        c
    }

    private fun <T> ok(r: ShopResult<T>): T {
        assertTrue("expected Ok, got $r", r is ShopResult.Ok)
        return (r as ShopResult.Ok).value
    }

    private fun checkIn(c: ShopClient, make: String, type: String, confirm: Boolean) = runBlocking {
        val id = ItemId.new()
        ok(c.createUnit(NewUnit(id = id, make = make, model = "Test", unitType = type, priceCents = 500_000)))
        val payload = LabelPayload.encode(LabelContent(id = id, make = make, model = "Test"))
        ok(c.recordTag(id, TagPrinted(ItemId.new(), payload, "share", Instant.now().toString())))
        if (confirm) ok(c.confirmTag(id, TagConfirm(payload)))
        id
    }

    @Test
    fun `sign-in stores the permissions the shop system sent`() {
        val mia = phone("mia", "Mia", "5678")
        assertTrue(mia.state.value.can(Permissions.SEE_REPORTS))
        assertTrue(mia.state.value.can(Permissions.EXPORT_REPORTS))
        assertTrue(mia.state.value.employeeId!!.isNotBlank())
        val dale = phone("dale", "Dale", "1234")
        assertFalse(dale.state.value.can(Permissions.SEE_REPORTS))
        assertTrue(dale.state.value.can(Permissions.SEE_OWN_HISTORY))
    }

    @Test
    fun `a report loads and every drill-down list has exactly its row's count`() = runBlocking {
        val dale = phone("dale", "Dale", "1234")
        checkIn(dale, "Polaris", "UTV", confirm = false)
        checkIn(dale, "Polaris", "ATV", confirm = true)
        checkIn(dale, "Honda", "Dirt Bike", confirm = false)
        val mia = phone("mia", "Mia", "5678")
        checkIn(mia, "Yamaha", "Dirt Bike", confirm = true)

        val summary = ok(mia.reportSummary())
        assertEquals(4L, summary.figures.first { it.report == ReportKeys.CHECK_INS }.tile.value)

        val report = ok(mia.report(ReportKeys.CHECK_INS, RangeQuery.preset(Presets.THIS_WEEK)))
        assertEquals(4L, report.tiles[0].value)
        var checked = 0
        for (b in report.breakdowns) for (row in b.rows) {
            val f = row.drill ?: continue
            val page = ok(mia.unitQuery(f))
            assertEquals("${b.key} ${row.label}", row.count, page.total)
            assertEquals(page.total.toInt(), page.items.size)
            checked++
        }
        assertTrue("checked $checked rows", checked >= 6)
        // Paging: a page of 1 still counts all of them, and the cursor reaches the rest.
        val person = report.breakdowns.first { it.key == "person" }.rows.first { it.label == "Dale" }
        val first = ok(mia.unitQuery(person.drill!!, limit = 1))
        assertEquals(3L, first.total)
        val second = ok(mia.unitQuery(person.drill!!, first.next, 1))
        assertEquals(1, second.items.size)
    }

    @Test
    fun `employees are refused reports, and history is their own`() = runBlocking {
        val dale = phone("dale", "Dale", "1234")
        checkIn(dale, "Honda", "Dirt Bike", confirm = true)
        val refused = dale.report(ReportKeys.CHECK_INS, RangeQuery.preset(Presets.THIS_WEEK))
        assertTrue(refused is ShopResult.Refused && refused.code == "NOT_ALLOWED")
        val mine = ok(dale.history(null, RangeQuery.preset(Presets.THIS_WEEK)))
        assertEquals(1L, mine.totals!!.checkIns)
        assertEquals(1L, mine.totals!!.movedToInStock)
        val mia = phone("mia", "Mia", "5678")
        val work = ok(mia.report(ReportKeys.WORK_BY_PERSON, RangeQuery.preset(Presets.THIS_WEEK)))
        val row = work.breakdowns.first().rows.first { it.label == "Dale" }
        val his = ok(mia.history(row.personId, RangeQuery.preset(Presets.THIS_WEEK)))
        assertEquals("Dale", his.person!!.displayName)
        assertEquals(row.figures.first { it.key == "checkIns" }.value, his.totals!!.checkIns)
    }

    @Test
    fun `the check-ins CSV downloads into the exports folder`() = runBlocking {
        val mia = phone("mia", "Mia", "5678")
        checkIn(mia, "Yamaha", "Dirt Bike", confirm = false)
        val dir = File(context.cacheDir, "exports-test")
        val file = ok(mia.exportCsv(ReportKeys.CHECK_INS, ExportRequest(preset = Presets.THIS_WEEK), dir))
        assertTrue(file.name.startsWith("inscan-check-ins-") && file.name.endsWith(".csv"))
        val text = file.readText()
        assertTrue(text.contains("Yamaha"))
        assertEquals(2, text.trimEnd().split("\r\n").size)
        val notAvailable = mia.exportCsv(ReportKeys.SOLD, ExportRequest(preset = Presets.THIS_WEEK), dir)
        assertTrue(notAvailable is ShopResult.Refused)
    }
}
