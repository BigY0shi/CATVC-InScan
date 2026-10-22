package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerTest {

    // Every test gets its own database file in a temp directory: no shared state, no real shop data.
    private val dbFile = File.createTempFile("inscan-test", ".db").also { it.delete(); it.deleteOnExit() }
    private val db = Database(dbFile.path)
    private val auth = Auth(db)

    private fun ApplicationTestBuilder.client(): HttpClient {
        application { inscanServer(db) }
        return createClient { install(ContentNegotiation) { json() } }
    }

    private suspend fun HttpClient.signIn(name: String, pin: String, label: String = "Phone"): String {
        val device = post("/auth/enroll") {
            contentType(ContentType.Application.Json); setBody(EnrollRequest(auth.newSetupCode("test"), label))
        }.body<EnrollResponse>().deviceToken
        return post("/auth/login") {
            header(DEVICE_HEADER, device); contentType(ContentType.Application.Json); setBody(LoginRequest(name, pin))
        }.body<LoginResponse>().sessionToken
    }

    private fun newUnit(id: String = ItemId.new(), vin: String? = "1M8GDM9AXKP042788", frame: Boolean = false) =
        NewUnit(id = id, make = "Honda", model = "CRF450R", year = 2025, color = "Red", unitType = "Dirt Bike",
            vin = vin, vinFrameVerified = frame, priceCents = 849_900)

    private suspend fun HttpClient.create(session: String, unit: NewUnit): HttpResponse = post("/units") {
        header("Authorization", "Bearer $session"); contentType(ContentType.Application.Json); setBody(unit)
    }

    private fun count(sql: String, vararg args: String): Int = db.read { c ->
        c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setString(i + 1, a) }
            st.executeQuery().use { it.next(); it.getInt(1) }
        }
    }

    private fun single(sql: String, vararg args: String): String? = db.read { c ->
        c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setString(i + 1, a) }
            st.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
    }

    @Test
    fun `setup codes work once`() = testApplication {
        val client = client()
        val code = auth.newSetupCode("test")
        val first = client.post("/auth/enroll") { contentType(ContentType.Application.Json); setBody(EnrollRequest(code, "Phone A")) }
        assertEquals(HttpStatusCode.OK, first.status)
        val second = client.post("/auth/enroll") { contentType(ContentType.Application.Json); setBody(EnrollRequest(code, "Phone B")) }
        assertEquals(HttpStatusCode.Unauthorized, second.status)
        assertTrue(second.bodyAsText().contains("SETUP_CODE_INVALID"))
        assertEquals(1, count("SELECT COUNT(*) FROM devices"))
    }

    @Test
    fun `wrong PINs lock the employee and nothing works without signing in`() = testApplication {
        auth.createEmployee("Sam", "employee", "1234")
        val client = client()
        val device = client.post("/auth/enroll") {
            contentType(ContentType.Application.Json); setBody(EnrollRequest(auth.newSetupCode("test"), "Phone"))
        }.body<EnrollResponse>().deviceToken
        repeat(4) {
            val r = client.post("/auth/login") { header(DEVICE_HEADER, device); contentType(ContentType.Application.Json); setBody(LoginRequest("Sam", "0000")) }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
        val fifth = client.post("/auth/login") { header(DEVICE_HEADER, device); contentType(ContentType.Application.Json); setBody(LoginRequest("Sam", "0000")) }
        assertTrue(fifth.bodyAsText().contains("PIN_LOCKED"))
        val correctWhileLocked = client.post("/auth/login") { header(DEVICE_HEADER, device); contentType(ContentType.Application.Json); setBody(LoginRequest("Sam", "1234")) }
        assertEquals(HttpStatusCode.TooManyRequests, correctWhileLocked.status)
        assertEquals(5, count("SELECT COUNT(*) FROM audit_log WHERE action = 'login.failed'"))

        val anonymous = client.get("/units")
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertTrue(anonymous.bodyAsText().contains("Sign in with your PIN first."))
    }

    @Test
    fun `logging an item records who did it, and refuses missing details in plain words`() = testApplication {
        val samId = auth.createEmployee("Sam", "employee", "1234")
        val client = client()
        val session = client.signIn("Sam", "1234")

        val missing = client.create(session, newUnit().copy(make = " ", priceCents = 0))
        assertEquals(HttpStatusCode.UnprocessableEntity, missing.status)
        val body = missing.body<ProblemBody>()
        assertEquals(listOf("make", "price"), body.details)
        assertFalse(missing.bodyAsText().contains("Exception"))
        assertEquals(0, count("SELECT COUNT(*) FROM units"))

        val unit = newUnit()
        assertEquals(HttpStatusCode.Created, client.create(session, unit).status)
        assertEquals("INCOMING", single("SELECT status FROM units WHERE id = ?", unit.id))
        assertEquals(samId, single("SELECT created_by FROM units WHERE id = ?", unit.id))
        assertEquals("ok", single("SELECT vin_check FROM units WHERE id = ?", unit.id))
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'unit.create' AND entity_id = ? AND actor = ?", unit.id, samId))
    }

    @Test
    fun `a VIN that fails the check needs the frame box ticked, and who ticked it is kept`() = testApplication {
        val samId = auth.createEmployee("Sam", "employee", "1234")
        val client = client()
        val session = client.signIn("Sam", "1234")
        val badVin = "1M8GDM9A1KP042788"

        val refused = client.create(session, newUnit(vin = badVin))
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        assertTrue(refused.bodyAsText().contains("VIN_NOT_CONFIRMED"))

        val unit = newUnit(vin = badVin, frame = true)
        assertEquals(HttpStatusCode.Created, client.create(session, unit).status)
        assertEquals("fail", single("SELECT vin_check FROM units WHERE id = ?", unit.id))
        assertEquals(samId, single("SELECT vin_frame_verified_by FROM units WHERE id = ?", unit.id))
    }

    @Test
    fun `retrying the same save is safe, reusing an id for something else is not`() = testApplication {
        auth.createEmployee("Sam", "employee", "1234")
        val client = client()
        val session = client.signIn("Sam", "1234")
        val unit = newUnit()
        assertEquals(HttpStatusCode.Created, client.create(session, unit).status)
        assertEquals(HttpStatusCode.Created, client.create(session, unit).status)
        val clash = client.create(session, unit.copy(model = "CRF250R"))
        assertEquals(HttpStatusCode.Conflict, clash.status)
        assertEquals(1, count("SELECT COUNT(*) FROM units"))
        assertEquals("CRF450R", single("SELECT model FROM units WHERE id = ?", unit.id))
    }

    @Test
    fun `the same VIN can't be logged twice`() = testApplication {
        auth.createEmployee("Sam", "employee", "1234")
        val client = client()
        val session = client.signIn("Sam", "1234")
        val first = newUnit()
        client.create(session, first)
        val second = client.create(session, newUnit())
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertTrue(second.body<ProblemBody>().details.contains(first.id))
    }

    @Test
    fun `scanning finds an item by our tag, by its id, or by VIN, and says which`() = testApplication {
        auth.createEmployee("Sam", "employee", "1234")
        val client = client()
        val session = client.signIn("Sam", "1234")
        val unit = newUnit()
        client.create(session, unit)

        suspend fun scan(code: String) = client.get("/units/by-code/${code.encodeURLPathPart()}") { header("Authorization", "Bearer $session") }

        val tag = LabelPayload.encode(LabelContent(unit.id, make = "Honda", model = "CRF450R"))
        assertEquals("tag", scan(tag).body<ScanMatch>().matchedBy)
        assertEquals("id", scan(unit.id).body<ScanMatch>().matchedBy)
        assertEquals("vin", scan("1m8gdm9axkp042788").body<ScanMatch>().matchedBy)
        val none = scan("012345678905")
        assertEquals(HttpStatusCode.NotFound, none.status)
        assertTrue(none.bodyAsText().contains("No item with that tag or VIN"))
    }

    @Test
    fun `two phones logging at the same time never collide`() = testApplication {
        auth.createEmployee("Sam", "employee", "1234")
        auth.createEmployee("Alex", "employee", "5678")
        val client = client()
        val phoneA = client.signIn("Sam", "1234", "Phone A")
        val phoneB = client.signIn("Alex", "5678", "Phone B")
        val statuses = coroutineScope {
            (1..40).map { i ->
                async { client.create(if (i % 2 == 0) phoneA else phoneB, newUnit(vin = null)).status }
            }.awaitAll()
        }
        assertTrue(statuses.all { it == HttpStatusCode.Created })
        assertEquals(40, count("SELECT COUNT(*) FROM units"))
        assertEquals(40, count("SELECT COUNT(DISTINCT id) FROM units"))
    }

    @Test
    fun `overlapping edits are flagged, kept in the audit log, and never silent`() = testApplication {
        auth.createEmployee("Sam", "employee", "1234")
        auth.createEmployee("Alex", "employee", "5678")
        val client = client()
        val phoneA = client.signIn("Sam", "1234", "Phone A")
        val phoneB = client.signIn("Alex", "5678", "Phone B")
        val unit = newUnit()
        client.create(phoneA, unit)

        suspend fun edit(session: String, changes: UnitChanges) = client.patch("/units/${unit.id}") {
            header("Authorization", "Bearer $session"); contentType(ContentType.Application.Json); setBody(changes)
        }.body<UnitSaved>()

        val first = edit(phoneA, UnitChanges(baseVersion = 1, color = "Blue"))
        assertFalse(first.overwroteOtherChanges)
        val second = edit(phoneB, UnitChanges(baseVersion = 1, color = "Green"))
        assertTrue(second.overwroteOtherChanges)
        assertEquals("Green", single("SELECT color FROM units WHERE id = ?", unit.id))
        val overlapBefore = single("SELECT before_json FROM audit_log WHERE action = 'unit.update.overlapping' AND entity_id = ?", unit.id)
        assertNotNull(overlapBefore)
        assertTrue(overlapBefore.contains("\"color\":\"Blue\""))
    }

    @Test
    fun `status changes follow the rules, and retiring needs the owner and a reason`() = testApplication {
        auth.createEmployee("Sam", "employee", "1234")
        auth.createEmployee("Owner", "admin", "9999")
        val client = client()
        val sam = client.signIn("Sam", "1234")
        val owner = client.signIn("Owner", "9999")
        val unit = newUnit()
        client.create(sam, unit)

        suspend fun status(session: String, s: String, reason: String = "") = client.post("/units/${unit.id}/status") {
            header("Authorization", "Bearer $session"); contentType(ContentType.Application.Json); setBody(StatusChange(s, reason))
        }

        assertEquals(HttpStatusCode.Conflict, status(sam, "SOLD").status)
        assertEquals(HttpStatusCode.OK, status(sam, "IN_STOCK").status)
        assertEquals(HttpStatusCode.Forbidden, status(sam, "RETIRED", "damaged").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, status(owner, "RETIRED").status)
        assertEquals(HttpStatusCode.OK, status(owner, "RETIRED", "Damaged in shipping").status)
        assertEquals("RETIRED", single("SELECT status FROM units WHERE id = ?", unit.id))
        val after = single("SELECT after_json FROM audit_log WHERE action = 'unit.status:RETIRED'")
        assertNotNull(after)
        assertTrue(after.contains("Damaged in shipping"))
        assertNull(single("SELECT id FROM units WHERE status = 'SOLD'"))
    }

    @Test
    fun `migrations run once and survive a restart without losing data`() = testApplication {
        auth.createEmployee("Sam", "employee", "1234")
        val client = client()
        val session = client.signIn("Sam", "1234")
        val unit = newUnit()
        client.create(session, unit)
        db.close()
        Database(dbFile.path).use { reopened ->
            val versions = reopened.read { c -> c.createStatement().use { it.executeQuery("SELECT COUNT(*) FROM schema_version").use { rs -> rs.next(); rs.getInt(1) } } }
            assertEquals(Database.MIGRATIONS.size, versions)
            val model = reopened.read { c -> c.prepareStatement("SELECT model FROM units WHERE id = ?").use { it.setString(1, unit.id); it.executeQuery().use { rs -> rs.next(); rs.getString(1) } } }
            assertEquals("CRF450R", model)
        }
    }
}
