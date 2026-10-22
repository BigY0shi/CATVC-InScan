package com.countryatv.core

import java.security.SecureRandom

// Unique IDs made on the phone, so a phone can log an item while offline and never collide
// with another phone. Format: ULID (26 characters, Crockford base32, sorts by creation time).
object ItemId {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val PATTERN = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
    private val random = SecureRandom()

    fun new(nowMillis: Long = System.currentTimeMillis()): String {
        val chars = CharArray(26)
        var time = nowMillis
        for (i in 9 downTo 0) {
            chars[i] = ALPHABET[(time and 31).toInt()]
            time = time ushr 5
        }
        val bytes = ByteArray(10).also { random.nextBytes(it) }
        // 80 random bits -> 16 base32 characters.
        var bitBuffer = 0L
        var bitCount = 0
        var out = 10
        for (b in bytes) {
            bitBuffer = (bitBuffer shl 8) or (b.toLong() and 0xFF)
            bitCount += 8
            while (bitCount >= 5) {
                bitCount -= 5
                chars[out++] = ALPHABET[((bitBuffer shr bitCount) and 31).toInt()]
            }
        }
        return String(chars)
    }

    fun isValid(value: String): Boolean = PATTERN.matches(value)
}
