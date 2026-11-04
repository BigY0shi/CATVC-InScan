package com.countryatv.server

import com.countryatv.core.reports.ExportRequest
import com.countryatv.core.reports.FigureUnits
import com.countryatv.core.reports.ReportDto
import com.countryatv.core.reports.ReportKeys
import com.countryatv.core.reports.ReportSummary
import com.countryatv.core.reports.UnitFilter
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Reports spec §16: the report's own numbers agree with its list figure, with every drill-down list,
// with the shop calendar, and with the permissions.
class ReportsTest {
    private val rs = ReportsShop()
    private val shop get() = rs.shop

    private suspend fun report(key: String, vararg params: Pair<String, String>): ReportDto {
        val r = rs.get(rs.ray, "/reports/$key", *params)
        assertEquals(HttpStatusCode.OK, r.status, "$key ${params.toList()}: ${r.bodyAsText()}")
        return r.body()
    }

    private suspend fun total(filter: UnitFilter): Long {
        val r = rs.query(rs.ray, filter)
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        return r.body<UnitQueryPage>().total
    }

    @Test
    fun `the summary's figure for every report is that report's first tile`() = testApplication {
        rs.start(this)
        val summary = rs.get(rs.ray, "/reports/summary").body<ReportSummary>()
        assertEquals(ReportKeys.BUILT, summary.figures.map { it.report })
        assertEquals(ReportKeys.NOT_BUILT, summary.notBuilt)
        assertEquals("2026-11-02" to "2026-11-04", summary.from to summary.to)
        for (f in summary.figures) {
            assertEquals(report(f.report, "preset" to "this_week").tiles[0], f.tile, f.report)
        }
    }

    @Test
    fun `every breakdown row's drill-down list has exactly the row's count, for every report and range`() = testApplication {
        rs.start(this)
        var checked = 0
        for (key in ReportKeys.BUILT) for (preset in listOf("today", "this_week", "last_week", "last_30_days")) {
            val dto = report(key, "preset" to preset)
            for (tile in dto.tiles) {
                val drill = tile.drill ?: continue
                if (tile.unit == FigureUnits.COUNT) { assertEquals(tile.value, total(drill), "$key/$preset tile ${tile.key}"); checked++ }
            }
            for (b in dto.breakdowns) for (row in b.rows) {
                val drill = row.drill ?: continue
                assertEquals(row.count, total(drill), "$key/$preset ${b.key}=${row.key}")
                checked++
            }
        }
        assertTrue(checked > 60, "checked $checked rows")
    }

    @Test
    fun `the figures are what happened this week`() = testApplication {
        rs.start(this)
        val checkIns = report(ReportKeys.CHECK_INS)
        assertEquals(3L, checkIns.tiles[0].value, "u1, u2, u3; u4 was Sunday night, u6 is an import")
        assertEquals("same_days_last_week", checkIns.tiles[0].comparison!!.basis)
        assertEquals("2026-10-26" to "2026-10-28", checkIns.tiles[0].comparison!!.let { it.from to it.to })
        assertEquals(1L, checkIns.tiles[0].comparison!!.value, "u5 on Wed 28 Oct")
        assertEquals(0L, checkIns.tiles[1].value, "none of this week's check-ins is In stock now (u1 sold)")
        assertEquals(listOf(3L, 2L), checkIns.tiles[1].parts.map { it.value }, "of 3, 2 still Incoming")
        val makes = checkIns.breakdowns.single { it.key == "make" }.rows
        assertEquals(2L, makes.single { it.key == "polaris" }.count, "\"Polaris\" and \"polaris\" are one make")
        assertEquals(listOf("Dale"), checkIns.breakdowns.single { it.key == "person" }.rows.map { it.label })
        assertEquals("day", checkIns.series!!.bucket)
        assertEquals(listOf(1L, 1L, 1L), checkIns.series!!.points.map { it.value })

        val status = report(ReportKeys.UNITS_BY_STATUS)
        assertEquals(3L, status.tiles[0].value, "u4, u5 and the imported u6")
        assertEquals(listOf(2L, 0L), status.tiles[0].parts.map { it.value })
        assertNull(status.from)

        val price = report(ReportKeys.ASKING_PRICE_TOTAL)
        assertEquals(2_199_900L + 1_449_900 + 399_900, price.tiles[0].value)
        assertEquals(false, price.tiles[1].tracked, "missing a price needs D-C2: not tracked, never 0")
        assertNull(price.tiles[1].value)

        val wait = report(ReportKeys.TIME_TO_IN_STOCK)
        assertEquals(120L, wait.tiles[0].value, "u1 waited 2 hours")
        assertEquals(listOf(1L, 2L), wait.tiles[0].parts.map { it.value })

        val tags = report(ReportKeys.TAGS_PRINTED)
        assertEquals(listOf(3L, 1L), tags.tiles.map { it.value }, "u1, u2 and u5's reprint; u4's tag was Sunday")
        assertTrue(tags.breakdowns.all { b -> b.rows.all { it.drill == null } }, "prints are events: no unit list")

        assertEquals(1L, report(ReportKeys.TAGS_NOT_CONFIRMED).tiles[0].value, "u2")

        val sold = report(ReportKeys.SOLD)
        assertEquals(listOf(1L, 849_900L), sold.tiles.map { it.value })
        assertEquals(listOf("Ray"), sold.breakdowns.single { it.key == "person" }.rows.map { it.label })

        val work = report(ReportKeys.WORK_BY_PERSON)
        val daleRow = work.breakdowns.single().rows.single { it.personId == rs.daleId }
        assertEquals(listOf(3L, 1L, 2L), daleRow.figures.map { it.value }, "Dale: 3 check-ins, moved u4, edited u2 and u5")
        assertNull(daleRow.drill)
    }

    @Test
    fun `presets resolve on the server with the shop calendar, and Sunday-start weeks move Sunday's work`() = testApplication {
        rs.start(this)
        val week = report(ReportKeys.CHECK_INS, "preset" to "this_week")
        assertEquals("2026-11-04T20:00:00.000Z", week.asOf)
        assertEquals("this_week", week.preset)
        assertEquals("2026-11-02" to "2026-11-04", week.from to week.to)
        assertEquals("2026-11-02T08:00:00.000Z" to "2026-11-05T08:00:00.000Z", week.startUtc to week.endUtc)

        val last = report(ReportKeys.CHECK_INS, "preset" to "last_week")
        assertEquals("2026-10-26" to "2026-11-01", last.from to last.to)
        assertEquals(2L, last.tiles[0].value, "u4 (11:50 pm Sunday, Pacific) and u5")
        assertEquals("week_before", last.tiles[0].comparison!!.basis)

        val custom = report(ReportKeys.CHECK_INS, "from" to "2026-10-26", "to" to "2026-11-01")
        assertNull(custom.preset)
        assertEquals(last.tiles[0].value, custom.tiles[0].value)
        assertEquals("previous_days", custom.tiles[0].comparison!!.basis)
        assertEquals(HttpStatusCode.BadRequest, rs.get(rs.ray, "/reports/check_ins", "from" to "2026-11-01", "to" to "2026-11-05").status, "the future")
        assertEquals(HttpStatusCode.BadRequest, rs.get(rs.ray, "/reports/check_ins", "preset" to "fortnight").status)
        assertEquals(HttpStatusCode.NotFound, rs.get(rs.ray, "/reports/aging").status, "not built yet")

        ShopSettings(shop.db, shop.clock).set(Setting.WEEK_START, "SUNDAY", rs.annId, null)
        val sundayWeek = report(ReportKeys.CHECK_INS, "preset" to "this_week")
        assertEquals("2026-11-01" to "2026-11-04", sundayWeek.from to sundayWeek.to)
        assertEquals(4L, sundayWeek.tiles[0].value, "Sunday night's u4 is now this week's")
        assertEquals("2026-11-01", rs.get(rs.ray, "/reports/summary").body<ReportSummary>().from)
    }

    @Test
    fun `a drill-down opened after the shop moved on still lists what the report counted`() = testApplication {
        rs.start(this)
        val before = report(ReportKeys.UNITS_BY_STATUS)
        val incoming = before.breakdowns.single { it.key == "status" }.rows.single { it.key == "INCOMING" }
        assertEquals(2L, incoming.count)
        shop.clock.advance(Duration.ofMinutes(10))
        rs.print(rs.dale, "u3", "2026-11-04T20:09:00Z")
        rs.confirm(rs.dale, "u3", "2026-11-04T20:09:30Z")
        rs.checkIn(rs.dale, "u7", "2026-11-04T20:09:45Z")
        suspend fun ids(f: UnitFilter) = rs.query(rs.ray, f).body<UnitQueryPage>().items.map { it.id }.toSet()
        assertEquals(setOf(rs.u.getValue("u2").id, rs.u.getValue("u3").id), ids(incoming.drill!!),
            "u3 moved and u7 arrived after asOf; the list is still the row's two units")
        assertEquals(setOf(rs.u.getValue("u2").id, rs.u.getValue("u7").id), ids(incoming.drill!!.copy(asOf = null)), "without asOf it's today's list")
        assertEquals(4L, report(ReportKeys.UNITS_BY_STATUS).breakdowns.single { it.key == "status" }.rows.single { it.key == "IN_STOCK" }.count,
            "a fresh report counts u3 In stock beside u4, u5 and u6")
    }

    @Test
    fun `units query pages through every match with no cap, and refuses a cursor from another filter`() = testApplication {
        rs.start(this, seed = false)
        repeat(45) { i -> rs.checkIn(rs.ray, "n$i", "2026-11-03T1${i % 10}:00:00Z") }
        val filter = UnitFilter(statuses = listOf("INCOMING"))
        val seen = mutableListOf<String>()
        var page = rs.query(rs.ray, filter, limit = 20).body<UnitQueryPage>()
        assertEquals(45L, page.total)
        seen += page.items.map { it.id }
        while (page.next != null) {
            page = rs.query(rs.ray, filter, page.next, 20).body<UnitQueryPage>()
            seen += page.items.map { it.id }
        }
        assertEquals(45, seen.size)
        assertEquals(45, seen.toSet().size, "equal check-in times never skip or repeat")
        val first = rs.query(rs.ray, filter, limit = 20).body<UnitQueryPage>()
        assertEquals(HttpStatusCode.BadRequest, rs.query(rs.ray, UnitFilter(statuses = listOf("IN_STOCK")), first.next, 20).status)
        assertNotNull(first.people, "managers get names beside ids")
        assertNull(rs.query(rs.dale, filter).body<UnitQueryPage>().people, "employees get no names")
        assertTrue(rs.query(rs.dale, filter).bodyAsText().let { rs.rayId !in it }, "and no ids")
    }

    @Test
    fun `an Employee is refused every report endpoint and other people's filters, and each refusal is recorded`() = testApplication {
        rs.start(this)
        val refused = listOf(
            rs.get(rs.dale, "/reports/summary"),
            rs.get(rs.dale, "/reports/check_ins", "preset" to "this_week"),
            rs.get(rs.dale, "/reports/units_by_status"),
            rs.client.authedPost(rs.dale, "/reports/check_ins/export", ExportRequest(preset = "this_week")),
            rs.get(rs.dale, "/people/${rs.rayId}/history"),
            rs.query(rs.dale, UnitFilter(checkedInBy = rs.rayId)),
            rs.query(rs.dale, UnitFilter(confirmedBy = rs.daleId)),
            rs.query(rs.dale, UnitFilter(tagPrintedBy = rs.daleId, hasTag = true))
        )
        for (r in refused) assertEquals(HttpStatusCode.Forbidden, r.status, r.call.request.url.toString())
        for (key in ReportKeys.BUILT) assertEquals(HttpStatusCode.Forbidden, rs.get(rs.dale, "/reports/$key").status, key)
        assertEquals(refused.size + ReportKeys.BUILT.size, shop.count("SELECT COUNT(*) FROM audit_log WHERE action = 'refused:NOT_ALLOWED' AND actor = ?", rs.daleId))
        assertEquals(HttpStatusCode.OK, rs.query(rs.dale, UnitFilter(checkedInBy = rs.daleId)).status, "Only units checked in by you")
    }

    @Test
    fun `export writes one audit row with the real count, and taking the permission away refuses the next one`() = testApplication {
        rs.start(this)
        val r = rs.client.authedPost(rs.ray, "/reports/check_ins/export", ExportRequest(preset = "this_week"))
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        assertEquals("text/csv; charset=UTF-8", r.headers[HttpHeaders.ContentType])
        assertTrue(r.headers[HttpHeaders.ContentDisposition]!!.contains("inscan-check-ins-2026-11-02-to-2026-11-04.csv"))
        val text = String(r.bodyAsBytes(), Charsets.UTF_8)
        assertTrue(text.startsWith("﻿Checked in (shop time),Tag ID,Status now,"))
        val lines = text.removeSuffix("\r\n").split("\r\n")
        assertEquals(4, lines.size, "header and 3 check-ins")
        assertTrue(lines[1].startsWith("2026-11-02 09:00,"), "shop time: ${lines[1]}")
        assertTrue(lines[1].endsWith(",8499.00,Dale,Front phone"), lines[1])
        assertEquals(1, shop.count("SELECT COUNT(*) FROM audit_log WHERE action = 'report.export'"))
        assertEquals("""{"report":"check_ins","from":"2026-11-02","to":"2026-11-04","rows":"each","count":3}""",
            shop.single("SELECT after_json FROM audit_log WHERE action = 'report.export'"))

        val table = rs.client.authedPost(rs.ray, "/reports/check_ins/export", ExportRequest(preset = "this_week", rows = "table", by = "make"))
        assertEquals("﻿Make,Check-ins,Share (%)\r\nPolaris,2,66.7\r\nHonda,1,33.3\r\n", String(table.bodyAsBytes(), Charsets.UTF_8))
        assertEquals(HttpStatusCode.NotFound, rs.client.authedPost(rs.ray, "/reports/sold/export", ExportRequest()).status)

        val admin = shop.auth.callerFor(rs.ann)
        val management = RoleAdmin(shop.db, shop.auth, shop.clock).roles().single { it.id == BuiltInRoles.MANAGEMENT }
        RoleAdmin(shop.db, shop.auth, shop.clock).setRolePermissions(BuiltInRoles.MANAGEMENT, management.permissions - Permission.EXPORT_REPORTS, admin)
        assertEquals(HttpStatusCode.Forbidden, rs.client.authedPost(rs.ray, "/reports/check_ins/export", ExportRequest(preset = "this_week")).status)
        assertEquals(2, shop.count("SELECT COUNT(*) FROM audit_log WHERE action = 'report.export'"), "the refused export wrote no export row")
        assertEquals(1, shop.count("SELECT COUNT(*) FROM audit_log WHERE action = 'refused:NOT_ALLOWED' AND actor = ?", rs.rayId))
        assertEquals(HttpStatusCode.OK, rs.get(rs.ray, "/reports/check_ins").status, "Ray can still see the report")
    }
}
