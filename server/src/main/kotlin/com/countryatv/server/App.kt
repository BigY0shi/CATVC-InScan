package com.countryatv.server

import com.countryatv.core.Problem
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Clock

@Serializable data class EnrollRequest(val code: String, val label: String)
@Serializable data class EnrollResponse(val deviceToken: String)
@Serializable data class LoginRequest(val name: String, val pin: String)
@Serializable data class LoginResponse(
    val sessionToken: String,
    val displayName: String,
    // "admin" or "employee", for phones released before roles. New phones read the fields below.
    val role: String,
    val employeeId: String = "",
    val roleName: String = "",
    // Permission names. The app hides what isn't here; the server refuses it anyway.
    val permissions: List<String> = emptyList()
)
@Serializable data class SetupCodeResponse(val code: String, val expiresInHours: Int = 24)

const val DEVICE_HEADER = "X-Device-Token"

private val UNATTRIBUTED = setOf(Problem.NOT_SIGNED_IN, Problem.PHONE_NOT_ENROLLED)

private val log = LoggerFactory.getLogger("InScanServer")

fun Application.inscanServer(db: Database, clock: Clock = Clock.systemUTC()) {
    val auth = Auth(db, clock)
    val settings = ShopSettings(db, clock)
    val units = UnitStore(db, clock, settings::maxQueueAge)
    val tags = TagStore(db, units, clock, settings::maxQueueAge)

    install(ContentNegotiation) { json() }
    // A refused request is recorded as refused:<CODE> in its own transaction, after the request's
    // own writes rolled back, so the record survives the refusal (reporting rule 4). Requests from
    // phones that aren't enrolled or signed in can't be attributed and aren't recorded.
    fun recordRefusal(call: ApplicationCall, code: String, status: HttpStatusCode, details: List<String>) {
        runCatching {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()
            val caller = runCatching { auth.callerFor(token) }.getOrNull()
            val deviceId = caller?.deviceId ?: runCatching { auth.deviceIdFor(call.request.headers[DEVICE_HEADER]) }.getOrNull()
            val after = buildJsonObject {
                put("status", status.value)
                put("details", JsonArray(details.map { JsonPrimitive(it) }))
            }.toString()
            db.transaction { c ->
                audit(c, clock, caller?.employeeId ?: "unknown", deviceId, "refused:$code", "request",
                    "${call.request.httpMethod.value} ${call.request.path()}", null, after)
            }
        }.onFailure { log.error("Couldn't record a refusal", it) }
    }

    install(StatusPages) {
        exception<ApiProblem> { call, e ->
            if (!e.alreadyAudited && e.problem !in UNATTRIBUTED) recordRefusal(call, e.problem.name, e.status, e.details)
            call.respond(e.status, e.body())
        }
        exception<BadRequestException> { call, _ ->
            recordRefusal(call, Problem.BAD_REQUEST.name, HttpStatusCode.BadRequest, emptyList())
            call.respond(HttpStatusCode.BadRequest, ProblemBody(Problem.BAD_REQUEST.name, Problem.BAD_REQUEST.message))
        }
        exception<Throwable> { call, e ->
            // Full detail goes to the server log for the owner; the phone gets a plain message.
            log.error("Unhandled error on ${call.request.local.uri}", e)
            call.respond(HttpStatusCode.InternalServerError, ProblemBody(Problem.SERVER_ERROR.name, Problem.SERVER_ERROR.message))
        }
    }

    fun ApplicationCall.caller(): Caller =
        auth.callerFor(request.headers["Authorization"]?.removePrefix("Bearer ")?.trim())

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        post("/auth/enroll") {
            val body = call.receive<EnrollRequest>()
            call.respond(EnrollResponse(auth.enrollDevice(body.code, body.label)))
        }
        // Names for the sign-in list. Only enrolled phones can see them.
        get("/auth/employees") {
            auth.deviceIdFor(call.request.headers[DEVICE_HEADER])
            call.respond(auth.employeeNames())
        }
        post("/auth/login") {
            val deviceId = auth.deviceIdFor(call.request.headers[DEVICE_HEADER])
            val body = call.receive<LoginRequest>()
            val token = auth.login(deviceId, body.name, body.pin)
            val caller = auth.callerFor(token)
            call.respond(LoginResponse(token, caller.displayName, caller.legacyRole, caller.employeeId, caller.roleName,
                caller.permissions.sortedBy { it.ordinal }.map { it.name }))
        }
        post("/admin/setup-codes") {
            val caller = call.caller()
            caller.require(Permission.CONNECT_PHONE)
            call.respond(SetupCodeResponse(auth.newSetupCode(caller.employeeId)))
        }

        get("/units") {
            call.caller()
            call.respond(units.list(call.request.queryParameters["status"], call.request.queryParameters["q"]))
        }
        get("/units/by-code/{code}") {
            call.caller()
            call.respond(units.byCode(call.parameters["code"].orEmpty()))
        }
        get("/units/{id}") {
            call.caller()
            call.respond(units.get(call.parameters["id"].orEmpty()))
        }
        post("/units") {
            val caller = call.caller()
            caller.require(Permission.SCAN_AND_TAG)
            call.respond(HttpStatusCode.Created, units.create(call.receive<NewUnit>(), caller))
        }
        patch("/units/{id}") {
            val caller = call.caller()
            caller.require(Permission.EDIT_UNITS)
            call.respond(units.update(call.parameters["id"].orEmpty(), call.receive<UnitChanges>(), caller))
        }
        post("/units/{id}/status") {
            val caller = call.caller()
            call.respond(units.changeStatus(call.parameters["id"].orEmpty(), call.receive<StatusChange>(), caller))
        }
        get("/units/{id}/tags") {
            call.caller()
            call.respond(tags.forUnit(call.parameters["id"].orEmpty()))
        }
        post("/units/{id}/tags") {
            val caller = call.caller()
            caller.require(Permission.SCAN_AND_TAG)
            call.respond(HttpStatusCode.Created, tags.recordPrint(call.parameters["id"].orEmpty(), call.receive<TagPrinted>(), caller))
        }
        post("/units/{id}/tags/confirm") {
            val caller = call.caller()
            caller.require(Permission.SCAN_AND_TAG)
            call.respond(tags.confirm(call.parameters["id"].orEmpty(), call.receive<TagConfirm>(), caller))
        }
    }
}
