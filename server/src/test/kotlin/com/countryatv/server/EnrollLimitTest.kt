package com.countryatv.server

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Guessing at setup codes is slowed down per network address, and a lockout is recorded once.
class EnrollLimitTest {
    private val shop = TestShop()

    // The proxy in front of the server puts the caller's address last in X-Forwarded-For.
    private suspend fun HttpClient.enroll(code: String, from: String? = "203.0.113.7"): HttpResponse = post("/auth/enroll") {
        if (from != null) header("X-Forwarded-For", from)
        contentType(ContentType.Application.Json); setBody(EnrollRequest(code, "Phone"))
    }

    @Test
    fun `five wrong setup codes lock that address out, even with the right code, until 15 minutes pass`() = testApplication {
        val client = shop.client(this)
        val code = shop.auth.newSetupCode("test")

        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, client.enroll("WRONG$it").status) }
        val locked = client.enroll(code)
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertTrue(locked.bodyAsText().contains("SETUP_CODE_LOCKED"))
        assertEquals(0, shop.count("SELECT COUNT(*) FROM devices"))
        // The right code wasn't spent by the refused try.
        assertNull(shop.single("SELECT used_at FROM setup_codes"))

        shop.clock.advance(Duration.ofMinutes(14))
        assertEquals(HttpStatusCode.TooManyRequests, client.enroll(code).status)
        shop.clock.advance(Duration.ofMinutes(1))
        assertEquals(HttpStatusCode.OK, client.enroll(code).status)
        assertEquals(1, shop.count("SELECT COUNT(*) FROM devices"))
    }

    @Test
    fun `a lockout is recorded once, and tries while locked out add nothing`() = testApplication {
        val client = shop.client(this)

        repeat(25) { client.enroll("WRONG$it") }

        assertEquals(
            List(4) { "refused:SETUP_CODE_INVALID" } + listOf("enroll.lockout", "refused:SETUP_CODE_INVALID"),
            shop.column("SELECT action FROM audit_log WHERE action LIKE 'refused:%' OR action LIKE 'enroll.%' ORDER BY id")
        )
        assertEquals("""{"until":"2026-10-27T16:15:00.000Z"}""", shop.single("SELECT after_json FROM audit_log WHERE action = 'enroll.lockout'"))
        // The address stays out of the shop's records.
        assertEquals(0, shop.count("SELECT COUNT(*) FROM audit_log WHERE COALESCE(after_json,'') LIKE '%203.0.113.7%' OR entity_id LIKE '%203.0.113.7%'"))
    }

    @Test
    fun `one address being locked out doesn't stop another`() = testApplication {
        val client = shop.client(this)
        repeat(5) { client.enroll("WRONG$it", from = "203.0.113.7") }
        assertEquals(HttpStatusCode.TooManyRequests, client.enroll("WRONG", from = "203.0.113.7").status)

        assertEquals(HttpStatusCode.OK, client.enroll(shop.auth.newSetupCode("test"), from = "198.51.100.20").status)
    }

    @Test
    fun `only the address the proxy added counts, not ones the caller claims`() = testApplication {
        val client = shop.client(this)
        // A guesser sends a different made-up address each time; the proxy appends the real one.
        repeat(5) { client.enroll("WRONG$it", from = "10.0.0.$it, 203.0.113.7") }

        assertEquals(HttpStatusCode.TooManyRequests, client.enroll(shop.auth.newSetupCode("test"), from = "10.9.9.9, 203.0.113.7").status)
    }

    @Test
    fun `a code that works clears the address's wrong tries`() = testApplication {
        val client = shop.client(this)
        repeat(4) { client.enroll("WRONG$it") }
        assertEquals(HttpStatusCode.OK, client.enroll(shop.auth.newSetupCode("test")).status)

        repeat(4) { assertEquals(HttpStatusCode.Unauthorized, client.enroll("WRONG$it").status) }
        assertEquals(HttpStatusCode.OK, client.enroll(shop.auth.newSetupCode("test")).status)
    }

    @Test
    fun `wrong tries older than 15 minutes stop counting`() = testApplication {
        val client = shop.client(this)
        repeat(4) { client.enroll("WRONG$it") }
        shop.clock.advance(Duration.ofMinutes(15))
        repeat(4) { assertEquals(HttpStatusCode.Unauthorized, client.enroll("WRONG$it").status) }

        assertEquals(HttpStatusCode.OK, client.enroll(shop.auth.newSetupCode("test")).status)
    }

    @Test
    fun `without a proxy header, tries are counted against the address that connected`() = testApplication {
        val client = shop.client(this)
        repeat(5) { client.enroll("WRONG$it", from = null) }

        assertEquals(HttpStatusCode.TooManyRequests, client.enroll(shop.auth.newSetupCode("test"), from = null).status)
    }
}
