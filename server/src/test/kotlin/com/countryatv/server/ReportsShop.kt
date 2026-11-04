package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.LabelContent
import com.countryatv.core.LabelPayload
import com.countryatv.core.reports.UnitFilter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.test.assertEquals

// A shop with a week of work in it, for the Reports and My history tests. The clock reads
// Wed 4 Nov 2026, noon Pacific; This week is Mon 2 Nov to today (week starts Monday).
//
// Dale (Employee), Ray (Management), Ann (Admin):
//   u1  Dale checks in Mon 9:00, prints its tag; Ray confirms it Mon 11:00 (In stock), marks it Sold Tue 10:00.
//   u2  Dale checks in Tue 9:00, prints its tag, changes its price; Ray changes the price again. Incoming.
//   u3  Dale checks in Wed 8:00, make typed "polaris" (same make as u2's "Polaris"). No tag. Incoming.
//   u4  Ray checks in Sun 1 Nov 11:50 pm (last week), prints its tag; Dale confirms it Mon 10:00 (In stock).
//   u5  Ray checks in Wed 28 Oct (last week), prints, confirms it Thu 29 Oct. Dale reprints it Tue
//       ("damaged"), confirms the new tag, and changes its price.
//   u6  Ann imports an old record and moves it In stock (as an import): nobody's check-in.
class ReportsShop(val shop: TestShop = TestShop(MutableClock(Instant.parse("2026-11-04T20:00:00Z")))) {
    lateinit var client: HttpClient
    lateinit var dale: String
    lateinit var ray: String
    lateinit var ann: String
    lateinit var daleId: String
    lateinit var rayId: String
    lateinit var annId: String
    val u = mutableMapOf<String, NewUnit>()
    private val json = Json { encodeDefaults = true }

    suspend fun start(app: ApplicationTestBuilder, seed: Boolean = true): ReportsShop {
        daleId = shop.auth.createEmployee("Dale", "employee", "1111")
        rayId = shop.auth.createEmployee("Ray", "management", "2222")
        annId = shop.auth.createEmployee("Ann", "admin", "3333")
        client = shop.client(app)
        dale = client.signIn(shop.auth, "Dale", "1111", "Front phone")
        ray = client.signIn(shop.auth, "Ray", "2222", "Office phone")
        ann = client.signIn(shop.auth, "Ann", "3333", "Back phone")
        if (seed) seed()
        return this
    }

    fun payload(unitId: String) = LabelPayload.encode(LabelContent(id = unitId, make = u.values.first { it.id == unitId }.make, model = "X"))

    suspend fun checkIn(session: String, key: String, at: String, make: String = "Honda", type: String = "Dirt Bike", price: Long = 849_900, source: String? = null): NewUnit {
        val n = unit(make = make, priceCents = price).copy(unitType = type, occurredAt = at, source = source)
        u[key] = n
        assertEquals(HttpStatusCode.Created, client.createUnit(session, n).status, "check in $key")
        return n
    }

    suspend fun print(session: String, key: String, at: String, reason: String = ""): String {
        val id = ItemId.new()
        val r = client.authedPost(session, "/units/${u.getValue(key).id}/tags", TagPrinted(id, payload(u.getValue(key).id), "printer:QL-820", at, reason))
        assertEquals(HttpStatusCode.Created, r.status, "print $key")
        return id
    }

    suspend fun confirm(session: String, key: String, at: String) =
        assertEquals(HttpStatusCode.OK, client.authedPost(session, "/units/${u.getValue(key).id}/tags/confirm", TagConfirm(payload(u.getValue(key).id), at)).status, "confirm $key")

    suspend fun status(session: String, key: String, to: String, at: String, source: String? = null) =
        assertEquals(HttpStatusCode.OK, client.authedPost(session, "/units/${u.getValue(key).id}/status", StatusChange(to, occurredAt = at, source = source)).status, "status $key")

    suspend fun editPrice(session: String, key: String, cents: Long, at: String? = null) {
        val id = u.getValue(key).id
        val version = client.authedGet(session, "/units/$id").body<UnitDto>().version
        val r = client.patch("/units/$id") {
            header("Authorization", "Bearer $session"); contentType(ContentType.Application.Json); setBody(UnitChanges(baseVersion = version, priceCents = cents, occurredAt = at))
        }
        assertEquals(HttpStatusCode.OK, r.status, "edit $key")
    }

    private suspend fun seed() {
        checkIn(dale, "u1", "2026-11-02T17:00:00Z", make = "Honda", price = 849_900)
        print(dale, "u1", "2026-11-02T17:05:00Z")
        confirm(ray, "u1", "2026-11-02T19:00:00Z")
        status(ray, "u1", "SOLD", "2026-11-03T18:00:00Z")

        checkIn(dale, "u2", "2026-11-03T17:00:00Z", make = "Polaris", type = "ATV", price = 989_900)
        print(dale, "u2", "2026-11-03T17:02:00Z")
        editPrice(dale, "u2", 949_900)
        editPrice(ray, "u2", 929_900)

        checkIn(dale, "u3", "2026-11-04T16:00:00Z", make = "polaris", type = "ATV", price = 1_099_900)

        checkIn(ray, "u4", "2026-11-02T07:50:00Z", make = "Can-Am", type = "Side-by-Side (UTV)", price = 2_199_900)
        print(ray, "u4", "2026-11-02T07:55:00Z")
        confirm(dale, "u4", "2026-11-02T18:00:00Z")

        checkIn(ray, "u5", "2026-10-28T18:00:00Z", make = "Kawasaki", type = "Jet Ski", price = 1_499_900)
        print(ray, "u5", "2026-10-28T18:05:00Z")
        confirm(ray, "u5", "2026-10-29T18:00:00Z")
        print(dale, "u5", "2026-11-03T20:00:00Z", reason = "damaged")
        confirm(dale, "u5", "2026-11-03T20:05:00Z")
        editPrice(dale, "u5", 1_449_900)

        checkIn(ann, "u6", "2026-11-03T21:00:00Z", make = "Yamaha", price = 399_900, source = "import")
        status(ann, "u6", "IN_STOCK", "2026-11-03T21:01:00Z", source = "import")
    }

    suspend fun query(session: String, filter: UnitFilter, cursor: String? = null, limit: Int? = null): HttpResponse = client.get("/units/query") {
        header("Authorization", "Bearer $session")
        parameter("filter", json.encodeToString(filter))
        cursor?.let { parameter("cursor", it) }
        limit?.let { parameter("limit", it) }
    }

    suspend fun get(session: String, path: String, vararg params: Pair<String, String>): HttpResponse = client.get(path) {
        header("Authorization", "Bearer $session")
        for ((k, v) in params) parameter(k, v)
    }
}
