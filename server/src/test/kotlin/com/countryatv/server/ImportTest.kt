package com.countryatv.server

import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

// Records from before the shop system arrive as imports: their own action, never anyone's check-in.
class ImportTest {
    private val shop = TestShop()

    @Test
    fun `an imported unit and its status steps are recorded as an import, not as anyone's work`() = testApplication {
        shop.auth.createEmployee("Ray", "admin", "5678")
        val client = shop.client(this)
        val ray = client.signIn(shop.auth, "Ray", "5678")
        val old = unit().copy(source = "import")
        assertEquals(HttpStatusCode.Created, client.createUnit(ray, old).status)
        client.authedPost(ray, "/units/${old.id}/status", StatusChange("IN_STOCK", source = "import"))
        client.authedPost(ray, "/units/${old.id}/status", StatusChange("SOLD", source = "import"))

        assertEquals(listOf("unit.import", "unit.status:IN_STOCK", "unit.status:SOLD"),
            shop.column("SELECT action FROM audit_log WHERE entity_id = ? ORDER BY id", old.id))
        assertEquals(0, shop.count("SELECT COUNT(*) FROM audit_log WHERE action = 'unit.create'"))
        assertEquals(listOf("import", "import"), shop.column("SELECT source FROM unit_status_changes WHERE unit_id = ? ORDER BY id", old.id))
    }

    @Test
    fun `importing needs Import old records, and an unknown source is refused`() = testApplication {
        shop.auth.createEmployee("Mia", "management", "1234")
        val client = shop.client(this)
        val mia = client.signIn(shop.auth, "Mia", "1234")
        val refused = client.createUnit(mia, unit().copy(source = "import"))
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals(listOf(Permission.LEGACY_IMPORT.label), refused.body<ProblemBody>().details)
        assertEquals(HttpStatusCode.BadRequest, client.createUnit(mia, unit().copy(source = "spreadsheet")).status)
        assertEquals(0, shop.count("SELECT COUNT(*) FROM units"))
    }

    @Test
    fun `released phones that don't say import still upload as before`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val sam = client.signIn(shop.auth, "Sam", "1234")
        val u = unit()
        assertEquals(HttpStatusCode.Created, client.createUnit(sam, u).status)
        assertEquals(listOf("unit.create"), shop.column("SELECT action FROM audit_log WHERE entity_id = ?", u.id))
    }
}
