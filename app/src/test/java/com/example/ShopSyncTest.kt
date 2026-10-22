package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.countryatv.core.ItemId
import com.countryatv.core.UnitStatus
import com.example.data.AppDatabase
import com.example.data.OutboxEntry
import com.example.shop.ItemEdits
import com.example.shop.NewUnit
import com.example.shop.ShopClient
import com.example.shop.ShopResult
import com.example.shop.ShopSync
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

// Phones talking to the real shop server (TestShopServer). Every check on the result reads the
// server's own database, not what the app says happened.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShopSyncTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var shop: TestShopServer
    private val phoneDbs = mutableListOf<AppDatabase>()

    // A test phone: its own local database, its own stored sign-in, and a switch for losing signal.
    private inner class Phone(name: String) {
        var offline = false
        // When set, the next request reaches the server but the answer is lost on the way back.
        var loseNextAnswer = false
        private val http = OkHttpClient.Builder().addInterceptor { chain ->
            if (offline) throw IOException("no signal")
            val response = chain.proceed(chain.request())
            if (loseNextAnswer) {
                loseNextAnswer = false
                response.close()
                throw IOException("connection dropped")
            }
            response
        }.build()
        val db: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            .also { phoneDbs += it }
        val client = ShopClient(context, http, allowPlainHttp = true, prefsName = "test_$name")
        val sync = ShopSync(db.shopDao(), client)

        fun signIn(employee: String, pin: String) = runBlocking {
            assertTrue(client.enroll(shop.baseUrl, shop.auth.newSetupCode("test"), "Test phone") is ShopResult.Ok)
            assertTrue(client.signIn(employee, pin) is ShopResult.Ok)
        }
    }

    @Before
    fun startServer() {
        shop = TestShopServer()
        shop.auth.createEmployee("Sam", "employee", "1234")
        shop.auth.createEmployee("Jo", "employee", "5678")
    }

    @After
    fun stopServer() {
        phoneDbs.forEach { it.close() }
        shop.close()
    }

    private fun serverCount(sql: String, vararg args: String) = shop.count(sql, *args)
    private fun serverValue(sql: String, vararg args: String) = shop.value(sql, *args)

    private fun bike(vin: String? = null, priceCents: Long = 849_900, make: String = "Honda") = NewUnit(
        id = ItemId.new(), make = make, model = "CRF450R", year = 2025, color = "Red",
        unitType = "Dirt Bike", vin = vin, priceCents = priceCents
    )

    @Test
    fun `items logged with no signal wait on the phone and arrive once it reconnects`() = runBlocking {
        val phone = Phone("a").apply { signIn("Sam", "1234") }
        phone.offline = true
        val unit = bike()
        phone.sync.logNew(unit)
        phone.sync.logStatus(unit.id, UnitStatus.IN_STOCK)

        val offlineRound = phone.sync.syncNow()
        assertNotNull(offlineRound.stoppedBecause)
        assertEquals(2, offlineRound.stillWaiting)
        assertEquals(0, serverCount("SELECT COUNT(*) FROM units"))
        assertEquals("IN_STOCK", phone.db.shopDao().unit(unit.id)!!.status)

        phone.offline = false
        val round = phone.sync.syncNow()
        assertNull(round.stoppedBecause)
        assertEquals(2, round.sent)
        assertEquals(0, round.stillWaiting)
        assertEquals("IN_STOCK", serverValue("SELECT status FROM units WHERE id = ?", unit.id))
        assertEquals(2, serverValue("SELECT version FROM units WHERE id = ?", unit.id)!!.toInt())
        val local = phone.db.shopDao().unit(unit.id)!!
        assertEquals(2, local.version)
        assertEquals("none", local.vinCheck)
    }

    @Test
    fun `a save whose answer was lost is sent again without making a second item`() = runBlocking {
        val phone = Phone("a").apply { signIn("Sam", "1234") }
        val unit = bike()
        phone.sync.logNew(unit)
        phone.loseNextAnswer = true
        assertNotNull(phone.sync.syncNow().stoppedBecause)
        assertEquals(1, serverCount("SELECT COUNT(*) FROM units WHERE id = ?", unit.id))

        val round = phone.sync.syncNow()
        assertNull(round.stoppedBecause)
        assertEquals(0, round.stillWaiting)
        assertEquals(1, serverCount("SELECT COUNT(*) FROM units"))
        assertEquals(1, serverCount("SELECT COUNT(*) FROM audit_log WHERE action = 'unit.create'"))
    }

    @Test
    fun `a refused change is kept with its reason, holds back later changes to that item only, and can be fixed`() = runBlocking {
        val phone = Phone("a").apply { signIn("Sam", "1234") }
        val bad = bike(priceCents = 0)
        val good = bike()
        phone.sync.logNew(bad)
        phone.sync.logStatus(bad.id, UnitStatus.IN_STOCK)
        phone.sync.logNew(good)

        val round = phone.sync.syncNow()
        assertEquals(1, round.refused)
        assertEquals(1, round.sent)
        assertEquals(0, serverCount("SELECT COUNT(*) FROM units WHERE id = ?", bad.id))
        assertEquals(1, serverCount("SELECT COUNT(*) FROM units WHERE id = ?", good.id))

        val waiting = phone.db.shopDao().outbox()
        assertEquals(2, waiting.size)
        val refused = waiting.first()
        assertEquals(OutboxEntry.STATE_NEEDS_ATTENTION, refused.state)
        assertEquals("price", refused.problemDetails)
        assertTrue(refused.problemMessage!!.isNotBlank())
        assertEquals(OutboxEntry.STATE_PENDING, waiting[1].state)

        phone.sync.retry(refused, correctedUnit = bad.copy(priceCents = 500_000))
        val fixed = phone.sync.syncNow()
        assertEquals(2, fixed.sent)
        assertEquals(0, fixed.stillWaiting)
        assertEquals("500000", serverValue("SELECT price_cents FROM units WHERE id = ?", bad.id))
        assertEquals("IN_STOCK", serverValue("SELECT status FROM units WHERE id = ?", bad.id))
    }

    @Test
    fun `two phones logging the same VIN offline - the second is refused, not merged`() = runBlocking {
        val a = Phone("a").apply { signIn("Sam", "1234") }
        val b = Phone("b").apply { signIn("Jo", "5678") }
        a.offline = true; b.offline = true
        a.sync.logNew(bike(vin = "1M8GDM9AXKP042788"))
        b.sync.logNew(bike(vin = "1M8GDM9AXKP042788", make = "Yamaha"))
        a.offline = false; b.offline = false

        assertEquals(1, a.sync.syncNow().sent)
        val bRound = b.sync.syncNow()
        assertEquals(1, bRound.refused)
        assertEquals(1, serverCount("SELECT COUNT(*) FROM units WHERE vin = ?", "1M8GDM9AXKP042788"))
        assertEquals("Honda", serverValue("SELECT make FROM units WHERE vin = ?", "1M8GDM9AXKP042788"))
        assertEquals(OutboxEntry.STATE_NEEDS_ATTENTION, b.db.shopDao().outbox().single().state)

        // Phone B's employee throws the duplicate away; B then shows only the shop system's item.
        b.sync.discard(b.db.shopDao().outbox().single())
        b.sync.syncNow()
        assertEquals(listOf("Honda"), b.db.shopDao().allUnitsOnce().map { it.make })
    }

    @Test
    fun `overlapping edits from two phones both land, and the later phone is told`() = runBlocking {
        val a = Phone("a").apply { signIn("Sam", "1234") }
        val b = Phone("b").apply { signIn("Jo", "5678") }
        val unit = bike()
        a.sync.logNew(unit)
        a.sync.syncNow()
        b.sync.syncNow()
        assertEquals(1, b.db.shopDao().unit(unit.id)!!.version)

        a.sync.logEdits(unit.id, ItemEdits(color = "Blue"))
        b.sync.logEdits(unit.id, ItemEdits(priceCents = 799_900))
        // A sends first, so B's edit is based on a version that is now out of date.
        assertTrue(a.sync.syncNow().overlapped.isEmpty())
        b.sync.syncNow().let { round ->
            assertEquals(listOf(unit.id), round.overlapped)
            assertEquals(0, round.stillWaiting)
        }
        assertEquals("Blue", serverValue("SELECT color FROM units WHERE id = ?", unit.id))
        assertEquals("799900", serverValue("SELECT price_cents FROM units WHERE id = ?", unit.id))
        assertEquals(1, serverCount("SELECT COUNT(*) FROM audit_log WHERE action = 'unit.update.overlapping'"))
        val bCopy = b.db.shopDao().unit(unit.id)!!
        assertEquals("Blue", bCopy.color)
        assertEquals(799_900, bCopy.priceCents)
    }

    @Test
    fun `fetching never overwrites an item with a change still held on this phone`() = runBlocking {
        val a = Phone("a").apply { signIn("Sam", "1234") }
        val b = Phone("b").apply { signIn("Jo", "5678") }
        val unit = bike()
        a.sync.logNew(unit)
        a.sync.syncNow()
        b.sync.syncNow()

        a.sync.logEdits(unit.id, ItemEdits(color = "Blue"))
        a.sync.syncNow()
        // B's change is refused (no price), so it stays on B; B still fetches everything else.
        b.sync.logEdits(unit.id, ItemEdits(color = "Green", priceCents = 0))
        val round = b.sync.syncNow()
        assertEquals(1, round.refused)
        assertNull(round.stoppedBecause)
        assertEquals("Green", b.db.shopDao().unit(unit.id)!!.color)
        assertEquals("Blue", serverValue("SELECT color FROM units WHERE id = ?", unit.id))
        assertEquals(OutboxEntry.STATE_NEEDS_ATTENTION, b.db.shopDao().outbox().single().state)
    }

    @Test
    fun `an expired sign-in stops the round and keeps every waiting change`() = runBlocking {
        val phone = Phone("a").apply { signIn("Sam", "1234") }
        val unit = bike()
        phone.sync.logNew(unit)
        phone.client.signOut()
        val round = phone.sync.syncNow()
        assertTrue(round.needsSignIn)
        assertEquals(1, round.stillWaiting)
        assertEquals(0, serverCount("SELECT COUNT(*) FROM units"))
        assertEquals(OutboxEntry.STATE_PENDING, phone.db.shopDao().outbox().single().state)

        assertTrue(phone.client.signIn("Sam", "1234") is ShopResult.Ok)
        assertEquals(1, phone.sync.syncNow().sent)
        assertEquals(1, serverCount("SELECT COUNT(*) FROM units WHERE id = ?", unit.id))
    }

    @Test
    fun `scanning finds items on the phone by tag, ID or VIN, and asks the server for unknown ones`() = runBlocking {
        val a = Phone("a").apply { signIn("Sam", "1234") }
        val b = Phone("b").apply { signIn("Jo", "5678") }
        val unit = bike(vin = "1M8GDM9AXKP042788")
        a.sync.logNew(unit)
        val tag = com.countryatv.core.LabelPayload.encode(
            com.countryatv.core.LabelContent(id = unit.id, make = "Honda", model = "CRF450R")
        )
        assertEquals(unit.id, a.sync.findOnPhone(tag)?.id)
        assertEquals(unit.id, a.sync.findOnPhone(unit.id)?.id)
        assertEquals(unit.id, a.sync.findOnPhone("1m8gdm9axkp042788")?.id)
        assertNull(b.sync.findOnPhone(tag))

        a.sync.syncNow()
        val found = b.sync.findOnServer(tag)
        assertTrue(found is ShopResult.Ok)
        assertEquals(unit.id, b.sync.findOnPhone(tag)?.id)
        assertTrue(b.sync.findOnServer("NOT-A-REAL-CODE") is ShopResult.Refused)
    }
}
