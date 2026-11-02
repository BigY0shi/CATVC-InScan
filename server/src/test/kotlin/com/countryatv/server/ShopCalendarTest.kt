package com.countryatv.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Shop dates become UTC instants in the shop's zone, with the shop's week start.
// Sabotage check (run by hand): construct these calendars with ZoneOffset.UTC and they go red.
class ShopCalendarTest {
    private val pacific = ZoneId.of("America/Los_Angeles")
    private fun at(instant: String) = MutableClock(Instant.parse(instant))
    private fun cal(now: String, weekStart: DayOfWeek = DayOfWeek.MONDAY) = ShopCalendar(pacific, weekStart, at(now))
    private fun d(s: String) = LocalDate.parse(s)
    private fun hours(r: ShopRange) = Duration.between(r.startUtc, r.endUtc).toHours()

    @Test
    fun `the day the clocks go back is 25 hours and the day they go forward is 23`() {
        val c = cal("2026-11-04T20:00:00Z")
        val fallBack = c.range(d("2026-11-01"), d("2026-11-01"))
        assertEquals(Instant.parse("2026-11-01T07:00:00Z"), fallBack.startUtc)
        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), fallBack.endUtc)
        assertEquals(25, hours(fallBack))
        val springForward = c.range(d("2027-03-14"), d("2027-03-14"))
        assertEquals(Instant.parse("2027-03-14T08:00:00Z"), springForward.startUtc)
        assertEquals(23, hours(springForward))
    }

    @Test
    fun `today is the shop's date, not UTC's`() {
        // 11:30 pm Pacific on Sat 31 Oct is already Sunday in UTC.
        val c = cal("2026-11-01T06:30:00Z")
        assertEquals(d("2026-10-31"), c.today())
        assertEquals(Instant.parse("2026-10-31T07:00:00Z"), c.range(RangePreset.TODAY).startUtc)
    }

    @Test
    fun `this week and last week follow the week start, including Sunday-start shops`() {
        val wed = "2026-11-04T20:00:00Z" // Wed 4 Nov, noon Pacific
        assertEquals(d("2026-11-02") to d("2026-11-04"), cal(wed).range(RangePreset.THIS_WEEK).let { it.from to it.to })
        assertEquals(d("2026-11-01") to d("2026-11-04"), cal(wed, DayOfWeek.SUNDAY).range(RangePreset.THIS_WEEK).let { it.from to it.to })
        val last = cal(wed).range(RangePreset.LAST_WEEK)
        assertEquals(d("2026-10-26") to d("2026-11-01"), last.from to last.to)
        assertEquals(7 * 24 + 1, hours(last), "last week holds the 25-hour Sunday")
        // On a Sunday, a Monday-start week began six days ago.
        assertEquals(d("2026-10-26"), cal("2026-11-01T20:00:00Z").range(RangePreset.THIS_WEEK).from)
        assertEquals(d("2026-11-01"), cal("2026-11-01T20:00:00Z", DayOfWeek.SUNDAY).range(RangePreset.THIS_WEEK).from)
    }

    @Test
    fun `this month and the last 30 days count today`() {
        val c = cal("2026-11-01T20:00:00Z")
        assertEquals(d("2026-11-01") to d("2026-11-01"), c.range(RangePreset.THIS_MONTH).let { it.from to it.to })
        val thirty = c.range(RangePreset.LAST_30_DAYS)
        assertEquals(d("2026-10-03") to d("2026-11-01"), thirty.from to thirty.to)
        assertEquals(30, thirty.days)
    }

    @Test
    fun `charts bucket by range length, and day buckets carry the DST day's real length`() {
        val c = cal("2026-11-04T20:00:00Z")
        fun size(days: Long) = c.bucketSize(c.range(d("2026-01-01"), d("2026-01-01").plusDays(days - 1)))
        assertEquals(BucketSize.NONE, size(1))
        assertEquals(BucketSize.DAY, size(7))
        assertEquals(BucketSize.DAY, size(14))
        assertEquals(BucketSize.WEEK, size(15))
        assertEquals(BucketSize.WEEK, size(112))
        assertEquals(BucketSize.MONTH, size(113))

        val week = c.buckets(c.range(d("2026-10-26"), d("2026-11-01")))
        assertEquals(7, week.size)
        assertEquals(listOf(24L, 24, 24, 24, 24, 24, 25), week.map { Duration.between(it.startUtc, it.endUtc).toHours() })

        val weeks = c.buckets(c.range(d("2026-10-01"), d("2026-11-04")))
        assertEquals(d("2026-10-01") to d("2026-10-04"), weeks.first().let { it.from to it.to }, "the first week is cut to the range")
        assertEquals(d("2026-11-02") to d("2026-11-04"), weeks.last().let { it.from to it.to })
        weeks.zipWithNext().forEach { (a, b) -> assertEquals(a.endUtc, b.startUtc, "no gaps or overlaps") }
    }
}

class ShopSettingsTest {
    @Test
    fun `defaults hold until changed, bad values are refused, and every change is audited`() {
        val shop = TestShop()
        val settings = ShopSettings(shop.db, shop.clock)
        assertEquals("America/Los_Angeles", settings.get(Setting.TIME_ZONE))
        assertEquals(DayOfWeek.MONDAY, settings.weekStart())
        assertEquals("90", settings.get(Setting.OLD_STOCK_DAYS))
        assertEquals("13", settings.get(Setting.KEEP_EVENTS_MONTHS))
        assertEquals("200", settings.get(Setting.PHONE_SOLD_UNITS_KEPT))
        assertEquals("IN_STOCK", settings.get(Setting.STOCK_OPENS_ON))

        settings.set(Setting.WEEK_START, "SUNDAY", "owner", null)
        assertEquals(DayOfWeek.SUNDAY, settings.calendar().weekStart)
        val bad = assertFailsWith<ApiProblem> { settings.set(Setting.TIME_ZONE, "Mars/Olympus", "owner", null) }
        assertEquals(HttpStatusCode.UnprocessableEntity, bad.status)
        assertEquals("America/Los_Angeles", settings.get(Setting.TIME_ZONE))
        assertEquals(1, shop.count("SELECT COUNT(*) FROM audit_log WHERE action = 'setting.change' AND entity_id = 'shop.weekStart'"))
        assertEquals("""{"value":"MONDAY"}""", shop.single("SELECT before_json FROM audit_log WHERE action = 'setting.change'"))
    }

    @Test
    fun `the oldest believable queued change follows its setting`() = testApplication {
        val shop = TestShop(MutableClock(Instant.parse("2026-11-02T16:00:00Z")))
        ShopSettings(shop.db, shop.clock).set(Setting.MAX_QUEUE_AGE_DAYS, "7", "owner", null)
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val u = unit().copy(occurredAt = "2026-10-23T16:00:00Z")
        client.createUnit(s, u)
        assertEquals("2026-10-26T16:00:00.000Z", shop.single("SELECT checked_in_at FROM units WHERE id = ?", u.id))
    }
}
