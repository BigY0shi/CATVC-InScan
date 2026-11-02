package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Every stored time is fixed-width UTC with milliseconds, so sorting the text sorts the times.
class TimeFormatTest {
    private val fixedWidth = Regex("""^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z$""")

    @Test
    fun `text order is time order, including the first second of a day`() = testApplication {
        val shop = TestShop(MutableClock(Instant.parse("2026-10-31T23:00:00Z")))
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val times = listOf("2026-11-01T00:00:00Z", "2026-11-01T00:00:00.500Z", "2026-10-31T23:59:59.999Z", "2026-11-01T00:00:01Z")
        val made = times.associateWith { t -> shop.clock.set(t); unit().also { client.createUnit(s, it) }.id }
        shop.clock.set("2026-11-01T00:00:02Z")
        val listed = client.authedGet(s, "/units").body<List<UnitListItem>>().map { it.id }
        val byInstant = times.sortedByDescending { Instant.parse(it) }.map { made.getValue(it) }
        assertEquals(byInstant, listed)
        assertEquals("2026-11-01T00:00:00.000Z", shop.single("SELECT created_at FROM units WHERE id = ?", made.getValue("2026-11-01T00:00:00Z")))
    }

    @Test
    fun `a phone's print time is normalised, and something that isn't a time is refused`() = testApplication {
        val shop = TestShop()
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val u = unit().also { client.createUnit(s, it) }
        val payload = LabelPayload.encode(LabelContent(id = u.id, make = u.make, model = u.model))

        val offset = client.authedPost(s, "/units/${u.id}/tags", TagPrinted(ItemId.new(), payload, "shared", "2026-10-27T09:00:00-07:00"))
        assertEquals("2026-10-27T16:00:00.000Z", offset.body<TagDto>().printedAt)
        val garbage = client.authedPost(s, "/units/${u.id}/tags", TagPrinted(ItemId.new(), payload, "shared", "yesterday"))
        assertEquals(HttpStatusCode.BadRequest, garbage.status)
        assertEquals(listOf("printedAt"), garbage.body<ProblemBody>().details)
        assertEquals(1, shop.count("SELECT COUNT(*) FROM tags"))
    }

    @Test
    fun `migrating an old shop rewrites every time column, and every column ending in _at or _until is covered`() = testApplication {
        val old = OldShop()
        Database(old.file.path).use { db ->
            // Exercise today's write paths too, so columns added after version 2 hold values.
            val clock = MutableClock()
            val auth = Auth(db, clock)
            auth.createEmployee("Sam", "employee", "1234")
            application { inscanServer(db, clock) }
            val client = createClient { install(ContentNegotiation) { json() } }
            val s = client.signIn(auth, "Sam", "1234")
            val u = unit().also { client.createUnit(s, it) }
            val payload = LabelPayload.encode(LabelContent(id = u.id, make = u.make, model = u.model))
            client.authedPost(s, "/units/${u.id}/tags", TagPrinted(ItemId.new(), payload, "shared", "2026-10-27T16:00:00Z"))
            client.authedPost(s, "/units/${u.id}/tags/confirm", TagConfirm(payload))

            val columns = db.read { c ->
                val tables = c.createStatement().use { st ->
                    st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
                }
                tables.flatMap { t ->
                    c.createStatement().use { st ->
                        st.executeQuery("PRAGMA table_info($t)").use { rs -> buildList { while (rs.next()) add(t to rs.getString("name")) } }
                    }
                }.filter { (_, col) -> col.endsWith("_at") || col.endsWith("_until") || col == "at" || col.endsWith("_since") }
            }
            assertTrue(columns.size >= 12, "found ${columns.size} time columns")
            for ((table, col) in columns) {
                val values = db.read { c ->
                    c.createStatement().use { st ->
                        st.executeQuery("SELECT $col FROM $table WHERE $col IS NOT NULL").use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
                    }
                }
                for (v in values) {
                    if (table == "tags" && col == "printed_at" && v == "yesterday") continue // not a time: kept as it was
                    assertTrue(fixedWidth.matches(v), "$table.$col holds \"$v\"")
                }
            }
            assertEquals("2026-10-20T15:00:00.000Z", db.read { c -> c.prepareStatement("SELECT created_at FROM employees WHERE id = ?").use { it.setString(1, old.dale); it.executeQuery().use { rs -> rs.next(); rs.getString(1) } } })
            assertEquals("2026-10-28T04:00:00.000Z", db.read { c -> c.createStatement().use { st -> st.executeQuery("SELECT expires_at FROM sessions WHERE token_hash = 'st'").use { rs -> rs.next(); rs.getString(1) } } })
        }
    }
}
