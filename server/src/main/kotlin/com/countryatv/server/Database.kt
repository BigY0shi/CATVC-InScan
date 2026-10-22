package com.countryatv.server

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant

// One SQLite file is the shop's system of record. A single connection guarded by a lock is plenty
// for four phones, and it keeps every write strictly ordered.
class Database(path: String) : AutoCloseable {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$path").apply {
        createStatement().use {
            it.execute("PRAGMA journal_mode=WAL")
            it.execute("PRAGMA foreign_keys=ON")
            it.execute("PRAGMA busy_timeout=5000")
        }
    }

    init {
        migrate()
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
    // to change the schema, append a new entry. Nothing here ever drops shop data.
    private fun migrate() {
        transaction { c ->
            c.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)") }
            val current = c.createStatement().use { st ->
                st.executeQuery("SELECT MAX(version) FROM schema_version").use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
            MIGRATIONS.forEachIndexed { index, sql ->
                val version = index + 1
                if (version > current) {
                    c.createStatement().use { st -> sql.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { st.execute(it) } }
                    c.prepareStatement("INSERT INTO schema_version (version) VALUES (?)").use { it.setInt(1, version); it.executeUpdate() }
                }
            }
        }
    }

    companion object {
        val MIGRATIONS = listOf(
            """
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
            """.trimIndent()
        )
    }
}

fun now(): String = Instant.now().toString()

fun ResultSet.stringOrNull(column: String): String? = getString(column)

fun ResultSet.intOrNull(column: String): Int? = getInt(column).let { if (wasNull()) null else it }
