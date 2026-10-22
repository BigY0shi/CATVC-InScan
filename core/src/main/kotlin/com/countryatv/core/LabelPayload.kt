package com.countryatv.core

// What the printed QR tag holds: the item's ID plus the details that were entered, so a tag
// is readable even with no connection. Never the price, status or any customer data.
data class LabelContent(
    val id: String,
    val make: String = "",
    val model: String = "",
    val subModel: String = "",
    val year: String = "",
    val color: String = "",
    val unitType: String = "",
    val vin: String = ""
)

object LabelPayload {
    private const val PREFIX = "CATV1"
    private val KEYS = listOf("id", "mk", "md", "sm", "yr", "co", "ty", "vin")

    fun encode(c: LabelContent): String {
        val values = listOf(c.id, c.make, c.model, c.subModel, c.year, c.color, c.unitType, c.vin)
        return PREFIX + KEYS.zip(values)
            .filter { it.second.isNotBlank() }
            .joinToString("") { ";${it.first}=${escape(it.second.trim())}" }
    }

    // Returns null for anything that isn't one of our tags (an OEM barcode, a VIN, another QR code).
    fun decode(raw: String): LabelContent? {
        if (!raw.startsWith("$PREFIX;")) return null
        val fields = raw.removePrefix(PREFIX).split(';').filter { it.isNotEmpty() }.associate { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return null
            part.substring(0, eq) to (unescape(part.substring(eq + 1)) ?: return null)
        }
        val id = fields["id"] ?: return null
        if (!ItemId.isValid(id)) return null
        return LabelContent(
            id = id,
            make = fields["mk"].orEmpty(),
            model = fields["md"].orEmpty(),
            subModel = fields["sm"].orEmpty(),
            year = fields["yr"].orEmpty(),
            color = fields["co"].orEmpty(),
            unitType = fields["ty"].orEmpty(),
            vin = fields["vin"].orEmpty()
        )
    }

    private fun escape(s: String): String = buildString {
        for (ch in s) {
            when (ch) {
                '%', ';', '=' -> append('%').append("%02X".format(ch.code))
                else -> append(ch)
            }
        }
    }

    private fun unescape(s: String): String? {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '%') {
                if (i + 2 >= s.length) return null
                sb.append(s.substring(i + 1, i + 3).toIntOrNull(16)?.toChar() ?: return null)
                i += 3
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }
}
