package com.countryatv.server

import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

// Time comes from one injected clock, so time rules can be tested without waiting.
class ClockTest {
    private val shop = TestShop()

    @Test
    fun `stored times come from the server's clock`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        shop.clock.set("2026-10-27T19:15:00Z")
        val saved = client.createUnit(s, unit()).body<UnitSaved>()
        assertEquals(shop.clock.instant(), java.time.Instant.parse(saved.unit.createdAt))
        assertEquals(shop.clock.instant(), java.time.Instant.parse(shop.single("SELECT at FROM audit_log WHERE action = 'unit.create'")!!))
    }

    @Test
    fun `a session ends after 12 hours and a lockout after 5 minutes`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        shop.clock.advance(Duration.ofHours(12).minusSeconds(1))
        assertEquals(HttpStatusCode.OK, client.authedGet(s, "/units").status)
        shop.clock.advance(Duration.ofSeconds(2))
        assertEquals(HttpStatusCode.Unauthorized, client.authedGet(s, "/units").status)

        val device = shop.auth.enrollDevice(shop.auth.newSetupCode("test"), "Phone")
        val deviceId = shop.auth.deviceIdFor(device)
        repeat(5) { runCatching { shop.auth.login(deviceId, "Sam", "0000") } }
        val locked = runCatching { shop.auth.login(deviceId, "Sam", "1234") }.exceptionOrNull() as ApiProblem
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        shop.clock.advance(Duration.ofMinutes(5).plusSeconds(1))
        shop.auth.login(deviceId, "Sam", "1234")
    }
}
