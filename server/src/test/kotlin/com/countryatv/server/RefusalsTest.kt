package com.countryatv.server

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

// A refused request leaves a record that survives the refusal, and every sign-in try is recorded.
class RefusalsTest {
    private val shop = TestShop()

    @Test
    fun `a 403 and a 409 each leave their refusal row, while the refused change itself rolls back`() = testApplication {
        val samId = shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val u = unit().also { client.createUnit(s, it) }

        assertEquals(HttpStatusCode.Forbidden, client.authedPost(s, "/units/${u.id}/status", StatusChange("RETIRED", "water damage")).status)
        assertEquals(HttpStatusCode.Conflict, client.authedPost(s, "/units/${u.id}/status", StatusChange("SOLD")).status)

        assertEquals(listOf("refused:NOT_ALLOWED", "refused:STATUS_CHANGE_NOT_ALLOWED"), shop.column("SELECT action FROM audit_log WHERE action LIKE 'refused:%' ORDER BY id"))
        assertEquals(listOf(samId), shop.column("SELECT DISTINCT actor FROM audit_log WHERE action LIKE 'refused:%'"))
        assertEquals("POST /units/${u.id}/status", shop.single("SELECT entity_id FROM audit_log WHERE action = 'refused:STATUS_CHANGE_NOT_ALLOWED'"))
        assertEquals("""{"status":409,"details":["Incoming → Sold"]}""", shop.single("SELECT after_json FROM audit_log WHERE action = 'refused:STATUS_CHANGE_NOT_ALLOWED'"))
        assertEquals("INCOMING", shop.single("SELECT status FROM units WHERE id = ?", u.id))
        assertEquals(0, shop.count("SELECT COUNT(*) FROM unit_status_changes"))
    }

    @Test
    fun `five wrong PINs leave five failures and a lockout, and tries while locked out or with an unknown name are recorded once`() = testApplication {
        val samId = shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val device = shop.auth.enrollDevice(shop.auth.newSetupCode("test"), "Phone")
        suspend fun login(name: String, pin: String) = client.post("/auth/login") {
            header(DEVICE_HEADER, device); contentType(ContentType.Application.Json); setBody(LoginRequest(name, pin))
        }.status

        repeat(5) { login("Sam", "0000") }
        assertEquals(HttpStatusCode.TooManyRequests, login("Sam", "1234"))
        assertEquals(HttpStatusCode.Unauthorized, login("Samm", "1234"))

        assertEquals(
            List(5) { "login.failed" } + listOf("login.lockout", "refused:PIN_LOCKED", "login.unknown_name"),
            shop.column("SELECT action FROM audit_log WHERE action LIKE 'login%' OR action LIKE 'refused:%' ORDER BY id")
        )
        assertEquals(samId, shop.single("SELECT entity_id FROM audit_log WHERE action = 'login.lockout'"))
        assertEquals("""{"until":"2026-10-27T16:05:00.000Z"}""", shop.single("SELECT after_json FROM audit_log WHERE action = 'login.lockout'"))
        assertEquals("Samm", shop.single("SELECT entity_id FROM audit_log WHERE action = 'login.unknown_name'"))
    }

    @Test
    fun `requests nobody can be named for aren't recorded, and unreadable ones are`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        assertEquals(HttpStatusCode.Unauthorized, client.authedGet("not-a-session", "/units").status)
        assertEquals(0, shop.count("SELECT COUNT(*) FROM audit_log WHERE action LIKE 'refused:%'"))

        val s = client.signIn(shop.auth, "Sam", "1234")
        val bad = client.post("/units") { header("Authorization", "Bearer $s"); contentType(ContentType.Application.Json); setBody("{\"make\":") }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals(listOf("refused:BAD_REQUEST"), shop.column("SELECT action FROM audit_log WHERE action LIKE 'refused:%'"))
    }
}
