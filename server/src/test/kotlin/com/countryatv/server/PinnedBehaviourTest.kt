package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Today's behaviour, pinned before the groundwork changes anything underneath it. If one of these
// goes red, a change broke something phones already rely on.
class PinnedBehaviourTest {
    private val shop = TestShop()

    @Test
    fun `the unit list filters by status and searches make, model, sub-model, colour, VIN and the full ID`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val rancher = unit(make = "Honda", model = "FourTrax Rancher", subModel = "4x4", color = "Red", vin = "1M8GDM9AXKP042788")
        val rzr = unit(make = "Polaris", model = "RZR XP 1000", color = "Grey")
        val yz = unit(make = "Yamaha", model = "YZ250F", color = "Blue")
        for (u in listOf(rancher, rzr, yz)) assertEquals(HttpStatusCode.Created, client.createUnit(s, u).status)
        client.authedPost(s, "/units/${yz.id}/status", StatusChange("IN_STOCK"))

        suspend fun ids(query: String) = client.authedGet(s, "/units$query").body<List<UnitDto>>().map { it.id }.toSet()

        assertEquals(setOf(rancher.id, rzr.id, yz.id), ids(""))
        assertEquals(setOf(rancher.id, rzr.id), ids("?status=INCOMING"))
        assertEquals(setOf(yz.id), ids("?status=IN_STOCK"))
        assertEquals(setOf(rancher.id), ids("?q=rancher"), "model, any case")
        assertEquals(setOf(rancher.id), ids("?q=4x4"), "sub-model")
        assertEquals(setOf(rzr.id), ids("?q=grey"), "colour")
        assertEquals(setOf(rancher.id), ids("?q=${rancher.vin!!.takeLast(6)}"), "part of the VIN")
        assertEquals(setOf(yz.id), ids("?q=${yz.id}"), "the full ID")
        assertEquals(emptySet(), ids("?q=${yz.id.takeLast(8)}"), "the list search doesn't match the 8-character tag ID today")
        assertEquals(setOf(rzr.id), ids("?q=polaris&status=INCOMING"))
    }

    @Test
    fun `each status change writes one audit row, and the same status twice writes nothing`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val u = unit()
        client.createUnit(s, u)

        assertEquals(HttpStatusCode.OK, client.authedPost(s, "/units/${u.id}/status", StatusChange("IN_STOCK")).status)
        assertEquals(HttpStatusCode.OK, client.authedPost(s, "/units/${u.id}/status", StatusChange("IN_STOCK")).status)
        assertEquals(1, shop.count("SELECT COUNT(*) FROM audit_log WHERE action = 'unit.status:IN_STOCK' AND entity_id = ?", u.id))
        // The status route's after-record wraps the unit; the tag confirm's (below) is the bare unit.
        val after = Json.parseToJsonElement(shop.single("SELECT after_json FROM audit_log WHERE action = 'unit.status:IN_STOCK'")!!).jsonObject
        assertTrue("unit" in after)
    }

    @Test
    fun `a tag print writes one row, its confirm writes the confirm and the move, and a second confirm writes nothing`() = testApplication {
        shop.auth.createEmployee("Sam", "employee", "1234")
        val client = shop.client(this)
        val s = client.signIn(shop.auth, "Sam", "1234")
        val u = unit()
        client.createUnit(s, u)
        val payload = LabelPayload.encode(LabelContent(id = u.id, make = u.make, model = u.model))
        val printed = client.authedPost(s, "/units/${u.id}/tags", TagPrinted(ItemId.new(), payload, "shared", "2026-10-27T16:00:00Z"))
        assertEquals(HttpStatusCode.Created, printed.status)
        assertEquals("2026-10-27T16:00:00.000Z", printed.body<TagDto>().printedAt, "stored fixed-width since groundwork step 3")

        repeat(2) { assertEquals(HttpStatusCode.OK, client.authedPost(s, "/units/${u.id}/tags/confirm", TagConfirm(payload)).status) }
        assertEquals(
            listOf("unit.create", "tag.print", "tag.confirm", "unit.status:IN_STOCK"),
            shop.column("SELECT action FROM audit_log WHERE action <> 'login' AND action NOT LIKE 'device.%' AND action NOT LIKE 'employee.%' ORDER BY id")
        )
        val after = Json.parseToJsonElement(shop.single("SELECT after_json FROM audit_log WHERE action = 'unit.status:IN_STOCK'")!!).jsonObject
        assertTrue("unit" !in after && "status" in after, "the confirm's after-record is the bare unit")
        assertEquals("IN_STOCK", shop.single("SELECT status FROM units WHERE id = ?", u.id))
    }
}
