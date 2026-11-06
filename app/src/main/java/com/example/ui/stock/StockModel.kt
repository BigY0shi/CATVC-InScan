package com.example.ui.stock

import com.countryatv.core.ItemId
import com.countryatv.core.Money
import com.countryatv.core.UnitStatus
import com.countryatv.core.Vin
import com.example.data.ShopUnitEntity
import com.example.ui.home.checkedInAt
import com.example.ui.home.shortDay
import com.example.ui.home.unitName
import com.example.ui.inscan.look
import com.example.ui.inscan.statusOf
import com.example.ui.inscan.unitTypeWord
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// Everything the Stock list shows, worked out from this phone's copy (stock-list-ui-spec.md).
// Pure functions, so the list, the search sheet and the tests all read the same answers.

// The status chips, in their locked order. All means everything not sold (and not retired).
enum class StockStatus(val word: String, val unit: UnitStatus?) {
    ALL("All", null),
    IN_STOCK("In stock", UnitStatus.IN_STOCK),
    INCOMING("Incoming", UnitStatus.INCOMING),
    SALE_PENDING("Sale pending", UnitStatus.SALE_PENDING),
    SOLD("Sold", UnitStatus.SOLD),
    RETIRED("Retired", UnitStatus.RETIRED);

    fun includes(s: UnitStatus): Boolean = if (this == ALL) s != UnitStatus.SOLD && s != UnitStatus.RETIRED else unit == s

    companion object {
        fun of(s: UnitStatus?): StockStatus = entries.firstOrNull { it.unit == s && s != null } ?: ALL
    }
}

enum class StockSort(val label: String) {
    NEWEST_IN_STOCK("Newest in stock"),
    OLDEST_IN_STOCK("Oldest in stock"),
    NEWEST_CHECKED_IN("Newest checked in"),
    OLDEST_CHECKED_IN("Oldest checked in"),
    PRICE_HIGH("Price, high to low"),
    PRICE_LOW("Price, low to high"),
    MAKE_AZ("Make, A to Z"),
}

// Each status answers a different question, so each has its own default order (spec section 3).
fun defaultSortLabel(s: StockStatus): String = when (s) {
    StockStatus.IN_STOCK -> "Newest in stock"
    StockStatus.INCOMING -> "Oldest first"
    StockStatus.ALL -> "Newest checked in"
    StockStatus.SALE_PENDING, StockStatus.SOLD, StockStatus.RETIRED -> "Newest first"
}

// The sort-sheet row that matches a status's default, for the radio mark.
fun defaultSortRow(s: StockStatus): StockSort? = when (s) {
    StockStatus.IN_STOCK -> StockSort.NEWEST_IN_STOCK
    StockStatus.INCOMING -> StockSort.OLDEST_CHECKED_IN
    StockStatus.ALL -> StockSort.NEWEST_CHECKED_IN
    else -> null
}

enum class CheckedIn(val label: String) { ANY("Any time"), TODAY("Today"), WEEK("This week"), MONTH("This month") }

const val NO_YEAR = "No year"

// The unit types in the shop's words, in the order the mockup lists them.
val TYPE_ORDER = listOf("Two wheels", "Four wheels", "Side-by-side", "Snow", "Water", "Other")

data class StockFilters(
    val noVin: Boolean = false,
    val types: Set<String> = emptySet(),
    val makes: Set<String> = emptySet(),      // cleaned make keys
    val years: Set<String> = emptySet(),      // "2026" or NO_YEAR
    val checkedIn: CheckedIn = CheckedIn.ANY,
    val showRetired: Boolean = false,
) {
    val count: Int get() = (if (noVin) 1 else 0) + types.size + makes.size + years.size +
        (if (checkedIn != CheckedIn.ANY) 1 else 0) + (if (showRetired) 1 else 0)
}

data class StockQuery(
    val status: StockStatus = StockStatus.IN_STOCK,
    val text: String = "",
    val sort: StockSort? = null,
    val filters: StockFilters = StockFilters(),
)

// ---------- Matching (spec section 8, the phone side) ----------

private val NON_ALNUM = Regex("[^a-z0-9]")

fun clean(s: String): String = s.lowercase(Locale.US).replace(NON_ALNUM, "").replace("gray", "grey")

private fun foldVin(s: String): String = Vin.normalize(s).replace('O', '0').replace('I', '1').replace('Q', '0')

// The query with a leading "ID" stripped, as typed off a tag ("ID 3HZW8RTB").
private fun withoutIdPrefix(text: String): String = text.trim().replace(Regex("^(?i)id\\s+"), "").let {
    if (it.length == 10 && it.startsWith("ID", ignoreCase = true) && ItemId.normalizeShort(it.drop(2)) != null) it.drop(2) else it
}

// The 8-character tag ID the query names exactly, if it names one.
fun exactShortId(text: String): String? = ItemId.normalizeShort(withoutIdPrefix(text))

enum class MatchedBy { ID, VIN, TEXT }

fun matchUnit(u: ShopUnitEntity, text: String): MatchedBy? {
    val q = text.trim()
    if (q.isEmpty()) return MatchedBy.TEXT
    val short = ItemId.shortForm(u.id)
    if (exactShortId(q) == short) return MatchedBy.ID
    val vin = u.vin?.let { foldVin(it) }.orEmpty()
    val fields = listOf(
        u.make, u.model, u.subModel, u.color, u.year?.toString().orEmpty(), unitTypeWord(u.unitType), u.unitType, short,
    ).map { clean(it) } + clean(listOf(u.make, u.model, u.subModel).joinToString(""))
    var byVin = false
    val words = withoutIdPrefix(q).split(Regex("\\s+")).map { clean(it) }.filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    for (w in words) {
        if (fields.any { it.contains(w) }) continue
        if (ItemId.normalizeShort(w) == short) continue
        val v = foldVin(w)
        val vinHit = vin.isNotEmpty() && ((v.length >= 6 && vin.contains(v)) || (v.length in 4..8 && vin.endsWith(v)))
        if (vinHit) { byVin = true; continue }
        return null
    }
    return if (byVin) MatchedBy.VIN else MatchedBy.TEXT
}

// What the Find sheet jumps straight to: the one unit whose tag ID or full VIN was typed exactly.
fun exactUnit(units: List<ShopUnitEntity>, text: String): ShopUnitEntity? {
    val q = text.trim()
    exactShortId(q)?.let { short -> units.filter { ItemId.shortForm(it.id) == short }.singleOrNull()?.let { return it } }
    val vin = Vin.normalize(q)
    if (vin.length == 17) units.filter { it.vin != null && Vin.normalize(it.vin) == vin }.singleOrNull()?.let { return it }
    return null
}

// ---------- Dates ----------

fun statusOfUnit(u: ShopUnitEntity): UnitStatus = statusOf(u.status) ?: UnitStatus.INCOMING

private fun inStockSince(u: ShopUnitEntity): Instant? = u.inStockSince?.let { runCatching { Instant.parse(it) }.getOrNull() }
private fun updatedAt(u: ShopUnitEntity): Instant? = runCatching { Instant.parse(u.updatedAt) }.getOrNull()

private fun checkedInWithin(u: ShopUnitEntity, c: CheckedIn, today: LocalDate, zone: ZoneId): Boolean {
    if (c == CheckedIn.ANY) return true
    val d = checkedInAt(u.id)?.atZone(zone)?.toLocalDate() ?: return false
    val from = when (c) {
        CheckedIn.TODAY -> today
        CheckedIn.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        CheckedIn.MONTH -> today.withDayOfMonth(1)
        CheckedIn.ANY -> return true
    }
    return !d.isBefore(from) && !d.isAfter(today)
}

// ---------- Filters ----------

private fun yearKey(u: ShopUnitEntity) = u.year?.toString() ?: NO_YEAR

private enum class Group { MISSING, TYPE, MAKE, YEAR, WHEN }

private fun passes(u: ShopUnitEntity, f: StockFilters, today: LocalDate, zone: ZoneId, skip: Group? = null): Boolean =
    (skip == Group.MISSING || !f.noVin || u.vin.isNullOrBlank()) &&
        (skip == Group.TYPE || f.types.isEmpty() || unitTypeWord(u.unitType) in f.types) &&
        (skip == Group.MAKE || f.makes.isEmpty() || clean(u.make) in f.makes) &&
        (skip == Group.YEAR || f.years.isEmpty() || yearKey(u) in f.years) &&
        (skip == Group.WHEN || checkedInWithin(u, f.checkedIn, today, zone))

// ---------- Sorting ----------

private fun <T : Comparable<T>> nullsLast(a: T?, b: T?, desc: Boolean): Int = when {
    a == null && b == null -> 0
    a == null -> 1
    b == null -> -1
    desc -> b.compareTo(a)
    else -> a.compareTo(b)
}

fun sortUnits(units: List<ShopUnitEntity>, status: StockStatus, sort: StockSort?): List<ShopUnitEntity> {
    val byId = compareByDescending<ShopUnitEntity> { it.id }
    val primary: Comparator<ShopUnitEntity> = when (sort) {
        StockSort.NEWEST_IN_STOCK -> Comparator { a, b -> nullsLast(inStockSince(a), inStockSince(b), desc = true) }
        StockSort.OLDEST_IN_STOCK -> Comparator { a, b -> nullsLast(inStockSince(a), inStockSince(b), desc = false) }
        StockSort.NEWEST_CHECKED_IN -> Comparator { a, b -> nullsLast(checkedInAt(a.id), checkedInAt(b.id), desc = true) }
        StockSort.OLDEST_CHECKED_IN -> Comparator { a, b -> nullsLast(checkedInAt(a.id), checkedInAt(b.id), desc = false) }
        StockSort.PRICE_HIGH -> Comparator { a, b -> nullsLast(a.priceCents.takeIf { it > 0 }, b.priceCents.takeIf { it > 0 }, desc = true) }
        StockSort.PRICE_LOW -> Comparator { a, b -> nullsLast(a.priceCents.takeIf { it > 0 }, b.priceCents.takeIf { it > 0 }, desc = false) }
        StockSort.MAKE_AZ -> Comparator { a, b ->
            nullsLast(clean(a.make).ifEmpty { null }, clean(b.make).ifEmpty { null }, desc = false).takeIf { it != 0 }
                ?: clean(a.model).compareTo(clean(b.model))
        }
        null -> when (status) {
            StockStatus.IN_STOCK -> Comparator { a, b -> nullsLast(inStockSince(a), inStockSince(b), desc = true) }
            // The scan queue: the oldest unconfirmed box first, the same order as Home.
            StockStatus.INCOMING -> Comparator { a, b -> nullsLast(checkedInAt(a.id), checkedInAt(b.id), desc = false) }
            StockStatus.ALL -> Comparator { a, b -> nullsLast(checkedInAt(a.id), checkedInAt(b.id), desc = true) }
            // "Sale started" and "sold" dates aren't on the phone yet: the last change stands in.
            else -> Comparator { a, b -> nullsLast(updatedAt(a), updatedAt(b), desc = true) }
        }
    }
    return units.sortedWith(primary.then(byId))
}

// ---------- Card lines ----------

// One piece of the meta line. A gap is a missing detail, shown with a warning icon.
data class MetaPart(val text: String, val gap: Boolean = false, val spoken: String = text)

data class StockCard(
    val unitId: String,
    val shortId: String,
    val name: String,
    val status: UnitStatus,
    val typeWord: String,
    val parts: List<MetaPart>,
    val spoken: String,
)

private val FULL_DAYS = mapOf("Mon" to "Monday", "Tue" to "Tuesday", "Wed" to "Wednesday", "Thu" to "Thursday",
    "Fri" to "Friday", "Sat" to "Saturday", "Sun" to "Sunday")

private fun spokenDay(s: String) = FULL_DAYS.entries.fold(s) { acc, (k, v) -> acc.replace(Regex("\\b$k\\b"), v) }

private fun plural(n: Long, one: String, many: String = one + "s") = if (n == 1L) "$n $one" else "$n $many"

fun timeOfDay(at: Instant, zone: ZoneId): String =
    at.atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)).lowercase(Locale.US)

// D-H4: no names on any card, own units included. Colour is always shown when the unit has one.
fun stockCard(u: ShopUnitEntity, now: Instant, zone: ZoneId): StockCard {
    val today = now.atZone(zone).toLocalDate()
    val status = statusOfUnit(u)
    val type = unitTypeWord(u.unitType)
    val parts = mutableListOf(MetaPart(type))
    if (u.color.isNotBlank()) parts += MetaPart(u.color.trim().replaceFirstChar { it.titlecase(Locale.US) })
    fun price() { parts += if (u.priceCents > 0) MetaPart(Money.format(u.priceCents).removeSuffix(".00")) else MetaPart("no price", gap = true) }
    fun noVin() { if (u.vin.isNullOrBlank()) parts += MetaPart("no VIN", gap = true, spoken = "no V I N") }
    val checked = checkedInAt(u.id)
    when (status) {
        UnitStatus.IN_STOCK -> {
            price(); noVin()
            inStockSince(u)?.let { since ->
                val days = ChronoUnit.DAYS.between(since.atZone(zone).toLocalDate(), today)
                parts += if (days <= 0) MetaPart("in stock today") else MetaPart("${plural(days, "day")} in stock")
            }
        }
        UnitStatus.INCOMING -> {
            if (checked != null) {
                val day = shortDay(checked, today, zone)
                parts += MetaPart("checked in $day", spoken = "checked in ${spokenDay(day)}")
                val days = ChronoUnit.DAYS.between(checked.atZone(zone).toLocalDate(), today)
                parts += if (days <= 0) MetaPart("waiting since ${timeOfDay(checked, zone)}") else MetaPart("waiting ${plural(days, "day")}")
            }
            noVin()
        }
        UnitStatus.SALE_PENDING -> { price(); noVin() }
        UnitStatus.SOLD, UnitStatus.RETIRED -> Unit
    }
    val short = ItemId.shortForm(u.id)
    val name = unitName(u)
    val spoken = buildList {
        add(name); add("tag ${short.toList().joinToString(" ")}")
        if (u.color.isNotBlank()) add(u.color.trim().lowercase(Locale.US))
        parts.drop(if (u.color.isNotBlank()) 2 else 1).forEach { add(it.spoken) }
        add(status.look().word.lowercase(Locale.US))
    }.joinToString(", ") + "."
    return StockCard(u.id, short, name, status, type, parts, spoken)
}

// ---------- The list ----------

sealed interface StockEmpty {
    // Nothing in this status at all (no search, no filters).
    data class NoneInStatus(val status: StockStatus) : StockEmpty
    data class FiltersMatchNothing(val filters: Int) : StockEmpty
    // The search matched elsewhere: other statuses, sold units, or with filters off.
    data class MatchesElsewhere(
        val status: StockStatus,
        val others: List<Pair<StockStatus, Int>>,
        val sold: Int,
        val filters: Int,
    ) : StockEmpty
    data class NothingMatches(val text: String, val filters: Int) : StockEmpty
}

data class StockView(
    val query: StockQuery,
    val chips: List<Pair<StockStatus, Int?>>,
    val results: List<ShopUnitEntity>,
    val resultsLine: String,
    val sortLabel: String,
    val empty: StockEmpty?,
)

private val CHIP_ORDER = listOf(StockStatus.ALL, StockStatus.IN_STOCK, StockStatus.INCOMING, StockStatus.SALE_PENDING, StockStatus.SOLD)

fun units(n: Int) = if (n == 1) "1 unit" else "$n units"

fun stockView(all: List<ShopUnitEntity>, q: StockQuery, now: Instant, zone: ZoneId): StockView {
    val today = now.atZone(zone).toLocalDate()
    val searching = q.text.isNotBlank()
    val matched = if (searching) all.mapNotNull { u -> matchUnit(u, q.text)?.let { u to it } } else all.map { it to MatchedBy.TEXT }
    val filtered = matched.filter { passes(it.first, q.filters, today, zone) }
    fun countIn(s: StockStatus, from: List<Pair<ShopUnitEntity, MatchedBy>>) = from.count { s.includes(statusOfUnit(it.first)) }
    val statuses = CHIP_ORDER + if (q.filters.showRetired || q.status == StockStatus.RETIRED) listOf(StockStatus.RETIRED) else emptyList()
    val base = all.map { it to MatchedBy.TEXT }
    val chips = statuses.map { s ->
        s to when {
            searching -> countIn(s, filtered)
            s == StockStatus.SOLD || s == StockStatus.RETIRED -> null
            else -> countIn(s, base)
        }
    }
    val inStatus = filtered.filter { q.status.includes(statusOfUnit(it.first)) }
    val sorted = sortUnits(inStatus.map { it.first }, q.status, q.sort)
    // An exact tag match ranks first, but never stops other fields matching.
    val byTag = inStatus.filter { it.second == MatchedBy.ID }.map { it.first.id }.toSet()
    val results = if (searching) sorted.sortedBy { if (it.id in byTag) 0 else 1 } else sorted
    val empty: StockEmpty? = if (results.isNotEmpty()) null else when {
        searching -> {
            val others = listOf(StockStatus.IN_STOCK, StockStatus.INCOMING, StockStatus.SALE_PENDING)
                .filter { it != q.status && !q.status.includes(it.unit!!) }
                .map { it to countIn(it, filtered) }.filter { it.second > 0 }
            val sold = if (q.status == StockStatus.SOLD) 0 else countIn(StockStatus.SOLD, filtered)
            if (others.isEmpty() && sold == 0) StockEmpty.NothingMatches(q.text.trim(), q.filters.count)
            else StockEmpty.MatchesElsewhere(q.status, others, sold, q.filters.count)
        }
        q.filters.count > 0 -> StockEmpty.FiltersMatchNothing(q.filters.count)
        else -> StockEmpty.NoneInStatus(q.status)
    }
    val line = when {
        results.isEmpty() && searching -> "0 units in ${q.status.word}"
        q.status == StockStatus.INCOMING && q.sort == null && results.size > 1 -> "${units(results.size)}, waiting longest first"
        else -> units(results.size)
    }
    return StockView(q, chips, results, line, q.sort?.label ?: defaultSortLabel(q.status), empty)
}

// ---------- The filter sheet ----------

data class FilterOption(val key: String, val label: String, val count: Int?, val selected: Boolean, val available: Boolean = true)

data class FilterSheetModel(
    val missing: List<FilterOption>,
    val notTracked: List<String>,
    val types: List<FilterOption>,
    val makes: List<FilterOption>,
    val allMakes: Int,
    val years: List<FilterOption>,
    val checkedIn: CheckedIn,
    val showRetired: Boolean,
    val total: Int,
    val nothingBecause: String?,
)

// Each group's counts ignore that group's own choices and apply the others, so picking
// "Two wheels" doesn't zero the other types.
fun filterSheetModel(all: List<ShopUnitEntity>, status: StockStatus, text: String, f: StockFilters, now: Instant, zone: ZoneId, showAllMakes: Boolean): FilterSheetModel {
    val today = now.atZone(zone).toLocalDate()
    val effectiveStatus = status
    val scope = all.filter { (text.isBlank() || matchUnit(it, text) != null) && effectiveStatus.includes(statusOfUnit(it)) }
    fun ignoring(g: Group) = scope.filter { passes(it, f, today, zone, skip = g) }
    val missingScope = ignoring(Group.MISSING)
    val typeScope = ignoring(Group.TYPE)
    val makeScope = ignoring(Group.MAKE)
    val yearScope = ignoring(Group.YEAR)
    val missing = listOf(
        FilterOption("noPrice", "No price", null, false, available = false),
        FilterOption("noVin", "No VIN", missingScope.count { it.vin.isNullOrBlank() }, f.noVin),
        FilterOption("noTag", "No tag yet", null, false, available = false),
        FilterOption("tagOld", "Tag out of date", null, false, available = false),
    )
    val types = TYPE_ORDER.map { t -> FilterOption(t, t, typeScope.count { unitTypeWord(it.unitType) == t }, t in f.types) }
    val makeCounts = makeScope.filter { clean(it.make).isNotEmpty() }.groupBy { clean(it.make) }
        .map { (key, us) -> Triple(key, us.groupingBy { it.make.trim() }.eachCount().maxByOrNull { it.value }!!.key, us.size) }
        .sortedWith(compareByDescending<Triple<String, String, Int>> { it.third }.thenBy { it.second })
    val shown = if (showAllMakes) makeCounts else makeCounts.take(6) + makeCounts.drop(6).filter { it.first in f.makes }
    val makes = shown.map { FilterOption(it.first, it.second, it.third, it.first in f.makes) }
    val years = (yearScope.mapNotNull { it.year }.distinct().sortedDescending().map { it.toString() } + NO_YEAR)
        .map { y -> FilterOption(y, y, yearScope.count { yearKey(it) == y }, y in f.years) }
    val total = scope.count { passes(it, f, today, zone) }
    val names = buildList {
        if (f.noVin) add("No VIN")
        addAll(TYPE_ORDER.filter { it in f.types })
        addAll(makeCounts.filter { it.first in f.makes }.map { it.second })
        addAll(f.years.sortedDescending())
        if (f.checkedIn != CheckedIn.ANY) add("Checked in ${f.checkedIn.label.lowercase(Locale.US)}")
    }
    return FilterSheetModel(
        missing = missing,
        notTracked = missing.filter { !it.available }.map { it.label },
        types = types, makes = makes, allMakes = makeCounts.size, years = years,
        checkedIn = f.checkedIn, showRetired = f.showRetired, total = total,
        nothingBecause = if (total == 0 && names.isNotEmpty()) "${names.joinToString(" + ")} match nothing." else null,
    )
}

// The applied-filter chips under the results line, in the sheet's order.
data class AppliedFilter(val label: String, val remove: (StockFilters) -> StockFilters)

fun appliedFilters(all: List<ShopUnitEntity>, f: StockFilters): List<AppliedFilter> = buildList {
    if (f.noVin) add(AppliedFilter("No VIN") { it.copy(noVin = false) })
    TYPE_ORDER.filter { it in f.types }.forEach { t -> add(AppliedFilter(t) { it.copy(types = it.types - t) }) }
    f.makes.sorted().forEach { key ->
        val label = all.firstOrNull { clean(it.make) == key }?.make?.trim() ?: key
        add(AppliedFilter(label) { it.copy(makes = it.makes - key) })
    }
    f.years.sortedDescending().forEach { y -> add(AppliedFilter(y) { it.copy(years = it.years - y) }) }
    if (f.checkedIn != CheckedIn.ANY) add(AppliedFilter("Checked in ${f.checkedIn.label.lowercase(Locale.US)}") { it.copy(checkedIn = CheckedIn.ANY) })
    if (f.showRetired) add(AppliedFilter("Show retired") { it.copy(showRetired = false) })
}
