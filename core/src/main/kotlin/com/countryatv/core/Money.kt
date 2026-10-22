package com.countryatv.core

import java.text.NumberFormat
import java.util.Locale

sealed class MoneyResult {
    data class Valid(val cents: Long) : MoneyResult()
    data class Invalid(val message: String) : MoneyResult()
}

// Money is whole cents. Typed amounts are parsed strictly: anything unclear is refused in plain words.
object Money {
    private val PATTERN = Regex("""^\d{1,9}(\.\d{1,2})?$""")

    fun parseDollars(input: String, what: String = "price"): MoneyResult {
        val cleaned = input.trim().removePrefix("$").trim().replace(",", "")
        if (cleaned.isEmpty()) return MoneyResult.Invalid("Enter a $what before saving.")
        if (!PATTERN.matches(cleaned)) {
            return MoneyResult.Invalid("The $what \"${input.trim()}\" isn't a number. Type it like 8499 or 8,499.99.")
        }
        val parts = cleaned.split(".")
        val dollars = parts[0].toLong()
        val centsPart = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0L
        val cents = dollars * 100 + centsPart
        if (cents <= 0) return MoneyResult.Invalid("The $what must be more than \$0.")
        return MoneyResult.Valid(cents)
    }

    fun format(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.US).format(cents / 100.0)
}
