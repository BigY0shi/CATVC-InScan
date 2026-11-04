package com.countryatv.server

import com.countryatv.core.Problem
import com.countryatv.core.UnitStatus
import com.countryatv.core.reports.UnitFilter
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.Base64

// A piece of SQL and the values bound to its "?"s, in order.
class Sql(text: String = "", vararg args: Any?) {
    val text = StringBuilder(text)
    val args = args.toMutableList()

    fun add(more: String, vararg values: Any?): Sql { text.append(more); args.addAll(values); return this }
    fun add(more: Sql): Sql { text.append(more.text); args.addAll(more.args); return this }
    override fun toString() = text.toString()
}

fun PreparedStatement.bind(args: List<Any?>) {
    args.forEachIndexed { i, v ->
        when (v) {
            null -> setNull(i + 1, Types.NULL)
            is String -> setString(i + 1, v)
            is Long -> setLong(i + 1, v)
            is Int -> setInt(i + 1, v)
            is Boolean -> setInt(i + 1, if (v) 1 else 0)
            else -> error("can't bind ${v::class}")
        }
    }
}

fun <T> Connection.query(sql: Sql, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql.toString()).use { st -> st.bind(sql.args); st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } } }

// What a report can split units by. Each dimension has ONE SQL expression, used both to GROUP BY
// and, through UnitFilter, to filter the row's drill-down list. That is what makes a row's count
// and its list's total agree by construction (reports spec §7).
enum class UnitDimension { STATUS, TYPE, MAKE, CHECKED_IN_BY, CONFIRMED_BY, SOLD_BY, TAG_PRINTED_BY }

data class GroupCount(val key: String, val label: String, val count: Long, val sum: Long?)

// Turns a UnitFilter into SQL over `units u`. The one matcher for /units/query and for every
// report that counts units.
object UnitQuery {
    private val STATUS_NAMES = UnitStatus.entries.map { it.name }.toSet()

    // A unit's status as of asOf: the "from" of the first change recorded after asOf, or, when
    // nothing has changed since, its status now. Only changes after asOf are read, and every change
    // since the typed table exists is recorded, so this is exact.
    fun statusExpr(asOf: String?): Sql =
        if (asOf == null) Sql("u.status")
        else Sql("COALESCE((SELECT s.from_status FROM unit_status_changes s WHERE s.unit_id = u.id AND s.received_at > ? ORDER BY s.received_at, s.id LIMIT 1), u.status)", asOf)

    // The unit's first move from Incoming to In stock (not an import), as known at asOf.
    fun firstMove(column: String, asOf: String?): Sql {
        val sql = Sql("(SELECT s.$column FROM unit_status_changes s WHERE s.unit_id = u.id AND s.from_status = 'INCOMING' AND s.to_status = 'IN_STOCK' AND s.source <> 'import'")
        if (asOf != null) sql.add(" AND s.received_at <= ?", asOf)
        return sql.add(" ORDER BY s.occurred_at, s.id LIMIT 1)")
    }

    // The unit's latest sale in [from, to) (not an import), as known at asOf.
    fun latestSale(column: String, from: String, to: String, asOf: String?): Sql {
        val sql = Sql("(SELECT s.$column FROM unit_status_changes s WHERE s.unit_id = u.id AND s.to_status = 'SOLD' AND s.source <> 'import' AND s.occurred_at >= ? AND s.occurred_at < ?", from, to)
        if (asOf != null) sql.add(" AND s.received_at <= ?", asOf)
        return sql.add(" ORDER BY s.occurred_at DESC, s.id DESC LIMIT 1)")
    }

    // The unit's newest printed tag (the one most likely on the box, as the confirm uses), as known at asOf.
    fun newestTag(column: String, asOf: String?): Sql {
        val sql = Sql("(SELECT t.$column FROM tags t WHERE t.unit_id = u.id")
        if (asOf != null) sql.add(" AND EXISTS (SELECT 1 FROM audit_log a WHERE a.entity_id = t.id AND a.action = 'tag.print' AND a.at <= ?)", asOf)
        return sql.add(" ORDER BY t.printed_at DESC, t.rowid DESC LIMIT 1)")
    }

    fun dimensionExpr(dim: UnitDimension, f: UnitFilter): Sql = when (dim) {
        UnitDimension.STATUS -> statusExpr(f.asOf)
        UnitDimension.TYPE -> Sql("u.unit_type")
        UnitDimension.MAKE -> Sql("lower(u.make)")
        UnitDimension.CHECKED_IN_BY -> Sql("u.created_by")
        UnitDimension.CONFIRMED_BY -> firstMove("actor", f.asOf)
        UnitDimension.SOLD_BY -> latestSale("actor", f.soldFrom ?: error("soldBy needs a sold range"), f.soldTo ?: error("soldBy needs a sold range"), f.asOf)
        UnitDimension.TAG_PRINTED_BY -> newestTag("printed_by", f.asOf)
    }

    // The filter that narrows f to one value of a dimension: the drill for a GROUP BY row.
    fun narrow(f: UnitFilter, dim: UnitDimension, value: String): UnitFilter = when (dim) {
        UnitDimension.STATUS -> f.copy(statuses = listOf(value))
        UnitDimension.TYPE -> f.copy(unitTypes = listOf(value))
        UnitDimension.MAKE -> f.copy(makes = listOf(value))
        UnitDimension.CHECKED_IN_BY -> f.copy(checkedInBy = value)
        UnitDimension.CONFIRMED_BY -> f.copy(confirmedBy = value, movedToInStock = true)
        UnitDimension.SOLD_BY -> f.copy(soldBy = value)
        UnitDimension.TAG_PRINTED_BY -> f.copy(tagPrintedBy = value, hasTag = true)
    }

    fun where(f: UnitFilter): Sql {
        val w = Sql("1=1")
        f.asOf?.let { w.add(" AND u.created_at <= ?", it) }
        f.statuses?.let { w.add(" AND ").add(statusExpr(f.asOf)).add(inList(it)) }
        f.unitTypes?.let { w.add(" AND u.unit_type").add(inList(it)) }
        f.makes?.let { makes ->
            if (makes.isEmpty()) w.add(" AND 0") else w.add(" AND lower(u.make) IN (${makes.joinToString(",") { "lower(?)" }})", *makes.toTypedArray())
        }
        f.imported?.let { w.add(" AND u.imported = ?", it) }
        f.checkedInFrom?.let { w.add(" AND u.checked_in_at >= ?", it) }
        f.checkedInTo?.let { w.add(" AND u.checked_in_at < ?", it) }
        f.checkedInBy?.let { w.add(" AND u.created_by = ?", it) }
        f.movedToInStock?.let { w.add(" AND ").add(firstMove("id", f.asOf)).add(if (it) " IS NOT NULL" else " IS NULL") }
        f.confirmedBy?.let { w.add(" AND ").add(firstMove("actor", f.asOf)).add(" = ?", it) }
        val soldFrom = f.soldFrom
        val soldTo = f.soldTo
        if (soldFrom != null && soldTo != null) {
            w.add(" AND ").add(latestSale("id", soldFrom, soldTo, f.asOf)).add(" IS NOT NULL")
            f.soldBy?.let { w.add(" AND ").add(latestSale("actor", soldFrom, soldTo, f.asOf)).add(" = ?", it) }
        }
        f.hasTag?.let { w.add(" AND ").add(newestTag("id", f.asOf)).add(if (it) " IS NOT NULL" else " IS NULL") }
        f.newestTagPrintedFrom?.let { w.add(" AND ").add(newestTag("printed_at", f.asOf)).add(" >= ?", it) }
        f.newestTagPrintedTo?.let { w.add(" AND ").add(newestTag("printed_at", f.asOf)).add(" < ?", it) }
        f.tagPrintedBy?.let { w.add(" AND ").add(newestTag("printed_by", f.asOf)).add(" = ?", it) }
        return w
    }

    private fun inList(values: List<String>): Sql =
        if (values.isEmpty()) Sql(" IN (SELECT NULL WHERE 0)") else Sql(" IN (${values.joinToString(",") { "?" }})", *values.toTypedArray())

    fun count(c: Connection, f: UnitFilter): Long =
        c.query(Sql("SELECT COUNT(*) FROM units u WHERE ").add(where(f))) { it.getLong(1) }.single()

    // SUM of an expression over the matching units; null when no units match.
    fun sum(c: Connection, f: UnitFilter, expr: Sql): Long? =
        c.query(Sql("SELECT SUM(").add(expr).add(") FROM units u WHERE ").add(where(f))) { rs -> rs.getLong(1).takeUnless { rs.wasNull() } }.single()

    // GROUP BY one dimension. Rows whose value is unknown (null) are left out; reports only group
    // populations where the value is always there.
    fun groupCounts(c: Connection, f: UnitFilter, dim: UnitDimension, sumExpr: Sql? = null): List<GroupCount> {
        val sql = Sql("SELECT ").add(dimensionExpr(dim, f)).add(" AS g, MIN(u.make) AS label, COUNT(*) AS n")
        if (sumExpr != null) sql.add(", SUM(").add(sumExpr).add(") AS total")
        sql.add(" FROM units u WHERE ").add(where(f)).add(" GROUP BY g")
        return c.query(sql) { rs ->
            val key = rs.getString("g")
            GroupCount(key ?: "", if (dim == UnitDimension.MAKE) rs.getString("label") else key ?: "", rs.getLong("n"),
                if (sumExpr != null) rs.getLong("total").takeUnless { rs.wasNull() } else null)
        }.filter { it.key.isNotEmpty() }
    }

    // Matching units in the filter's sort, after the keyset (checkedInAt, id) when given.
    fun <T> rows(c: Connection, f: UnitFilter, after: Pair<String, String>?, limit: Int?, extraColumns: Sql? = null, map: (ResultSet) -> T): List<T> {
        val newest = f.sort != UnitFilter.SORT_OLDEST_CHECKED_IN
        val sql = Sql("SELECT u.*")
        if (extraColumns != null) sql.add(", ").add(extraColumns)
        sql.add(" FROM units u WHERE ").add(where(f))
        if (after != null) {
            val op = if (newest) "<" else ">"
            sql.add(" AND (u.checked_in_at $op ? OR (u.checked_in_at = ? AND u.id $op ?))", after.first, after.first, after.second)
        }
        val dir = if (newest) "DESC" else "ASC"
        sql.add(" ORDER BY u.checked_in_at $dir, u.id $dir")
        if (limit != null) sql.add(" LIMIT ?", limit)
        return c.query(sql, map)
    }

    // Checks a filter that came from outside and puts its times in the stored format.
    fun validate(f: UnitFilter): UnitFilter {
        val bad = mutableListOf<String>()
        fun time(name: String, v: String?): String? = v?.let { parseTime(it)?.let(::stampOf) ?: run { bad += name; null } }
        f.statuses?.filterNot { it in STATUS_NAMES }?.forEach { bad += "status $it" }
        if (f.sort !in UnitFilter.SORTS) bad += "sort"
        if (f.soldBy != null && (f.soldFrom == null || f.soldTo == null)) bad += "soldBy needs soldFrom and soldTo"
        if ((f.soldFrom == null) != (f.soldTo == null)) bad += "soldFrom and soldTo go together"
        val clean = f.copy(
            asOf = time("asOf", f.asOf),
            checkedInFrom = time("checkedInFrom", f.checkedInFrom), checkedInTo = time("checkedInTo", f.checkedInTo),
            soldFrom = time("soldFrom", f.soldFrom), soldTo = time("soldTo", f.soldTo),
            newestTagPrintedFrom = time("newestTagPrintedFrom", f.newestTagPrintedFrom),
            newestTagPrintedTo = time("newestTagPrintedTo", f.newestTagPrintedTo)
        )
        if (bad.isNotEmpty()) throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, bad)
        return clean
    }
}

// GET /units/query. items use the list shape (D-H4): no one's id for people without See Reports;
// with it, people maps every id in createdBy and updatedBy to a name.
@Serializable
data class UnitQueryPage(
    val total: Long,
    val items: List<UnitListItem>,
    // Send back as ?cursor= with the same filter for the next page; null on the last page.
    val next: String? = null,
    // The server time page 1 was pinned to. Every page counts and lists the shop as it was then.
    val asOf: String,
    val people: Map<String, String>? = null
)

@Serializable
private data class QueryCursor(val asOf: String, val checkedInAt: String, val id: String, val filter: String)

class UnitQueryService(private val db: Database, private val clock: java.time.Clock) {
    private val json = Json { encodeDefaults = true }

    fun page(raw: UnitFilter, cursor: String?, limit: Int, caller: Caller): UnitQueryPage {
        checkPersonFilters(raw, caller)
        val filter = UnitQuery.validate(raw)
        val hash = Auth.sha256(json.encodeToString(filter.copy(asOf = null))).take(16)
        val after = cursor?.let { decode(it) }
        if (after != null && after.filter != hash) {
            throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("the cursor belongs to a different filter"))
        }
        if (after != null && filter.asOf != null && filter.asOf != after.asOf) {
            throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("the cursor belongs to a different asOf"))
        }
        val asOf = after?.asOf ?: filter.asOf ?: clock.stamp()
        val pinned = filter.copy(asOf = asOf)
        val size = limit.coerceIn(1, 200)
        return db.read { c ->
            val total = UnitQuery.count(c, pinned)
            val units = UnitQuery.rows(c, pinned, after?.let { it.checkedInAt to it.id }, size + 1) { rs -> rs.toUnitDto() }
            val shown = units.take(size)
            val next = if (units.size > size) shown.last().let { encode(QueryCursor(asOf, it.checkedInAt, it.id, hash)) } else null
            val withPeople = caller.has(Permission.SEE_REPORTS)
            val people = if (withPeople) names(c, shown.flatMap { listOf(it.createdBy, it.updatedBy) }.toSet()) else null
            UnitQueryPage(total, shown.map { it.toListItem(withPeople) }, next, asOf, people)
        }
    }

    private fun encode(cur: QueryCursor) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.encodeToString(cur).toByteArray())

    private fun decode(text: String): QueryCursor = runCatching {
        json.decodeFromString<QueryCursor>(String(Base64.getUrlDecoder().decode(text)))
    }.getOrElse { throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("cursor")) }

    companion object {
        // Person filters need See Reports, except "Only units checked in by you" (reports spec §2,
        // stock spec §7). Otherwise an Employee could rebuild "work by person" from the list.
        fun checkPersonFilters(f: UnitFilter, caller: Caller) {
            val others = f.personFilters().filterNot { (field, id) -> field == "checkedInBy" && id == caller.employeeId }
            if (others.isNotEmpty()) caller.require(Permission.SEE_REPORTS)
            if (f.checkedInBy == caller.employeeId) caller.require(Permission.SEE_OWN_HISTORY)
        }
    }
}

// employee id -> display name, for everyone in ids (deactivated people too: their work stays named).
fun names(c: Connection, ids: Set<String>): Map<String, String> {
    if (ids.isEmpty()) return emptyMap()
    val all = c.query(Sql("SELECT id, display_name FROM employees")) { it.getString(1) to it.getString(2) }.toMap()
    return ids.mapNotNull { id -> all[id]?.let { id to it } }.toMap()
}

fun allNames(c: Connection): Map<String, String> = c.query(Sql("SELECT id, display_name FROM employees")) { it.getString(1) to it.getString(2) }.toMap()

// Reads a filter from the one documented query-string form: ?filter=<the UnitFilter as JSON>,
// URL-encoded. Unknown fields are refused, so a typo can't silently widen a list.
private val filterJson = Json { ignoreUnknownKeys = false }

fun parseFilterParam(text: String?): UnitFilter =
    if (text.isNullOrBlank()) UnitFilter()
    else runCatching { filterJson.decodeFromString<UnitFilter>(text) }.getOrElse { throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf("filter")) }
