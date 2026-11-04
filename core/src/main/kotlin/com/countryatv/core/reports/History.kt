package com.countryatv.core.reports

import kotlinx.serialization.Serializable

// What one person did, as GET /me/history and GET /people/{employeeId}/history return it
// (my-history spec §2 and §7). It never carries another employee's id or name.
@Serializable
data class HistoryPage(
    // The server time page 1 was built at. Later pages send it back with the cursor, so a change
    // that arrives in between can't land behind the cursor or shift the next page.
    val asOf: String,
    val preset: String? = null,
    // The range as shop dates, both inclusive.
    val from: String,
    val to: String,
    // Whose work this is. Only on /people/{employeeId}/history; null on /me/history.
    val person: PersonRef? = null,
    // The tiles and the "Also" line. Page 1 only.
    val totals: HistoryTotals? = null,
    // Newest first: by occurredAt, then eventId, both descending.
    val events: List<HistoryEvent>,
    // Send back as ?cursor= with the same asOf for the next page; null on the last page.
    val next: String? = null
)

@Serializable
data class PersonRef(val id: String, val displayName: String, val active: Boolean)

@Serializable
data class HistoryTotals(
    val checkIns: Long,
    // Of those check-ins, how many are still Incoming (as of asOf).
    val stillIncoming: Long,
    // Tag confirms that moved a unit from Incoming to In stock: the Work by person column.
    val movedToInStock: Long,
    val edits: Long,
    val reprints: Long
)

// kind, with the words the app shows (my-history spec §2):
//   check_in            Checked in (tagPrintedAt: the first tag, or null for "No tag yet")
//   reprint             Reprinted the tag (reason)
//   printed_tag         Printed the tag (the first tag of a unit someone else checked in)
//   moved_to_in_stock   Moved to In stock (a tag confirm that moved it)
//   confirmed_new_tag   Confirmed the new tag (a confirm on a unit already In stock)
//   edit                Changed the price / colour / VIN / details (changes)
//   marked_sold         Marked sold (priceCents: asking price at the time)
//   brought_back        Brought it back in (Sold to In stock)
//   status_change       Any other status change (fromStatus, toStatus, reason)
// Imports are never events.
@Serializable
data class HistoryEvent(
    // The audit row id. Unique; the tiebreak for equal times.
    val eventId: Long,
    val kind: String,
    // When it happened on the phone.
    val occurredAt: String,
    val unit: HistoryUnit,
    val tagPrintedAt: String? = null,
    val changes: List<FieldChange> = emptyList(),
    // True when any changed field's current value no longer matches this edit's "to".
    val changedSince: Boolean = false,
    // An edit that overlapped someone else's (the phone had an older copy).
    val overlapping: Boolean = false,
    val fromStatus: String? = null,
    val toStatus: String? = null,
    val reason: String? = null,
    val priceCents: Long? = null
)

// The unit as it is now. shortId is the 8 characters on the tag.
@Serializable
data class HistoryUnit(val id: String, val shortId: String, val year: Int? = null, val make: String, val model: String, val subModel: String = "", val color: String = "")

// field: price (cents, as text), colour, vin, year, make, model or type. Other fields are never sent.
@Serializable
data class FieldChange(val field: String, val from: String? = null, val to: String? = null, val changedSince: Boolean = false)

object HistoryKinds {
    const val CHECK_IN = "check_in"
    const val REPRINT = "reprint"
    const val PRINTED_TAG = "printed_tag"
    const val MOVED_TO_IN_STOCK = "moved_to_in_stock"
    const val CONFIRMED_NEW_TAG = "confirmed_new_tag"
    const val EDIT = "edit"
    const val MARKED_SOLD = "marked_sold"
    const val BROUGHT_BACK = "brought_back"
    const val STATUS_CHANGE = "status_change"
}
