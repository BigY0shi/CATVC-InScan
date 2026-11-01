package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Every status change is one typed row, whichever path made it.
class StatusChangesTest {
    private val shop = TestShop()

    private fun changes(unitId: String): List<String> = shop.column(
        "SELECT from_status || '>' || to_status || ' ' || source || ' ' || occurred_at || ' ' || price_cents FROM unit_status_changes WHERE unit_id = ? ORDER BY id",
        unitId
    ).map { it!! }

    @Test
    fun `the status route writes exactly one manual row per real change`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val u = unit(priceCents = 849_900).also { client.createUnit(s, it) }
        client.authedPost(s, "/units/${u.id}/status", StatusChange("IN_STOCK", occurredAt = "2026-10-27T15:00:00Z"))
        client.authedPost(s, "/units/${u.id}/status", StatusChange("IN_STOCK"))
        assertEquals(listOf("INCOMING>IN_STOCK manual 2026-10-27T15:00:00.000Z 849900"), changes(u.id))
        assertEquals("2026-10-27T16:00:00.000Z", shop.single("SELECT received_at FROM unit_status_changes WHERE unit_id = ?", u.id))
        assertEquals(
            shop.single("SELECT id FROM audit_log WHERE action = 'unit.status:IN_STOCK' AND entity_id = ?", u.id),
            shop.single("SELECT audit_id FROM unit_status_changes WHERE unit_id = ?", u.id)
        )
    }

    @Test
    fun `a tag confirm writes exactly one scan row, and a second confirm writes none`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val u = unit().also { client.createUnit(s, it) }
        val payload = LabelPayload.encode(LabelContent(id = u.id, make = u.make, model = u.model))
        client.authedPost(s, "/units/${u.id}/tags", TagPrinted(ItemId.new(), payload, "shared", "2026-10-27T16:00:00Z"))
        repeat(2) { client.authedPost(s, "/units/${u.id}/tags/confirm", TagConfirm(payload)) }
        assertEquals(listOf("INCOMING>IN_STOCK scan 2026-10-27T16:00:00.000Z 849900"), changes(u.id))
    }

    @Test
    fun `in stock since is the first move to In stock and later moves don't change it`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        shop.auth.createEmployee("Ray", "admin", "5678")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val r = client.signIn(shop.auth, "Ray", "5678")
        val u = unit().also { client.createUnit(s, it) }
        assertNull(shop.single("SELECT in_stock_since FROM units WHERE id = ?", u.id))
        client.authedPost(s, "/units/${u.id}/status", StatusChange("IN_STOCK"))
        shop.clock.advance(Duration.ofHours(1))
        client.authedPost(s, "/units/${u.id}/status", StatusChange("SOLD"))
        shop.clock.advance(Duration.ofHours(1))
        assertEquals(HttpStatusCode.OK, client.authedPost(r, "/units/${u.id}/status", StatusChange("IN_STOCK")).status)
        assertEquals("2026-10-27T16:00:00.000Z", shop.single("SELECT in_stock_since FROM units WHERE id = ?", u.id))
        assertEquals(3, changes(u.id).size)
    }

    @Test
    fun `the backfill reads both audit shapes and matches the audit log`() {
        val old = OldShop()
        Database(old.file.path).use { db ->
            fun col(sql: String, vararg a: String) = db.read { c ->
                c.prepareStatement(sql).use { st -> a.forEachIndexed { i, v -> st.setString(i + 1, v) }; st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } } }
            }
            assertEquals(
                col("SELECT COUNT(*) FROM audit_log WHERE action LIKE 'unit.status:%'"),
                col("SELECT COUNT(*) FROM unit_status_changes")
            )
            assertEquals(listOf("INCOMING>IN_STOCK scan 2026-10-26T08:05:00.250Z"),
                col("SELECT from_status || '>' || to_status || ' ' || source || ' ' || occurred_at FROM unit_status_changes WHERE unit_id = ?", old.inStock))
            assertEquals(listOf("INCOMING>IN_STOCK manual 2026-10-25T18:30:00.000Z", "IN_STOCK>SOLD manual 2026-10-26T17:00:00.000Z"),
                col("SELECT from_status || '>' || to_status || ' ' || source || ' ' || occurred_at FROM unit_status_changes WHERE unit_id = ? ORDER BY id", old.sold))
            assertEquals(listOf("2026-10-26T08:05:00.250Z"), col("SELECT in_stock_since FROM units WHERE id = ?", old.inStock))
            assertEquals(listOf("2026-10-25T18:30:00.000Z"), col("SELECT in_stock_since FROM units WHERE id = ?", old.sold))
            assertEquals(listOf<String?>(null), col("SELECT in_stock_since FROM units WHERE id = ?", old.incoming))
            assertEquals(listOf("849900"), col("SELECT DISTINCT price_cents FROM unit_status_changes"))
        }
    }
}
