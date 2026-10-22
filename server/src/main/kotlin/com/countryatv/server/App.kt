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
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable data class EnrollRequest(val code: String, val label: String)
@Serializable data class EnrollResponse(val deviceToken: String)
@Serializable data class LoginRequest(val name: String, val pin: String)
@Serializable data class LoginResponse(val sessionToken: String, val displayName: String, val role: String)
@Serializable data class SetupCodeResponse(val code: String, val expiresInHours: Int = 24)

const val DEVICE_HEADER = "X-Device-Token"

private val log = LoggerFactory.getLogger("InScanServer")

fun Application.inscanServer(db: Database) {
    val auth = Auth(db)
    val units = UnitStore(db)
    val tags = TagStore(db, units)

    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<ApiProblem> { call, e -> call.respond(e.status, e.body()) }
        exception<BadRequestException> { call, _ ->
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
            call.respond(LoginResponse(token, caller.displayName, caller.role))
        }
        post("/admin/setup-codes") {
            val caller = call.caller()
            if (!caller.isAdmin) throw ApiProblem(HttpStatusCode.Forbidden, Problem.NOT_ALLOWED)
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
            call.respond(HttpStatusCode.Created, units.create(call.receive<NewUnit>(), caller))
        }
        patch("/units/{id}") {
            val caller = call.caller()
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
            call.respond(HttpStatusCode.Created, tags.recordPrint(call.parameters["id"].orEmpty(), call.receive<TagPrinted>(), caller))
        }
        post("/units/{id}/tags/confirm") {
            val caller = call.caller()
            call.respond(tags.confirm(call.parameters["id"].orEmpty(), call.receive<TagConfirm>(), caller))
        }
    }
}
