package com.example.shop

import com.countryatv.core.reports.ExportRequest
import com.countryatv.core.reports.HistoryPage
import com.countryatv.core.reports.ReportDto
import com.countryatv.core.reports.ReportSummary
import com.countryatv.core.reports.UnitFilter
import java.io.File

// A range as the app asks for it (reports spec §5): a preset the shop system resolves, or two shop
// dates (yyyy-MM-dd, inclusive). The phone never works out where a week starts.
data class RangeQuery(val preset: String? = null, val from: String? = null, val to: String? = null) {
    companion object {
        fun preset(key: String) = RangeQuery(preset = key)
        fun dates(from: String, to: String) = RangeQuery(from = from, to = to)
    }
}

// GET /units/query (server UnitQueryPage). items use the list shape: no one's id for people
// without See Reports.
data class UnitPage(
    val total: Long,
    val items: List<UnitDto>,
    val next: String? = null,
    val asOf: String,
)

// What the Reports and My history screens ask the shop system for. Online only (D-RS3): nothing
// here is saved on the phone. ShopClient is the one implementation; tests pass fixtures.
interface ReportsApi {
    suspend fun reportSummary(): ShopResult<ReportSummary>
    suspend fun report(key: String, range: RangeQuery): ShopResult<ReportDto>
    // The row's drill filter, sent back unchanged, so the list has exactly the row's count.
    suspend fun unitQuery(filter: UnitFilter, cursor: String? = null, limit: Int = 30): ShopResult<UnitPage>
    // personId null: the caller's own history (/me/history). Later pages send page 1's asOf back.
    suspend fun history(personId: String?, range: RangeQuery, asOf: String? = null, cursor: String? = null): ShopResult<HistoryPage>
    // Streams the CSV into [dir] under the server's file name.
    suspend fun exportCsv(key: String, request: ExportRequest, dir: File): ShopResult<File>
}
