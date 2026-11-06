package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.Problem
import com.countryatv.core.UnitStatus
import com.countryatv.core.reports.FieldChange
import com.countryatv.core.reports.HistoryEvent
import com.countryatv.core.reports.HistoryKinds
import com.countryatv.core.reports.HistoryPage
import com.countryatv.core.reports.HistoryTotals
import com.countryatv.core.reports.HistoryUnit
import com.countryatv.core.reports.PersonRef
import com.countryatv.core.reports.UnitFilter
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.sql.Connection
import java.time.Clock
import java.util.Base64

// One person's event, with whose it is. The actor never leaves the server.
data class ActorEvent(val actor: String, val event: HistoryEvent)

// THE classifier (my-history spec §7): turns audit rows into the events people did, folding the
// duplicates. My history, a person's history and Work by person all count with it, so they agree.
// - A check-in and its first tag print, by the same person, are one event.
// - A tag confirm that moved the unit writes tag.confirm and unit.status:IN_STOCK: one event.
// - Imports (unit.import, status changes with source import) are no one's events.
// Only rows the server had by asOf count, so paging against a pinned asOf is stable.
object HistoryClassifier {
    private val json = Json { ignoreUnknownKeys = true }

    // Edits show only these fields; before_json holds other people's ids and notes and never leaves.
    private val EDIT_FIELDS = linkedMapOf(
        "priceCents" to "price", "color" to "colour", "vin" to "vin", "year" to "year",
        "make" to "make", "model" to "model", "unitType" to "type"
    )

    private data class Row(val id: Long, val at: String, val occurredAt: String, val actor: String, val action: String,
                           val entityId: String, val before: String?, val after: String?)

    private data class Change(val unitId: String, val from: String, val to: String, val source: String, val price: Long?)

    private data class Tag(val id: String, val unitId: String, val printedAt: String, val printedBy: String, val reason: String, val first: Boolean)

    fun events(c: Connection, actor: String?, startUtc: String, endUtc: String, asOf: String): List<ActorEvent> {
        val sql = Sql("""SELECT id, at, occurred_at, actor, action, entity_id, before_json, after_json FROM audit_log
                         WHERE occurred_at >= ? AND occurred_at < ? AND at <= ?
                           AND (action IN ('unit.create','unit.update','unit.update.overlapping','tag.print','tag.confirm') OR action LIKE 'unit.status:%')""",
            startUtc, endUtc, asOf)
        if (actor != null) sql.add(" AND actor = ?", actor)
        val rows = c.query(sql) { Row(it.getLong(1), it.getString(2), it.getString(3), it.getString(4), it.getString(5), it.getString(6), it.getString(7), it.getString(8)) }
        if (rows.isEmpty()) return emptyList()

        // The typed rows for the status changes above (same range, same asOf, same person).
        val changeSql = Sql("""SELECT s.audit_id, s.unit_id, s.from_status, s.to_status, s.source, s.price_cents
                               FROM unit_status_changes s JOIN audit_log a ON a.id = s.audit_id
                               WHERE a.occurred_at >= ? AND a.occurred_at < ? AND a.at <= ?""", startUtc, endUtc, asOf)
        if (actor != null) changeSql.add(" AND a.actor = ?", actor)
        val changes = c.query(changeSql) { rs ->
            rs.getLong(1) to Change(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6).takeUnless { rs.wasNull() })
        }.toMap()
        // Scan moves, keyed by unit, actor and the server time, to pair each with its tag.confirm row.
        // A confirm and the move it made share one phone time, so the same range finds them.
        val scanMoves = c.query(Sql("""SELECT unit_id || '|' || actor || '|' || received_at FROM unit_status_changes
                                       WHERE source = 'scan' AND from_status = 'INCOMING' AND to_status = 'IN_STOCK'
                                         AND occurred_at >= ? AND occurred_at < ?""", startUtc, endUtc)) { it.getString(1) }.toSet()

        val tagIds = rows.filter { it.action == "tag.print" || it.action == "tag.confirm" }.map { it.entityId }.toSet()
        val createdUnits = rows.filter { it.action == "unit.create" }.map { it.entityId }.toSet()
        val tags = loadTags(c, tagIds, createdUnits, asOf)
        val unitIds = rows.mapNotNull { r ->
            when (r.action) {
                "tag.print", "tag.confirm" -> tags[r.entityId]?.unitId
                else -> r.entityId
            }
        }.toSet()
        val units = loadUnits(c, unitIds)
        // Units from the legacy import have no check-in, so their first tag isn't folded into one.
        val imported = if (unitIds.isEmpty()) emptySet() else unitIds.chunked(500).flatMap { chunk ->
            c.query(Sql("SELECT id FROM units WHERE imported = 1 AND id IN (${chunk.joinToString(",") { "?" }})", *chunk.toTypedArray())) { it.getString(1) }
        }.toSet()

        return rows.mapNotNull { r -> classify(r, changes, scanMoves, tags, units, imported)?.let { ActorEvent(r.actor, it) } }
            .sortedWith(compareByDescending<ActorEvent> { it.event.occurredAt }.thenByDescending { it.event.eventId })
    }

    private fun classify(r: Row, changes: Map<Long, Change>, scanMoves: Set<String>, tags: Map<String, Tag>, units: Map<String, UnitDto>, imported: Set<String>): HistoryEvent? {
        fun event(kind: String, unitId: String) = units[unitId]?.let { u -> HistoryEvent(r.id, kind, r.occurredAt, u.toHistoryUnit()) }
        return when {
            r.action == "unit.create" -> {
                val u = units[r.entityId] ?: return null
                // The first tag, when the person who checked it in printed it (and the server had it by asOf).
                val first = tags.values.firstOrNull { it.unitId == u.id && it.first && it.printedBy == r.actor }
                event(HistoryKinds.CHECK_IN, u.id)?.copy(tagPrintedAt = first?.printedAt)
            }
            r.action == "tag.print" -> {
                val t = tags[r.entityId] ?: return null
                val u = units[t.unitId] ?: return null
                when {
                    !t.first -> event(HistoryKinds.REPRINT, u.id)?.copy(reason = t.reason.ifBlank { null })
                    u.createdBy == r.actor && u.id !in imported -> null // folded into the check-in
                    else -> event(HistoryKinds.PRINTED_TAG, u.id)
                }
            }
            r.action == "tag.confirm" -> {
                val t = tags[r.entityId] ?: return null
                if ("${t.unitId}|${r.actor}|${r.at}" in scanMoves) null // the move is the event
                else event(HistoryKinds.CONFIRMED_NEW_TAG, t.unitId)
            }
            r.action.startsWith("unit.status:") -> {
                val ch = changes[r.id] ?: fallbackChange(r) ?: return null
                if (ch.source == "import") return null
                val base = event(HistoryKinds.STATUS_CHANGE, r.entityId) ?: return null
                when {
                    ch.source == "scan" && ch.from == UnitStatus.INCOMING.name && ch.to == UnitStatus.IN_STOCK.name ->
                        base.copy(kind = HistoryKinds.MOVED_TO_IN_STOCK, fromStatus = ch.from, toStatus = ch.to)
                    ch.to == UnitStatus.SOLD.name -> base.copy(kind = HistoryKinds.MARKED_SOLD, fromStatus = ch.from, toStatus = ch.to, priceCents = ch.price)
                    ch.from == UnitStatus.SOLD.name -> base.copy(kind = HistoryKinds.BROUGHT_BACK, fromStatus = ch.from, toStatus = ch.to)
                    else -> base.copy(fromStatus = ch.from, toStatus = ch.to, reason = reasonOf(r.after))
                }
            }
            r.action == "unit.update" || r.action == "unit.update.overlapping" -> {
                val u = units[r.entityId] ?: return null
                val changed = editChanges(r.before, r.after, u)
                event(HistoryKinds.EDIT, u.id)?.copy(changes = changed, changedSince = changed.any { it.changedSince },
                    overlapping = r.action == "unit.update.overlapping")
            }
            else -> null
        }
    }

    private fun fallbackChange(r: Row): Change? {
        val before = r.before?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return null
        val after = r.after?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val from = before["status"]?.jsonPrimitive?.contentOrNull ?: return null
        val source = when {
            after?.get("source")?.jsonPrimitive?.contentOrNull == "import" -> "import"
            after?.containsKey("unit") == true -> "manual"
            else -> "scan"
        }
        return Change(r.entityId, from, r.action.removePrefix("unit.status:"), source, before["priceCents"]?.jsonPrimitive?.contentOrNull?.toLongOrNull())
    }

    private fun reasonOf(after: String?): String? = after?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        ?.get("reason")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun editChanges(beforeJson: String?, afterJson: String?, now: UnitDto): List<FieldChange> {
        val before = beforeJson?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return emptyList()
        val after = afterJson?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return emptyList()
        val current = Json.encodeToJsonElement(UnitDto.serializer(), now).jsonObject
        return EDIT_FIELDS.mapNotNull { (key, field) ->
            val from = before.text(key)
            val to = after.text(key)
            if (from == to) null else FieldChange(field, from, to, changedSince = current.text(key) != to)
        }
    }

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    // The tags named by the rows, plus every tag of the check-ins' units (to find their first tag).
    // first: no earlier tag for the unit. Only tags the server had by asOf.
    private fun loadTags(c: Connection, tagIds: Set<String>, unitIds: Set<String>, asOf: String): Map<String, Tag> {
        if (tagIds.isEmpty() && unitIds.isEmpty()) return emptyMap()
        val sql = Sql("""SELECT t.id, t.unit_id, t.printed_at, t.printed_by, t.reprint_reason,
                           NOT EXISTS (SELECT 1 FROM tags t2 WHERE t2.unit_id = t.unit_id AND (t2.printed_at < t.printed_at OR (t2.printed_at = t.printed_at AND t2.rowid < t.rowid))) AS first
                         FROM tags t WHERE EXISTS (SELECT 1 FROM audit_log a WHERE a.entity_id = t.id AND a.action = 'tag.print' AND a.at <= ?) AND (""", asOf)
        val parts = mutableListOf<String>()
        if (tagIds.isNotEmpty()) { parts += "t.id IN (${tagIds.joinToString(",") { "?" }})"; sql.args.addAll(tagIds) }
        if (unitIds.isNotEmpty()) { parts += "t.unit_id IN (${unitIds.joinToString(",") { "?" }})"; sql.args.addAll(unitIds) }
        sql.add(parts.joinToString(" OR ")).add(")")
        return c.query(sql) { Tag(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getString(5), it.getInt(6) == 1) }.associateBy { it.id }
    }

    private fun loadUnits(c: Connection, ids: Set<String>): Map<String, UnitDto> {
        if (ids.isEmpty()) return emptyMap()
        return ids.chunked(500).flatMap { chunk ->
            c.query(Sql("SELECT * FROM units WHERE id IN (${chunk.joinToString(",") { "?" }})", *chunk.toTypedArray())) { it.toUnitDto() }
        }.associateBy { it.id }
    }

    private fun UnitDto.toHistoryUnit() = HistoryUnit(id, ItemId.shortForm(id), year, make, model, subModel, color)
}

// GET /me/history and GET /people/{employeeId}/history.
class HistoryService(private val db: Database, private val clock: Clock, private val settings: ShopSettings) {
    companion object { const val PAGE_SIZE = 30 }

    fun page(employeeId: String, preset: String?, from: String?, to: String?, asOfParam: String?, cursor: String?, withPerson: Boolean): HistoryPage {
        if (cursor != null && asOfParam == null) {
            throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("asOf from page 1 is needed with a cursor"))
        }
        val now = clock.instant()
        val asOfInstant = asOfParam?.let { parseTime(it) ?: throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("asOf")) } ?: now
        if (asOfInstant.isAfter(now)) throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("asOf is in the future"))
        val asOf = stampOf(asOfInstant)
        // The range is resolved at asOf, so "This week" means the same week on every page.
        val calendar = ShopCalendar(settings.zone(), settings.weekStart(), Clock.fixed(asOfInstant, java.time.ZoneOffset.UTC))
        val resolved = resolveRange(calendar, preset, from, to)
        val start = stampOf(resolved.range.startUtc)
        val end = stampOf(resolved.range.endUtc)
        val after = cursor?.let(::decodeCursor)
        return db.read { c ->
            val person = c.query(Sql("SELECT id, display_name, active FROM employees WHERE id = ?", employeeId)) { PersonRef(it.getString(1), it.getString(2), it.getInt(3) == 1) }
                .singleOrNull() ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.PERSON_NOT_FOUND)
            val all = HistoryClassifier.events(c, employeeId, start, end, asOf).map { it.event }
            val rest = if (after == null) all else all.filter { e -> e.occurredAt < after.first || (e.occurredAt == after.first && e.eventId < after.second) }
            val shown = rest.take(PAGE_SIZE)
            val next = if (rest.size > PAGE_SIZE) shown.last().let { encodeCursor(it.occurredAt, it.eventId) } else null
            val totals = if (after == null) {
                val stillIncoming = UnitQuery.count(c, UnitFilter(asOf = asOf, imported = false, checkedInFrom = start, checkedInTo = end,
                    checkedInBy = employeeId, statuses = listOf(UnitStatus.INCOMING.name)))
                HistoryTotals(
                    checkIns = all.count { it.kind == HistoryKinds.CHECK_IN }.toLong(),
                    stillIncoming = stillIncoming,
                    movedToInStock = all.count { it.kind == HistoryKinds.MOVED_TO_IN_STOCK }.toLong(),
                    edits = all.count { it.kind == HistoryKinds.EDIT }.toLong(),
                    reprints = all.count { it.kind == HistoryKinds.REPRINT }.toLong()
                )
            } else null
            HistoryPage(asOf, resolved.preset?.key, resolved.range.from.toString(), resolved.range.to.toString(),
                if (withPerson) person else null, totals, shown, next)
        }
    }

    private fun encodeCursor(occurredAt: String, eventId: Long): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("$occurredAt|$eventId".toByteArray())

    private fun decodeCursor(text: String): Pair<String, Long> = runCatching {
        val (at, id) = String(Base64.getUrlDecoder().decode(text)).split("|")
        require(parseTime(at) != null)
        at to id.toLong()
    }.getOrElse { throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("cursor")) }
}
