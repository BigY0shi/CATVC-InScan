package com.countryatv.server

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

// One SQLite file is the shop's system of record. A single connection guarded by a lock is plenty
// for four phones, and it keeps every write strictly ordered.
class Database(path: String, private val migrations: List<Migration> = MIGRATIONS) : AutoCloseable {
    // Load the SQLite driver explicitly rather than relying on automatic discovery, which fails
    // when more than one class loader is in play (as in the app's Robolectric tests).
    private val connection: Connection = Class.forName("org.sqlite.JDBC").let { DriverManager.getConnection("jdbc:sqlite:$path") }.apply {
        createStatement().use {
            it.execute("PRAGMA journal_mode=WAL")
            it.execute("PRAGMA foreign_keys=ON")
            it.execute("PRAGMA busy_timeout=5000")
        }
    }

    init {
        try {
            migrate()
        } catch (e: Throwable) {
            connection.close()
            throw e
        }
    }

    // Runs [block] in one transaction: all of its writes land, or none do.
    fun <T> transaction(block: (Connection) -> T): T = synchronized(connection) {
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (e: Throwable) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    fun <T> read(block: (Connection) -> T): T = synchronized(connection) { block(connection) }

    override fun close() = connection.close()

    // Versioned schema changes. Each runs once, in order, and is never edited after release:
    // to change the schema, append a new entry. Nothing here ever drops shop data. Every pending
    // step runs in one transaction with its version rows, so a step that fails leaves the database
    // exactly as it was, and the server refuses to start rather than run on a half-migrated file.
    private fun migrate() {
        transaction { c ->
            c.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)") }
            val current = c.createStatement().use { st ->
                st.executeQuery("SELECT MAX(version) FROM schema_version").use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            migrations.forEachIndexed { index, step ->
                val version = index + 1
                if (version > current) {
                    when (step) {
                        is Migration.Sql -> c.createStatement().use { st ->
                            step.sql.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { st.execute(it) }
                        }
                        is Migration.Code -> step.run(c)
                    }
                    c.prepareStatement("INSERT INTO schema_version (version) VALUES (?)").use { it.setInt(1, version); it.executeUpdate() }
                }
            }
        }
    }

    fun schemaVersion(): Int = read { c ->
        c.createStatement().use { st -> st.executeQuery("SELECT MAX(version) FROM schema_version").use { rs -> rs.next(); rs.getInt(1) } }
    }

    companion object {
        val MIGRATIONS: List<Migration> = listOf(
            Migration.Sql("""
            CREATE TABLE employees (
              id TEXT PRIMARY KEY,
              display_name TEXT NOT NULL UNIQUE,
              role TEXT NOT NULL CHECK (role IN ('employee','admin')),
              pin_salt TEXT NOT NULL,
              pin_hash TEXT NOT NULL,
              failed_attempts INTEGER NOT NULL DEFAULT 0,
              locked_until TEXT,
              active INTEGER NOT NULL DEFAULT 1,
              created_at TEXT NOT NULL
            );
            CREATE TABLE setup_codes (
              code_hash TEXT PRIMARY KEY,
              created_by TEXT NOT NULL,
              expires_at TEXT NOT NULL,
              used_at TEXT
            );
            CREATE TABLE devices (
              id TEXT PRIMARY KEY,
              label TEXT NOT NULL,
              token_hash TEXT NOT NULL UNIQUE,
              enrolled_at TEXT NOT NULL,
              revoked_at TEXT
            );
            CREATE TABLE sessions (
              token_hash TEXT PRIMARY KEY,
              employee_id TEXT NOT NULL REFERENCES employees(id),
              device_id TEXT NOT NULL REFERENCES devices(id),
              expires_at TEXT NOT NULL
            );
            CREATE TABLE units (
              id TEXT PRIMARY KEY,
              vin TEXT UNIQUE,
              vin_check TEXT NOT NULL CHECK (vin_check IN ('ok','fail','not_applicable','none')),
              vin_frame_verified_by TEXT,
              year INTEGER,
              make TEXT NOT NULL,
              model TEXT NOT NULL,
              sub_model TEXT NOT NULL DEFAULT '',
              color TEXT NOT NULL DEFAULT '',
              unit_type TEXT NOT NULL,
              price_cents INTEGER NOT NULL CHECK (price_cents > 0),
              status TEXT NOT NULL,
              notes TEXT NOT NULL DEFAULT '',
              version INTEGER NOT NULL DEFAULT 1,
              created_at TEXT NOT NULL,
              created_by TEXT NOT NULL,
              updated_at TEXT NOT NULL,
              updated_by TEXT NOT NULL
            );
            CREATE TABLE audit_log (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              at TEXT NOT NULL,
              actor TEXT NOT NULL,
              device_id TEXT,
              action TEXT NOT NULL,
              entity TEXT NOT NULL,
              entity_id TEXT NOT NULL,
              before_json TEXT,
              after_json TEXT
            )
            """.trimIndent()),
            // M2: printed tags. A unit can have several (reprints). The confirm-scan proves the
            // label on the box reads, and is what moves a new unit from Incoming to In Stock.
            Migration.Sql("""
            CREATE TABLE tags (
              id TEXT PRIMARY KEY,
              unit_id TEXT NOT NULL REFERENCES units(id),
              payload TEXT NOT NULL,
              printed_via TEXT NOT NULL,
              printed_at TEXT NOT NULL,
              printed_by TEXT NOT NULL,
              device_id TEXT,
              reprint_reason TEXT NOT NULL DEFAULT '',
              confirmed_at TEXT,
              confirmed_by TEXT
            );
            CREATE INDEX tags_unit ON tags(unit_id)
            """.trimIndent()),
            // 3: one time format. Instant.toString() drops trailing zeros, so "...:00Z" sorted after
            // "...:00.5Z" as text. Every stored time becomes fixed-width UTC with milliseconds. A
            // value that isn't a time (a phone once sent anything for printed_at) is left as it was.
            Migration.Code("fixed-width UTC times") { c -> rewriteTimes(c, TIME_COLUMNS_AT_V2) }
        )

        // Every time column in the released schema (version 2). Frozen: later tables write
        // fixed-width times from the start.
        private val TIME_COLUMNS_AT_V2 = mapOf(
            "employees" to listOf("locked_until", "created_at"),
            "setup_codes" to listOf("expires_at", "used_at"),
            "devices" to listOf("enrolled_at", "revoked_at"),
            "sessions" to listOf("expires_at"),
            "units" to listOf("created_at", "updated_at"),
            "audit_log" to listOf("at"),
            "tags" to listOf("printed_at", "confirmed_at")
        )

        private val log = LoggerFactory.getLogger("InScanMigrations")

        private fun rewriteTimes(c: Connection, columns: Map<String, List<String>>) {
            var left = 0
            for ((table, cols) in columns) for (col in cols) {
                val rows = c.createStatement().use { st ->
                    st.executeQuery("SELECT rowid, $col FROM $table WHERE $col IS NOT NULL").use { rs ->
                        buildList { while (rs.next()) add(rs.getLong(1) to rs.getString(2)) }
                    }
                }
                c.prepareStatement("UPDATE $table SET $col = ? WHERE rowid = ?").use { up ->
                    for ((rowid, text) in rows) {
                        val instant = parseTime(text)
                        if (instant == null) { left++; continue }
                        val fixed = stampOf(instant)
                        if (fixed != text) { up.setString(1, fixed); up.setLong(2, rowid); up.executeUpdate() }
                    }
                }
            }
            if (left > 0) log.warn("$left stored value(s) in time columns weren't times and were left as they were")
        }
    }
}

// One schema step. SQL steps are plain statements split on ";" (so no ";" inside a literal). Code
// steps read and rewrite data that SQL alone can't, such as reformatting every stored time.
sealed interface Migration {
    data class Sql(val sql: String) : Migration
    class Code(val name: String, val run: (Connection) -> Unit) : Migration
}

// The server's clock, written as text. Every stored time comes from an injected Clock, so tests can move time.
fun Clock.stamp(): String = stampOf(instant())

// Fixed-width UTC with milliseconds, e.g. 2026-11-01T07:50:00.000Z, so text order is time order.
fun stampOf(instant: Instant): String = STAMP.format(instant)

private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

// Reads a time a phone or an older server wrote: any ISO-8601 instant, with Z or an offset.
fun parseTime(text: String?): Instant? {
    if (text.isNullOrBlank()) return null
    val t = text.trim()
    return runCatching { Instant.parse(t) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(t).toInstant() }.getOrNull()
}

fun ResultSet.stringOrNull(column: String): String? = getString(column)

fun ResultSet.intOrNull(column: String): Int? = getInt(column).let { if (wasNull()) null else it }
