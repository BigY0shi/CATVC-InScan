package com.countryatv.core

import com.countryatv.core.reports.Median
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// "Usually" figures: empty, odd and even (reports spec §16).
class MedianTest {
    @Test
    fun `no values have no median, never zero`() = assertNull(Median.of(emptyList()))

    @Test
    fun `an odd count takes the middle value, whatever the input order`() = assertEquals(120L, Median.of(listOf(600, 5, 120)))

    @Test
    fun `an even count takes the mean of the two middle values, rounded down`() {
        assertEquals(62L, Median.of(listOf(5, 120, 600, 4)))
        assertEquals(7L, Median.of(listOf(7, 7)))
    }
}
