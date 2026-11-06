package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.Problem
import com.countryatv.core.UnitStatus
import com.countryatv.core.reports.ExportRequest
import com.countryatv.core.reports.ReportKeys
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter

// CSV for spreadsheets (reports spec §8): UTF-8 with one byte-order mark, CRLF line ends, RFC 4180
// quoting. A text cell that a spreadsheet would run as a formula (starting =, +, -, @, tab or
// carriage return) gets a leading ' first, and only then is quoted. Numbers are written as numbers.
object CsvWriter {
    const val BOM = "﻿"

    sealed interface Cell
    data class Text(val value: String?) : Cell
    data class Num(val value: String) : Cell

    fun text(value: String?) = Text(value)
    fun num(value: Long?) = value?.let { Num(it.toString()) } ?: Text(null)
    fun money(cents: Long?) = cents?.let { Num(BigDecimal.valueOf(it, 2).toPlainString()) } ?: Text(null)

    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

    fun cell(c: Cell): String = when (c) {
        is Num -> c.value
        is Text -> {
            val raw = c.value ?: ""
            val guarded = if (raw.isNotEmpty() && raw[0] in FORMULA_START) "'$raw" else raw
            if (guarded.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"" + guarded.replace("\"", "\"\"") + "\"" else guarded
        }
    }

    fun write(header: List<String>, rows: List<List<Cell>>): String = buildString {
        append(BOM)
        append(header.joinToString(",") { cell(Text(it)) }).append("\r\n")
        for (r in rows) append(r.joinToString(",") { cell(it) }).append("\r\n")
    }
}

data class ExportFile(val name: String, val body: ByteArray, val rows: Int)

// POST /reports/{report}/export. Check-ins only for now: "each" (one row per check-in) or "table"
// (one breakdown, with shares). The rows are built in memory, the report.export audit row is
// written with the real count, and only then is the file sent: streaming while holding the shop's
// one connection would stall every check-in during a slow download.
class ReportExporter(private val db: Database, private val clock: Clock, private val reports: ReportService) {

    private val HEADER = listOf("Checked in (shop time)", "Tag ID", "Status now", "Type", "Make", "Model", "Year", "Colour", "VIN",
        "Serial number", "Asking price", "Checked in by", "Phone")

    fun export(key: String, req: ExportRequest, caller: Caller): ExportFile {
        if (key !in ReportKeys.BUILT) throw ApiProblem(HttpStatusCode.NotFound, Problem.REPORT_NOT_FOUND)
        if (key != ReportKeys.CHECK_INS) throw ApiProblem(HttpStatusCode.NotFound, Problem.EXPORT_NOT_AVAILABLE)
        if (req.rows != "each" && req.rows != "table") throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("rows"))
        val built = reports.withReport(key, req.preset, req.from, req.to) { ctx, range, dto ->
            val fromTo = "${range.range.from}-to-${range.range.to}"
            if (req.rows == "each") {
                val cohort = reports.checkInCohort(ctx, range.range).copy(sort = com.countryatv.core.reports.UnitFilter.SORT_OLDEST_CHECKED_IN)
                val time = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withZone(ctx.calendar.zone)
                val phone = Sql("(SELECT d.label FROM audit_log a JOIN devices d ON d.id = a.device_id WHERE a.entity_id = u.id AND a.action = 'unit.create' ORDER BY a.id LIMIT 1) AS phone")
                val rows = UnitQuery.rows(ctx.c, cohort, null, null, phone) { rs ->
                    val u = rs.toUnitDto()
                    listOf(
                        CsvWriter.text(time.format(Instant.parse(u.checkedInAt))),
                        CsvWriter.text(ItemId.shortForm(u.id)),
                        CsvWriter.text(UnitStatus.entries.firstOrNull { it.name == u.status }?.label ?: u.status),
                        CsvWriter.text(u.unitType), CsvWriter.text(u.make),
                        CsvWriter.text(listOf(u.model, u.subModel).filter { it.isNotBlank() }.joinToString(" ")),
                        CsvWriter.num(u.year?.toLong()), CsvWriter.text(u.color), CsvWriter.text(u.vin),
                        // No serial number field exists yet; the column stays empty rather than guessed.
                        CsvWriter.text(null),
                        CsvWriter.money(u.priceCents), CsvWriter.text(ctx.name(u.createdBy)), CsvWriter.text(rs.getString("phone"))
                    )
                }
                Triple("inscan-check-ins-$fromTo.csv", CsvWriter.write(HEADER, rows), rows.size) to range
            } else {
                val by = req.by ?: throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("by"))
                val breakdown = dto.breakdowns.firstOrNull { it.key == by } ?: throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("by"))
                val total = dto.tiles.first().value ?: 0
                val rows = breakdown.rows.map { r ->
                    val n = r.count ?: 0
                    val share = if (total == 0L) null else BigDecimal.valueOf(n * 1000 / total.toDouble()).setScale(0, RoundingMode.HALF_UP).movePointLeft(1)
                    listOf(CsvWriter.text(r.label), CsvWriter.num(n), share?.let { CsvWriter.Num(it.toPlainString()) } ?: CsvWriter.text(null))
                }
                val keyWord = by.replaceFirstChar { it.uppercase() }
                Triple("inscan-check-ins-by-$by-$fromTo.csv", CsvWriter.write(listOf(keyWord, "Check-ins", "Share (%)"), rows), rows.size) to range
            }
        }
        val (file, range) = built
        val (name, text, count) = file
        db.transaction { c ->
            audit(c, clock, caller.employeeId, caller.deviceId, "report.export", "report", key, null, buildJsonObject {
                put("report", key)
                put("from", range.range.from.toString())
                put("to", range.range.to.toString())
                put("rows", req.rows)
                put("count", count)
                req.by?.let { put("by", it) }
            }.toString())
        }
        return ExportFile(name, text.toByteArray(Charsets.UTF_8), count)
    }
}
