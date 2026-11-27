package com.countryatv.server

import io.ktor.http.HttpStatusCode
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// How long a setup code waits to be used: 24 hours unless the server console asks for longer.
class SetupCodeLifeTest {
    private val shop = TestShop()

    private fun refused(code: String) = assertFailsWith<ApiProblem> { shop.auth.enrollDevice(code, "Phone") }.status

    @Test
    fun `a code made without a time lasts 24 hours`() {
        val stillGood = shop.auth.newSetupCode("test")
        val tooLate = shop.auth.newSetupCode("test")
        shop.clock.advance(Duration.ofHours(24).minusSeconds(1))
        shop.auth.enrollDevice(stillGood, "Phone")
        shop.clock.advance(Duration.ofSeconds(2))
        assertEquals(HttpStatusCode.Unauthorized, refused(tooLate))
    }

    @Test
    fun `a code made for a week lasts a week and no longer`() {
        val stillGood = shop.auth.newSetupCode("test", 168)
        val tooLate = shop.auth.newSetupCode("test", 168)
        shop.clock.advance(Duration.ofDays(7).minusSeconds(1))
        shop.auth.enrollDevice(stillGood, "Phone")
        shop.clock.advance(Duration.ofSeconds(2))
        assertEquals(HttpStatusCode.Unauthorized, refused(tooLate))
    }

    @Test
    fun `a longer code still works only once`() {
        val code = shop.auth.newSetupCode("test", 168)
        shop.auth.enrollDevice(code, "Phone")
        assertEquals(HttpStatusCode.Unauthorized, refused(code))
    }

    @Test
    fun `a time outside 1 hour to 30 days is refused and no code is saved`() {
        assertFailsWith<IllegalArgumentException> { shop.auth.newSetupCode("test", 0) }
        assertFailsWith<IllegalArgumentException> { shop.auth.newSetupCode("test", MAX_SETUP_CODE_HOURS + 1) }
        assertEquals(0, shop.count("SELECT COUNT(*) FROM setup_codes"))
    }

    @Test
    fun `the console reads the hours after setup-code`() {
        assertEquals(24, setupCodeHours(emptyList()))
        assertEquals(168, setupCodeHours(listOf("168")))
        assertEquals(720, setupCodeHours(listOf("720")))
        assertNull(setupCodeHours(listOf("0")))
        assertNull(setupCodeHours(listOf("721")))
        assertNull(setupCodeHours(listOf("week")))
        assertNull(setupCodeHours(listOf("7", "days")))
    }

    @Test
    fun `the console says how long the code lasts`() {
        assertEquals("24 hours", hoursInWords(24))
        assertEquals("1 hour", hoursInWords(1))
        assertEquals("36 hours", hoursInWords(36))
        assertEquals("168 hours (7 days)", hoursInWords(168))
    }
}
