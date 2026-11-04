package com.countryatv.server

import com.countryatv.core.reports.HistoryKinds
import com.countryatv.core.reports.HistoryPage
import com.countryatv.core.reports.ReportDto
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// My history spec §9: own work only, one classifier shared with Work by person, and paging that a
// late arrival can't disturb.
class HistoryTest {
    private val rs = ReportsShop()
    private val shop get() = rs.shop

    private suspend fun history(session: String, path: String = "/me/history", vararg params: Pair<String, String>): HistoryPage {
        val r = rs.get(session, path, *params)
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        return r.body()
    }

    @Test
    fun `my history is my own work, classified and folded, with no one else's id or name`() = testApplication {
        rs.start(this)
        val raw = rs.get(rs.dale, "/me/history", "employeeId" to rs.rayId).bodyAsText()
        for (other in listOf(rs.rayId, rs.annId, "Ray", "Ann")) assertFalse(other in raw, "$other must not appear in Dale's history")
        val page = history(rs.dale, "/me/history", "employeeId" to rs.rayId)
        assertNull(page.person)
        assertEquals("2026-11-02" to "2026-11-04", page.from to page.to)
        val kinds = page.events.map { it.kind to it.unit.id }
        assertEquals(listOf(
            // Edits carry no phone time yet, so they happened when the server got them (now).
            HistoryKinds.EDIT to rs.u.getValue("u5").id,
            HistoryKinds.EDIT to rs.u.getValue("u2").id,
            HistoryKinds.CHECK_IN to rs.u.getValue("u3").id,
            HistoryKinds.CONFIRMED_NEW_TAG to rs.u.getValue("u5").id,
            HistoryKinds.REPRINT to rs.u.getValue("u5").id,
            HistoryKinds.CHECK_IN to rs.u.getValue("u2").id,
            HistoryKinds.MOVED_TO_IN_STOCK to rs.u.getValue("u4").id,
            HistoryKinds.CHECK_IN to rs.u.getValue("u1").id
        ), kinds, "newest first; first prints folded into check-ins; Ray's confirm and sale aren't Dale's")
        val u1 = page.events.last()
        assertEquals("2026-11-02T17:05:00.000Z", u1.tagPrintedAt, "Checked in, tag printed 9:05 am")
        assertNull(page.events.single { it.kind == HistoryKinds.CHECK_IN && it.unit.id == rs.u.getValue("u3").id }.tagPrintedAt, "u3: no tag yet")
        assertEquals("damaged", page.events.single { it.kind == HistoryKinds.REPRINT }.reason)
        val u2edit = page.events.single { it.kind == HistoryKinds.EDIT && it.unit.id == rs.u.getValue("u2").id }
        assertEquals("price", u2edit.changes.single().field)
        assertEquals("989900" to "949900", u2edit.changes.single().let { it.from to it.to })
        assertTrue(u2edit.changedSince, "Ray changed the price again after Dale")
        assertFalse(page.events.single { it.kind == HistoryKinds.EDIT && it.unit.id == rs.u.getValue("u5").id }.changedSince)
        val t = page.totals!!
        assertEquals(listOf(3L, 2L, 1L, 2L, 1L), listOf(t.checkIns, t.stillIncoming, t.movedToInStock, t.edits, t.reprints))
    }

    @Test
    fun `a person's history needs See Reports, agrees with Work by person, and still opens for someone deactivated`() = testApplication {
        rs.start(this)
        assertEquals(HttpStatusCode.Forbidden, rs.get(rs.dale, "/people/${rs.daleId}/history").status)
        val viaManager = history(rs.ray, "/people/${rs.daleId}/history")
        assertEquals("Dale", viaManager.person!!.displayName)
        assertEquals(history(rs.dale).events, viaManager.events)

        val work = rs.get(rs.ray, "/reports/work_by_person").body<ReportDto>().breakdowns.single().rows.single { it.personId == rs.daleId }
        val t = viaManager.totals!!
        assertEquals(listOf(t.checkIns, t.movedToInStock, t.edits), work.figures.map { it.value })
        val checkInsReport = rs.get(rs.ray, "/reports/check_ins").body<ReportDto>()
        assertEquals(t.checkIns, checkInsReport.breakdowns.single { it.key == "person" }.rows.single { it.key == rs.daleId }.count)

        RoleAdmin(shop.db, shop.auth, shop.clock).deactivateEmployee(rs.daleId, shop.auth.callerFor(rs.ann))
        val gone = history(rs.ray, "/people/${rs.daleId}/history")
        assertEquals(false, gone.person!!.active)
        assertEquals(viaManager.events, gone.events)
        assertEquals(HttpStatusCode.NotFound, rs.get(rs.ray, "/people/NOBODY/history").status)
    }

    @Test
    fun `an edit made offline keeps its phone time in history`() = testApplication {
        rs.start(this)
        rs.editPrice(rs.dale, "u3", 1_049_900, at = "2026-11-04T17:30:00Z")
        val edit = history(rs.dale).events.single { it.kind == HistoryKinds.EDIT && it.unit.id == rs.u.getValue("u3").id }
        assertEquals("2026-11-04T17:30:00.000Z", edit.occurredAt)
        assertEquals("2026-11-04T20:00:00.000Z", shop.single("SELECT at FROM audit_log WHERE action = 'unit.update' AND entity_id = ?", rs.u.getValue("u3").id))
    }

    @Test
    fun `imports are no one's history and no one's work`() = testApplication {
        rs.start(this)
        assertTrue(history(rs.ann).events.isEmpty(), "Ann only imported u6")
        val work = rs.get(rs.ray, "/reports/work_by_person").body<ReportDto>().breakdowns.single().rows
        assertTrue(work.none { it.personId == rs.annId })
    }

    @Test
    fun `paging is stable against asOf, ties appear once, and a late arrival doesn't land behind the cursor`() = testApplication {
        rs.start(this, seed = false)
        // 31 check-ins by Dale this week; the last two share a time.
        repeat(30) { i -> rs.checkIn(rs.dale, "p$i", "2026-11-03T%02d:%02d:00Z".format(10 + i / 6, (i % 6) * 10)) }
        rs.checkIn(rs.dale, "tie", "2026-11-03T14:50:00Z")
        val p1 = history(rs.dale)
        assertEquals(30, p1.events.size)
        assertEquals(31L, p1.totals!!.checkIns)
        val next = assertNotNull(p1.next)

        // A check-in made Monday on an offline phone arrives between the two page loads.
        shop.clock.advance(Duration.ofMinutes(5))
        rs.checkIn(rs.dale, "late", "2026-11-02T16:00:00Z")

        assertEquals(HttpStatusCode.BadRequest, rs.get(rs.dale, "/me/history", "cursor" to next).status, "a cursor needs its asOf")
        val p2 = history(rs.dale, "/me/history", "cursor" to next, "asOf" to p1.asOf)
        assertNull(p2.totals, "totals come on page 1 only")
        assertEquals(1, p2.events.size, "page 2 is what page 1 left, nothing more")
        assertNull(p2.next)
        val ids = (p1.events + p2.events).map { it.eventId }
        assertEquals(31, ids.toSet().size, "every event exactly once, ties included")
        assertFalse(rs.u.getValue("late").id in (p1.events + p2.events).map { it.unit.id })

        val fresh = history(rs.dale)
        assertEquals(32L, fresh.totals!!.checkIns, "a fresh page 1 has the late one")
    }
}
