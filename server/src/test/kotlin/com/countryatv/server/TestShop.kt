package com.countryatv.server

import com.countryatv.core.ItemId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

// A clock a test can move. Starts at a fixed instant, so stored times are predictable.
class MutableClock(var now: Instant = Instant.parse("2026-10-27T16:00:00Z"), private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun advance(by: Duration) { now = now.plus(by) }
    fun set(instant: String) { now = Instant.parse(instant) }
}

// One shop for one test: its own database file in a temp directory, no shared state, no real shop
// data, and a clock the test controls.
class TestShop(val clock: MutableClock = MutableClock()) {
    val dbFile: File = File.createTempFile("inscan-test", ".db").also { it.delete(); it.deleteOnExit() }
    val db = Database(dbFile.path)
    val auth = Auth(db, clock)

    fun client(app: ApplicationTestBuilder): HttpClient {
        app.application { inscanServer(db, clock) }
        return app.createClient { install(ContentNegotiation) { json() } }
    }

    fun count(sql: String, vararg args: String): Int = db.read { c ->
        c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setString(i + 1, a) }
            st.executeQuery().use { it.next(); it.getInt(1) }
        }
    }

    fun single(sql: String, vararg args: String): String? = db.read { c ->
        c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setString(i + 1, a) }
            st.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
    }

    fun column(sql: String, vararg args: String): List<String?> = db.read { c ->
        c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setString(i + 1, a) }
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }
}

suspend fun HttpClient.signIn(auth: Auth, name: String, pin: String, label: String = "Phone"): String {
    val device = post("/auth/enroll") {
        contentType(ContentType.Application.Json); setBody(EnrollRequest(auth.newSetupCode("test"), label))
    }.body<EnrollResponse>().deviceToken
    return post("/auth/login") {
        header(DEVICE_HEADER, device); contentType(ContentType.Application.Json); setBody(LoginRequest(name, pin))
    }.body<LoginResponse>().sessionToken
}

fun unit(
    id: String = ItemId.new(),
    make: String = "Honda",
    model: String = "CRF450R",
    subModel: String = "",
    color: String = "Red",
    vin: String? = null,
    priceCents: Long = 849_900
) = NewUnit(id = id, make = make, model = model, subModel = subModel, year = 2025, color = color, unitType = "Dirt Bike",
    vin = vin, priceCents = priceCents)

suspend fun HttpClient.createUnit(session: String, unit: NewUnit): HttpResponse = post("/units") {
    header("Authorization", "Bearer $session"); contentType(ContentType.Application.Json); setBody(unit)
}

suspend fun HttpClient.authedGet(session: String, path: String): HttpResponse = get(path) {
    header("Authorization", "Bearer $session")
}

suspend inline fun <reified T> HttpClient.authedPost(session: String, path: String, body: T): HttpResponse = post(path) {
    header("Authorization", "Bearer $session"); contentType(ContentType.Application.Json); setBody(body)
}
