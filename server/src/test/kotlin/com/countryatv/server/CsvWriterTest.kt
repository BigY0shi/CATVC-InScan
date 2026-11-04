package com.countryatv.server

import com.countryatv.core.reports.UnitFilter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Reports spec §8 file rules, without Ktor.
class CsvWriterTest {
    @Test
    fun `a formula is guarded, then quoted`() {
        assertEquals("\"'=a,b\"", CsvWriter.cell(CsvWriter.text("=a,b")))
        assertEquals("'@SUM(A1)", CsvWriter.cell(CsvWriter.text("@SUM(A1)")), "a phone named @SUM(A1)")
        for (start in listOf("+1", "-1", "\tx")) assertEquals("'$start", CsvWriter.cell(CsvWriter.text(start)))
        assertEquals("\"'\rx\"", CsvWriter.cell(CsvWriter.text("\rx")))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.cell(CsvWriter.text("say \"hi\"")))
    }

    @Test
    fun `numbers are written as numbers and never guarded`() {
        assertEquals("-12", CsvWriter.cell(CsvWriter.num(-12)))
        assertEquals("8499.00", CsvWriter.cell(CsvWriter.money(849_900)))
        assertEquals("", CsvWriter.cell(CsvWriter.num(null)))
    }

    @Test
    fun `one byte-order mark and CRLF line ends`() {
        val text = CsvWriter.write(listOf("A", "B"), listOf(listOf(CsvWriter.text("x"), CsvWriter.num(1)), listOf(CsvWriter.text("y"), CsvWriter.num(2))))
        assertEquals("﻿A,B\r\nx,1\r\ny,2\r\n", text)
        assertEquals(1, text.count { it == '﻿' })
    }
}

// The one documented way to put a filter in a query string: ?filter=<UnitFilter as JSON>.
class UnitFilterParamTest {
    @Test
    fun `a filter survives the round trip, and an unknown field is refused`() {
        val f = UnitFilter(asOf = "2026-11-04T20:00:00.000Z", statuses = listOf("INCOMING"), makes = listOf("polaris"), checkedInBy = "X", sort = UnitFilter.SORT_OLDEST_CHECKED_IN)
        assertEquals(f, parseFilterParam(Json { encodeDefaults = true }.encodeToString(f)))
        assertEquals(f, parseFilterParam(Json.encodeToString(f)), "defaults may be left out")
        assertEquals(UnitFilter(), parseFilterParam(null))
        assertFailsWith<ApiProblem> { parseFilterParam("""{"status":["INCOMING"]}""") }
    }
}
