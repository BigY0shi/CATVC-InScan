package com.countryatv.core.reports

import kotlinx.serialization.Serializable

// Which units a list shows. Reports build one for every row that counts units (the row's "drill"),
// and the app sends it back unchanged to GET /units/query, so the list always has exactly the
// row's count (reports spec §7). The server turns a filter into SQL with one function, used both
// for /units/query and for every report's breakdown, so the two can't disagree.
//
// Every field is optional; a null field doesn't filter. Times are fixed-width UTC instants
// ("2026-10-26T07:00:00.000Z"); ranges are [from, to).
@Serializable
data class UnitFilter(
    // The shop system as it was at this server time: units it had heard of by then, with the status
    // each had then. Reports set it to their own asOf, so a unit sold since the report loaded still
    // appears under the status it was counted in. /units/query pins it on page 1 when it's null.
    val asOf: String? = null,
    // Status names (INCOMING, IN_STOCK, SALE_PENDING, SOLD, RETIRED), as of asOf.
    val statuses: List<String>? = null,
    // Type labels as stored ("Dirt Bike").
    val unitTypes: List<String>? = null,
    // Makes, compared ignoring case.
    val makes: List<String>? = null,
    // false leaves out records from the legacy import, which are nobody's check-in.
    val imported: Boolean? = null,
    // When it was checked in on the phone.
    val checkedInFrom: String? = null,
    val checkedInTo: String? = null,
    // Person filters (an employee id). Each needs "See Reports", except checkedInBy set to the
    // caller's own id ("Only units checked in by you").
    val checkedInBy: String? = null,
    // true: units whose first move from Incoming to In stock is recorded (as of asOf).
    val movedToInStock: Boolean? = null,
    // Who made that first move to In stock. Person filter.
    val confirmedBy: String? = null,
    // Units marked Sold in [soldFrom, soldTo). Imports don't count as sales.
    val soldFrom: String? = null,
    val soldTo: String? = null,
    // Who made the latest sale in that range. Person filter; needs soldFrom and soldTo.
    val soldBy: String? = null,
    // true: units with at least one printed tag (as of asOf).
    val hasTag: Boolean? = null,
    // When the unit's newest tag was printed.
    val newestTagPrintedFrom: String? = null,
    val newestTagPrintedTo: String? = null,
    // Who printed the unit's newest tag. Person filter.
    val tagPrintedBy: String? = null,
    // newest_checked_in (default) or oldest_checked_in. The id breaks ties.
    val sort: String = SORT_NEWEST_CHECKED_IN
) {
    // The person filters that are set, by field name.
    fun personFilters(): Map<String, String> = buildMap {
        checkedInBy?.let { put("checkedInBy", it) }
        confirmedBy?.let { put("confirmedBy", it) }
        soldBy?.let { put("soldBy", it) }
        tagPrintedBy?.let { put("tagPrintedBy", it) }
    }

    companion object {
        const val SORT_NEWEST_CHECKED_IN = "newest_checked_in"
        const val SORT_OLDEST_CHECKED_IN = "oldest_checked_in"
        val SORTS = setOf(SORT_NEWEST_CHECKED_IN, SORT_OLDEST_CHECKED_IN)
    }
}
