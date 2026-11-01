package com.countryatv.server

import com.countryatv.core.Problem
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId

// Every shop policy is a Shop setting with a default, not a constant in code (roles spec §1).
// Only changed values are stored, so a default can still be improved for shops that never touched it.
enum class Setting(val key: String, val default: String, private val valid: (String) -> Boolean) {
    TIME_ZONE("shop.timeZone", "America/Los_Angeles", { v -> runCatching { ZoneId.of(v) }.isSuccess }),
    WEEK_START("shop.weekStart", "MONDAY", { v -> DayOfWeek.entries.any { it.name == v } }),
    OLD_STOCK_DAYS("stock.oldAfterDays", "90", { v -> v.toIntOrNull() in 1..3650 }),
    KEEP_EVENTS_MONTHS("history.keepMonths", "13", { v -> v.toIntOrNull() in 1..120 }),
    PHONE_SOLD_UNITS_KEPT("phone.soldUnitsKept", "200", { v -> v.toIntOrNull() in 0..10_000 }),
    STOCK_OPENS_ON("stock.opensOn", "IN_STOCK", { v -> v in setOf("ALL", "INCOMING", "IN_STOCK", "SALE_PENDING", "SOLD") }),
    MAX_QUEUE_AGE_DAYS("sync.maxQueueAgeDays", "30", { v -> v.toIntOrNull() in 1..365 });

    fun isValid(value: String) = valid(value)

    companion object {
        fun byKey(key: String): Setting? = entries.firstOrNull { it.key == key }
    }
}

class ShopSettings(private val db: Database, private val clock: Clock = Clock.systemUTC()) {

    fun get(setting: Setting): String = db.read { c ->
        c.prepareStatement("SELECT value FROM settings WHERE key = ?").use {
            it.setString(1, setting.key)
            it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }?.takeIf(setting::isValid) ?: setting.default

    fun all(): Map<String, String> = Setting.entries.associate { it.key to get(it) }

    fun zone(): ZoneId = ZoneId.of(get(Setting.TIME_ZONE))
    fun weekStart(): DayOfWeek = DayOfWeek.valueOf(get(Setting.WEEK_START))
    fun maxQueueAge(): Duration = Duration.ofDays(get(Setting.MAX_QUEUE_AGE_DAYS).toLong())
    fun calendar(): ShopCalendar = ShopCalendar(zone(), weekStart(), clock)

    // Who may change a setting is checked by the caller's permissions (step 8); this records it.
    fun set(setting: Setting, value: String, actor: String, deviceId: String?) {
        val clean = value.trim()
        if (!setting.isValid(clean)) {
            throw ApiProblem(HttpStatusCode.UnprocessableEntity, Problem.MISSING_DETAILS, listOf("a valid value for ${setting.key}"))
        }
        db.transaction { c ->
            val before = get(setting)
            c.prepareStatement(
                """INSERT INTO settings (key, value, updated_at, updated_by) VALUES (?,?,?,?)
                   ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at, updated_by = excluded.updated_by"""
            ).use {
                it.setString(1, setting.key); it.setString(2, clean); it.setString(3, clock.stamp()); it.setString(4, actor)
                it.executeUpdate()
            }
            audit(c, clock, actor, deviceId, "setting.change", "setting", setting.key,
                buildJsonObject { put("value", before) }.toString(), buildJsonObject { put("value", clean) }.toString())
        }
    }
}
