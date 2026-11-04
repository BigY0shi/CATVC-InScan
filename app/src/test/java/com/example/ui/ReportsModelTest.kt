package com.example.ui

import com.countryatv.core.reports.FieldChange
import com.countryatv.core.reports.HistoryEvent
import com.countryatv.core.reports.HistoryKinds
import com.countryatv.core.reports.HistoryUnit
import com.countryatv.core.reports.ReportKeys
import com.countryatv.core.reports.ReportSummary
import com.countryatv.core.reports.SummaryFigure
import com.countryatv.core.reports.Tile
import com.example.shop.RangeQuery
import com.example.ui.reports.NOT_TRACKED
import com.example.ui.reports.Presets
import com.example.ui.reports.RangeShown
import com.example.ui.reports.dollars
import com.example.ui.reports.eventRow
import com.example.ui.reports.guessRange
import com.example.ui.reports.presetDates
import com.example.ui.reports.rangeDates
import com.example.ui.reports.reportList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

// The words and ranges the Reports and My history screens show (reports spec §3 to §5, my-history §2).
class ReportsModelTest {
    private val today = LocalDate.of(2026, 10, 30) // Friday
    private val zone = ZoneId.of("America/Los_Angeles")

    @Test fun `preset rows carry their real dates`() {
        fun line(p: String) = presetDates(p, today).let { rangeDates(p, it.first, it.second, today) }
        assertEquals("Fri 30 Oct", line(Presets.TODAY))
        assertEquals("Mon 26 Oct to today", line(Presets.THIS_WEEK))
        assertEquals("Mon 19 Oct to Sun 25 Oct", line(Presets.LAST_WEEK))
        assertEquals("1 Oct to today", line(Presets.THIS_MONTH))
        assertEquals("1 Oct to 30 Oct", line(Presets.LAST_30_DAYS))
    }

    @Test fun `steppers step by the range's own unit and stop at today`() {
        val thisWeek = guessRange(RangeQuery.preset(Presets.THIS_WEEK), today)
        assertEquals("week", thisWeek.stepWord())
        assertNull(thisWeek.next(today))
        assertEquals(RangeQuery.preset(Presets.LAST_WEEK), thisWeek.previous())
        val lastWeek = guessRange(RangeQuery.preset(Presets.LAST_WEEK), today)
        assertEquals(RangeQuery.dates("2026-10-12", "2026-10-18"), lastWeek.previous())
        assertEquals(RangeQuery.preset(Presets.THIS_WEEK), lastWeek.next(today))
        val twoBack = RangeShown(RangeQuery.dates("2026-10-12", "2026-10-18"), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18))
        assertEquals("Week of 12 Oct", twoBack.title(today))
        assertEquals(RangeQuery.dates("2026-10-19", "2026-10-25"), twoBack.next(today))
        val yesterday = guessRange(RangeQuery.preset(Presets.TODAY), today).previous()
        assertEquals(RangeQuery.dates("2026-10-29", "2026-10-29"), yesterday)
        assertEquals("Yesterday", guessRange(yesterday, today).title(today))
        assertEquals(RangeQuery.preset(Presets.TODAY), guessRange(yesterday, today).next(today))
    }

    @Test fun `the list says not tracked yet for reports the shop system doesn't build, never 0`() {
        val s = ReportSummary("2026-10-30T16:38:00.000Z", Presets.THIS_WEEK, "2026-10-26", "2026-10-30", listOf(
            SummaryFigure(ReportKeys.CHECK_INS, "period", Tile("check_ins", 14)),
            SummaryFigure(ReportKeys.TIME_TO_IN_STOCK, "period", Tile("usual_minutes", 118)),
            SummaryFigure(ReportKeys.ASKING_PRICE_TOTAL, "snapshot", Tile("asking_price", 31_840_000)),
        ), notBuilt = ReportKeys.NOT_BUILT)
        val rows = reportList(s).flatMap { it.rows }.associateBy { it.key }
        assertEquals("14 this week", rows.getValue(ReportKeys.CHECK_INS).figure)
        assertEquals("Usually within 2 hours", rows.getValue(ReportKeys.TIME_TO_IN_STOCK).figure)
        assertEquals("$318,400", rows.getValue(ReportKeys.ASKING_PRICE_TOTAL).figure)
        for (k in listOf("aging", "missing_details", "days_to_sell", "shop_system")) {
            assertEquals(NOT_TRACKED, rows.getValue(k).figure); assertFalse(rows.getValue(k).opens)
        }
        assertEquals("$9,499", dollars(949_900))
    }

    @Test fun `history rows use the action table's words`() {
        val u = HistoryUnit("u1", "5NVA9HQC", 2026, "Polaris", "Sportsman 570")
        fun e(kind: String, changes: List<FieldChange> = emptyList(), since: Boolean = false, tag: String? = null, reason: String? = null) =
            HistoryEvent(1, kind, "2026-10-29T21:14:00.000Z", u, tag, changes, since, reason = reason)
        val price = eventRow(e(HistoryKinds.EDIT, listOf(FieldChange("price", "989900", "949900"))), zone)
        assertEquals("Changed the price", price.action); assertEquals("$9,899 → $9,499", price.note); assertEquals("2:14 pm", price.time)
        assertEquals("Changed the price, $9,899 to $9,499, 2026 Polaris Sportsman 570, 2:14 pm, ID 5 N V A 9 H Q C.", price.spoken)
        assertEquals("No tag yet", eventRow(e(HistoryKinds.CHECK_IN), zone).note)
        assertEquals("Tag printed 2:17 pm", eventRow(e(HistoryKinds.CHECK_IN, tag = "2026-10-29T21:17:00.000Z"), zone).note)
        assertEquals("Reason: damaged", eventRow(e(HistoryKinds.REPRINT, reason = "damaged"), zone).note)
        assertEquals("Changed the details", eventRow(e(HistoryKinds.EDIT, listOf(FieldChange("make", "A", "B"), FieldChange("price", "1", "2"))), zone).action)
        assertEquals("Printed the tag", eventRow(e(HistoryKinds.PRINTED_TAG), zone).action)
        assertEquals("Changed the status", eventRow(e(HistoryKinds.STATUS_CHANGE), zone).action)
    }
}
