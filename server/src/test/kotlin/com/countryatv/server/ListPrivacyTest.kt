package com.countryatv.server

import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// D-H4: bulk lists carry no one's id for people who can't see Reports. A single unit still does.
class ListPrivacyTest {
    private val shop = TestShop()

    @Test
    fun `an Employee's unit list holds no employee ids, a manager's does, and one unit's page still names people`() = testApplication {
        val samId = shop.auth.createEmployee("Sam", "employee", "1234")
        val rayId = shop.auth.createEmployee("Ray", "admin", "5678")
        val miaId = shop.auth.createEmployee("Mia", "management", "2468")
        val client = shop.client(this)
        val sam = client.signIn(shop.auth, "Sam", "1234")
        val ray = client.signIn(shop.auth, "Ray", "5678")
        val mia = client.signIn(shop.auth, "Mia", "2468")
        val samsUnit = unit().also { client.createUnit(sam, it) }
        val failingVin = unit(vin = "1M8GDM9AXKP042789").copy(vinFrameVerified = true).also { client.createUnit(ray, it) }
        client.authedPost(mia, "/units/${samsUnit.id}/status", StatusChange("IN_STOCK"))

        val employeeView = client.authedGet(sam, "/units").bodyAsText()
        for (id in listOf(samId, rayId, miaId)) assertFalse(id in employeeView, "an Employee's list must not carry $id")
        val rows = Json.parseToJsonElement(employeeView).jsonArray.map { it.jsonObject }
        assertEquals(2, rows.size)
        assertTrue(rows.all { "vinFrameVerifiedBy" !in it }, "no frame-check name in lists")
        assertTrue(rows.all { it["createdBy"].toString() == "\"\"" && it["updatedBy"].toString() == "\"\"" }, "kept, empty, for released phones")

        val managerView = client.authedGet(mia, "/units").body<List<UnitListItem>>()
        assertEquals(setOf(samId, rayId), managerView.map { it.createdBy }.toSet())
        assertEquals(miaId, managerView.single { it.id == samsUnit.id }.updatedBy)

        val onePage = client.authedGet(sam, "/units/${failingVin.id}").body<UnitDto>()
        assertEquals(rayId, onePage.vinFrameVerifiedBy, "a single unit still says who checked the frame")
        assertEquals(rayId, onePage.createdBy)
    }
}
