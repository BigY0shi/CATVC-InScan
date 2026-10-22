package com.countryatv.core

sealed class VinCheck {
    // Position 9 check digit is correct for a 17-character VIN.
    object Ok : VinCheck()

    // A 17-character VIN whose check digit or characters are wrong. Usually a typo, but some
    // off-road units carry identifiers that don't follow road-vehicle rules, so this warns, never blocks.
    data class Fail(val message: String) : VinCheck()

    // Not 17 characters: a serial number or PIN. The check digit rule doesn't apply.
    object NotApplicable : VinCheck()
}

// VIN rules for 17-character identifiers (check digit in position 9, weights mod 11, 10 = X).
object Vin {
    private val WEIGHTS = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)

    fun normalize(raw: String): String = raw.uppercase().filter { !it.isWhitespace() && it != '-' }

    fun check(raw: String): VinCheck {
        val vin = normalize(raw)
        if (vin.length != 17) return VinCheck.NotApplicable
        val bad = vin.filter { it == 'I' || it == 'O' || it == 'Q' || !(it.isDigit() || it in 'A'..'Z') }
        if (bad.isNotEmpty()) {
            return VinCheck.Fail("A VIN never contains the letters I, O or Q, or symbols. Found: ${bad.toSet().joinToString(" ")}. Check it against the frame.")
        }
        val sum = vin.mapIndexed { i, c -> value(c) * WEIGHTS[i] }.sum()
        val remainder = sum % 11
        val expected = if (remainder == 10) 'X' else ('0' + remainder)
        return if (vin[8] == expected) {
            VinCheck.Ok
        } else {
            VinCheck.Fail("This VIN doesn't add up (the 9th character should be $expected). Check it against the frame, or tick the box if it matches exactly.")
        }
    }

    private fun value(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        'A', 'J' -> 1
        'B', 'K', 'S' -> 2
        'C', 'L', 'T' -> 3
        'D', 'M', 'U' -> 4
        'E', 'N', 'V' -> 5
        'F', 'W' -> 6
        'G', 'P', 'X' -> 7
        'H', 'Y' -> 8
        'R', 'Z' -> 9
        else -> 0
    }
}
