package com.example.data

sealed class PriceResult {
    data class Valid(val dollars: Double) : PriceResult()
    data class Invalid(val message: String) : PriceResult()
}

// Parses a price typed by a person. Never guesses: anything unclear is refused with a plain message.
object PriceParser {
    private val pattern = Regex("""^\d+(\.\d{1,2})?$""")

    fun parse(input: String): PriceResult {
        val cleaned = input.trim().removePrefix("$").trim().replace(",", "")
        if (cleaned.isEmpty()) {
            return PriceResult.Invalid("Enter a price before saving.")
        }
        if (!pattern.matches(cleaned)) {
            return PriceResult.Invalid("The price \"${input.trim()}\" isn't a number. Type it like 8499 or 8,499.99.")
        }
        val dollars = cleaned.toDouble()
        if (dollars <= 0.0) {
            return PriceResult.Invalid("The price must be more than \$0.")
        }
        return PriceResult.Valid(dollars)
    }
}
