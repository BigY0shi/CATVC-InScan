package com.countryatv.core.reports

// "Usually" figures are medians (reports spec §4): the middle value, or the mean of the two middle
// values (rounded down) for an even count. No values, no median: null, never 0.
object Median {
    fun of(values: Collection<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else Math.floorDiv(sorted[mid - 1] + sorted[mid], 2L)
    }
}
