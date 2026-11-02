package com.countryatv.server

import com.countryatv.core.ItemId
import java.io.File

// A shop database as the server released before the groundwork left it: schema version 2, times in
// Instant.toString()'s variable width, both shapes of status audit rows, a phone's printed_at text.
// Written with raw SQL so it doesn't depend on today's code paths.
class OldShop {
    val file: File = File.createTempFile("inscan-old", ".db").also { it.delete(); it.deleteOnExit() }

    val dale = ItemId.new()
    val ray = ItemId.new()
    val phone = ItemId.new()
    val incoming = ItemId.new()
    val inStock = ItemId.new()
    val sold = ItemId.new()
    val tag = ItemId.new()
    val oddTag = ItemId.new()

    init {
        Database(file.path, Database.MIGRATIONS.take(2)).use { db ->
            db.transaction { c ->
                c.createStatement().use { st ->
                    st.execute("""INSERT INTO employees (id, display_name, role, pin_salt, pin_hash, failed_attempts, locked_until, active, created_at) VALUES
                        ('$dale','Dale','employee','s','h',0,'2026-10-27T16:05:00.123456Z',1,'2026-10-20T15:00:00Z'),
                        ('$ray','Ray','admin','s','h',0,NULL,1,'2026-10-20T15:00:00.5Z')""")
                    st.execute("INSERT INTO setup_codes (code_hash, created_by, expires_at, used_at) VALUES ('x','console','2026-10-21T15:00:00Z','2026-10-20T15:01:00.25Z')")
                    st.execute("INSERT INTO devices (id, label, token_hash, enrolled_at, revoked_at) VALUES ('$phone','Shop phone','t','2026-10-20T15:01:02.5Z',NULL)")
                    st.execute("INSERT INTO sessions (token_hash, employee_id, device_id, expires_at) VALUES ('st','$dale','$phone','2026-10-28T04:00:00.000000001Z')")
                    for ((id, status, at) in listOf(
                        Triple(incoming, "INCOMING", "2026-10-26T07:50:00Z"),
                        Triple(inStock, "IN_STOCK", "2026-10-26T07:51:00.1Z"),
                        Triple(sold, "SOLD", "2026-10-25T18:00:00.123Z")
                    )) {
                        st.execute("""INSERT INTO units (id, vin, vin_check, year, make, model, unit_type, price_cents, status, created_at, created_by, updated_at, updated_by)
                            VALUES ('$id', NULL, 'none', 2026, 'Honda', 'Rancher', 'ATV', 849900, '$status', '$at', '$dale', '$at', '$dale')""")
                        st.execute(audit(at, dale, "unit.create", id, null, unitJson(id, "INCOMING")))
                    }
                    st.execute("""INSERT INTO tags (id, unit_id, payload, printed_via, printed_at, printed_by, device_id, confirmed_at, confirmed_by)
                        VALUES ('$tag','$inStock','INSCAN1;id=$inStock','shared','2026-10-26T07:52:00Z','$dale','$phone','2026-10-26T08:05:00.250Z','$dale')""")
                    st.execute("""INSERT INTO tags (id, unit_id, payload, printed_via, printed_at, printed_by, device_id)
                        VALUES ('$oddTag','$incoming','INSCAN1;id=$incoming','shared','yesterday','$dale','$phone')""")
                    // The tag confirm's shape: before and after are bare units.
                    st.execute(audit("2026-10-26T08:05:00.25Z", dale, "unit.status:IN_STOCK", inStock, unitJson(inStock, "INCOMING"), unitJson(inStock, "IN_STOCK")))
                    // The /status route's shape: after wraps the unit, with an optional reason.
                    st.execute(audit("2026-10-25T18:30:00Z", ray, "unit.status:IN_STOCK", sold, unitJson(sold, "INCOMING"), """{"unit":${unitJson(sold, "IN_STOCK")}}"""))
                    st.execute(audit("2026-10-26T17:00:00.000001Z", ray, "unit.status:SOLD", sold, unitJson(sold, "IN_STOCK"), """{"unit":${unitJson(sold, "SOLD")}}"""))
                }
            }
        }
    }

    private fun unitJson(id: String, status: String) =
        """{"id":"$id","vin":null,"vinCheck":"none","vinFrameVerifiedBy":null,"year":2026,"make":"Honda","model":"Rancher","subModel":"","color":"","unitType":"ATV","priceCents":849900,"status":"$status","notes":"","version":1,"createdAt":"x","createdBy":"$dale","updatedAt":"x","updatedBy":"$dale"}"""

    private fun audit(at: String, actor: String, action: String, id: String, before: String?, after: String?): String {
        fun q(v: String?) = if (v == null) "NULL" else "'" + v.replace("'", "''") + "'"
        return "INSERT INTO audit_log (at, actor, device_id, action, entity, entity_id, before_json, after_json) VALUES " +
            "('$at','$actor','$phone','$action','unit','$id',${q(before)},${q(after)})"
    }

    fun rowCounts(db: Database): Map<String, Int> = db.read { c ->
        listOf("employees", "setup_codes", "devices", "sessions", "units", "tags", "audit_log").associateWith { t ->
            c.createStatement().use { st -> st.executeQuery("SELECT COUNT(*) FROM $t").use { it.next(); it.getInt(1) } }
        }
    }
}
