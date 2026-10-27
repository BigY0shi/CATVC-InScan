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

    // The last 8 characters (40 random bits), printed on tags for people to type when a QR code is
    // damaged. Long IDs don't fit small labels at a readable size. Lookups by short form refuse
    // when more than one item matches, so a clash can never open the wrong item.
    const val SHORT_LENGTH = 8
    private val SHORT_PATTERN = Regex("^[0-9A-HJKMNP-TV-Z]{$SHORT_LENGTH}$")

    fun shortForm(id: String): String = id.takeLast(SHORT_LENGTH)

    // Accepts what a person might type: lower case, spaces, and the letters Crockford base32 reads
    // as digits (O as 0, I and L as 1).
    fun normalizeShort(typed: String): String? {
        val clean = typed.uppercase().filter { !it.isWhitespace() && it != '-' }
            .replace('O', '0').replace('I', '1').replace('L', '1')
        return clean.takeIf { SHORT_PATTERN.matches(it) }
    }
}
