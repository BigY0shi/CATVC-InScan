package com.countryatv.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The main report, history and stock queries search an index instead of reading whole tables.
class IndexTest {
    private val shop = TestShop()

    private fun plan(sql: String): String = shop.db.read { c ->
        c.prepareStatement("EXPLAIN QUERY PLAN $sql").use { st ->
            for (i in 1..sql.count { it == '?' }) st.setString(i, "x")
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString("detail")) }.joinToString(" | ") }
        }
    }

    private fun assertUses(index: String, sql: String) {
        val p = plan(sql)
        assertTrue(index in p, "expected $index in: $p")
        assertFalse(Regex("""SCAN \w+(?! USING)""").containsMatchIn(p.replace(Regex("SCAN \\w+ USING (COVERING )?INDEX"), "")), "full scan in: $p")
    }

    @Test
    fun `report, history and stock queries use their indexes`() {
        assertUses("audit_action_occurred", "SELECT COUNT(*) FROM audit_log WHERE action = 'unit.create' AND occurred_at >= ? AND occurred_at < ?")
        assertUses("audit_actor_occurred", "SELECT action, entity_id, occurred_at FROM audit_log WHERE actor = ? AND occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at DESC")
        assertUses("audit_entity_action", "SELECT * FROM audit_log WHERE entity_id = ? AND action LIKE 'unit.%'")
        assertUses("units_status", "SELECT status, COUNT(*) FROM units GROUP BY status")
        assertUses("units_checked_in", "SELECT COUNT(*) FROM units WHERE checked_in_at >= ? AND checked_in_at < ?")
        assertUses("tags_printed", "SELECT COUNT(*) FROM tags WHERE printed_at >= ? AND printed_at < ?")
        assertUses("status_changes_to", "SELECT COUNT(*), SUM(price_cents) FROM unit_status_changes WHERE to_status = 'SOLD' AND occurred_at >= ? AND occurred_at < ?")
        assertUses("status_changes_unit", "SELECT * FROM unit_status_changes WHERE unit_id = ? ORDER BY occurred_at")
    }
}
