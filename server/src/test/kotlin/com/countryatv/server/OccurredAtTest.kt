package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

// A change made offline keeps the time it was made on the phone, within believable limits.
class OccurredAtTest {
    // Monday 2 Nov 2026, 8:00 am Pacific (PST, the day after the clocks go back).
    private val shop = TestShop(MutableClock(Instant.parse("2026-11-02T16:00:00Z")))

    private fun auditRow(action: String, entityId: String): Triple<String?, String?, String?> = shop.db.read { c ->
        c.prepareStatement("SELECT at, occurred_at, occurred_clamped FROM audit_log WHERE action = ? AND entity_id = ?").use {
            it.setString(1, action); it.setString(2, entityId)
            it.executeQuery().use { rs -> rs.next(); Triple(rs.getString(1), rs.getString(2), rs.getString(3)) }
        }
    }

    @Test
    fun `a check-in made at 11-50 pm Sunday and received at 8 am Monday keeps its Sunday time`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val u = unit().copy(occurredAt = "2026-11-01T23:50:00-08:00")
        val saved = client.createUnit(s, u).body<UnitSaved>().unit
        assertEquals("2026-11-02T07:50:00.000Z", saved.checkedInAt)
        assertEquals("2026-11-02T16:00:00.000Z", saved.createdAt, "created_at stays the time the server got it")
        assertEquals(Triple("2026-11-02T16:00:00.000Z", "2026-11-02T07:50:00.000Z", "0"), auditRow("unit.create", u.id))
    }

    @Test
    fun `older phones that send no time get the server's time`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val u = unit()
        assertEquals("2026-11-02T16:00:00.000Z", client.createUnit(s, u).body<UnitSaved>().unit.checkedInAt)
        assertEquals(Triple("2026-11-02T16:00:00.000Z", "2026-11-02T16:00:00.000Z", "0"), auditRow("unit.create", u.id))
    }

    @Test
    fun `a phone time in the future or older than any queue is clamped and flagged, a little ahead is fine`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val ahead = unit().copy(occurredAt = "2026-11-02T16:04:00Z")
        val future = unit().copy(occurredAt = "2026-11-02T17:00:00Z")
        val ancient = unit().copy(occurredAt = "2025-01-01T00:00:00Z")
        for (u in listOf(ahead, future, ancient)) assertEquals(HttpStatusCode.Created, client.createUnit(s, u).status)
        assertEquals(Triple("2026-11-02T16:00:00.000Z", "2026-11-02T16:04:00.000Z", "0"), auditRow("unit.create", ahead.id))
        assertEquals(Triple("2026-11-02T16:00:00.000Z", "2026-11-02T16:00:00.000Z", "1"), auditRow("unit.create", future.id))
        assertEquals(Triple("2026-11-02T16:00:00.000Z", "2026-10-03T16:00:00.000Z", "1"), auditRow("unit.create", ancient.id))
    }

    @Test
    fun `a time that isn't a time is refused and nothing is saved`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val r = client.createUnit(s, unit().copy(occurredAt = "last Sunday"))
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals(listOf("occurredAt"), r.body<ProblemBody>().details)
        assertEquals(0, shop.count("SELECT COUNT(*) FROM units"))
    }

    @Test
    fun `status changes, prints and confirms keep their phone times`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val u = unit().copy(occurredAt = "2026-11-02T06:00:00Z").also { client.createUnit(s, it) }
        val payload = LabelPayload.encode(LabelContent(id = u.id, make = u.make, model = u.model))
        val tagId = ItemId.new()
        client.authedPost(s, "/units/${u.id}/tags", TagPrinted(tagId, payload, "shared", "2026-11-02T06:01:00Z"))
        client.authedPost(s, "/units/${u.id}/tags/confirm", TagConfirm(payload, occurredAt = "2026-11-02T06:10:00Z"))
        assertEquals("2026-11-02T06:01:00.000Z", auditRow("tag.print", tagId).second)
        assertEquals("2026-11-02T06:10:00.000Z", auditRow("tag.confirm", tagId).second)
        assertEquals("2026-11-02T06:10:00.000Z", auditRow("unit.status:IN_STOCK", u.id).second)
        assertEquals("2026-11-02T06:10:00.000Z", shop.single("SELECT confirmed_at FROM tags WHERE id = ?", tagId))

        assertEquals(HttpStatusCode.OK, client.authedPost(s, "/units/${u.id}/status", StatusChange("SOLD", occurredAt = "2026-11-02T07:00:00Z")).status)
        assertEquals("2026-11-02T07:00:00.000Z", auditRow("unit.status:SOLD", u.id).second)
    }

    @Test
    fun `an old shop's rows get an occurred time and every unit a check-in time`() {
        val old = OldShop()
        Database(old.file.path).use { db ->
            db.read { c ->
                c.createStatement().use { st ->
                    st.executeQuery("SELECT COUNT(*) FROM audit_log WHERE occurred_at IS NULL OR occurred_at <> at").use { rs -> rs.next(); assertEquals(0, rs.getInt(1)) }
                    st.executeQuery("SELECT COUNT(*) FROM units WHERE checked_in_at IS NULL OR checked_in_at <> created_at").use { rs -> rs.next(); assertEquals(0, rs.getInt(1)) }
                }
            }
        }
    }
}
