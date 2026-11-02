package com.countryatv.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// The migration runner: SQL and Kotlin steps, once each, in order, all or nothing.
class MigrationTest {

    @Test
    fun `a database from the released schema migrates to the current one and keeps every row`() {
        val old = OldShop()
        val before = Database(old.file.path, Database.MIGRATIONS.take(2)).use { old.rowCounts(it) }
        Database(old.file.path).use { db ->
            assertEquals(Database.MIGRATIONS.size, db.schemaVersion())
            val after = old.rowCounts(db)
            // Later steps may add rows (an audit row per migration, say) but never lose one.
            before.forEach { (table, n) -> assertTrue(after.getValue(table) >= n, "$table lost rows: $n before, ${after[table]} after") }
            assertEquals(before.getValue("units"), after.getValue("units"))
            assertEquals(before.getValue("employees"), after.getValue("employees"))
        }
    }

    @Test
    fun `a Kotlin step runs once, after the steps before it`() {
        val old = OldShop()
        var runs = 0
        val step = Migration.Code("mark notes") { c ->
            runs++
            c.createStatement().use { it.executeUpdate("UPDATE units SET notes = 'migrated'") }
        }
        val list = Database.MIGRATIONS.take(2) + step
        Database(old.file.path, list).close()
        Database(old.file.path, list).use { db ->
            assertEquals(1, runs)
            assertEquals(3, db.schemaVersion())
            assertEquals(3, db.read { c -> c.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM units WHERE notes = 'migrated'").use { it.next(); it.getInt(1) } } })
        }
    }

    @Test
    fun `a step that fails leaves the database exactly as it was`() {
        val old = OldShop()
        val good = Migration.Code("touch every unit") { c -> c.createStatement().use { it.executeUpdate("UPDATE units SET notes = 'half done'") } }
        val bad = Migration.Code("fails") { error("boom") }
        assertFailsWith<IllegalStateException> { Database(old.file.path, Database.MIGRATIONS.take(2) + good + bad) }
        Database(old.file.path, Database.MIGRATIONS.take(2)).use { db ->
            assertEquals(2, db.schemaVersion())
            assertEquals(0, db.read { c -> c.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM units WHERE notes = 'half done'").use { it.next(); it.getInt(1) } } })
        }
    }
}
