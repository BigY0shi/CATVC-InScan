package com.example.shop

import android.content.Context
import android.content.SharedPreferences
import com.countryatv.core.reports.ExportRequest
import com.countryatv.core.reports.HistoryPage
import com.countryatv.core.reports.ReportDto
import com.countryatv.core.reports.ReportSummary
import com.countryatv.core.reports.UnitFilter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.buffer
import okio.sink
import java.io.File
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

// Shapes match the shop server's JSON (server/.../Units.kt, App.kt).
data class UnitDto(
    val id: String,
    val vin: String?,
    val vinCheck: String,
    val vinFrameVerifiedBy: String?,
    val year: Int?,
    val make: String,
    val model: String,
    val subModel: String,
    val color: String,
    val unitType: String,
    val priceCents: Long,
    val status: String,
    val notes: String,
    val version: Int,
    val createdAt: String,
    val createdBy: String,
    val updatedAt: String,
    val updatedBy: String,
    // Sent by newer shop systems; older ones leave them out.
    val checkedInAt: String? = null,
    val inStockSince: String? = null
)

data class NewUnit(
    val id: String,
    val make: String,
    val model: String,
    val subModel: String = "",
    val year: Int? = null,
    val color: String = "",
    val unitType: String,
    val vin: String? = null,
    val vinFrameVerified: Boolean = false,
    val priceCents: Long,
    val notes: String = ""
)

data class UnitChanges(
    val baseVersion: Int,
    val make: String? = null,
    val model: String? = null,
    val subModel: String? = null,
    val year: Int? = null,
    val color: String? = null,
    val unitType: String? = null,
    val vin: String? = null,
    val vinFrameVerified: Boolean = false,
    val priceCents: Long? = null,
    val notes: String? = null
)

data class StatusChange(val status: String, val reason: String = "")
data class TagPrinted(val id: String, val payload: String, val printedVia: String, val printedAt: String, val reprintReason: String = "")
data class TagConfirm(val payload: String)
data class TagDto(
    val id: String, val unitId: String, val payload: String, val printedVia: String, val printedAt: String,
    val printedBy: String, val reprintReason: String, val confirmedAt: String?, val confirmedBy: String?
)
data class UnitSaved(val unit: UnitDto, val overwroteOtherChanges: Boolean = false)
data class ScanMatch(val matchedBy: String, val unit: UnitDto)
private data class ProblemBody(val code: String, val message: String, val details: List<String> = emptyList())
private data class EnrollRequest(val code: String, val label: String)
private data class EnrollResponse(val deviceToken: String)
private data class LoginRequest(val name: String, val pin: String)
// employeeId, roleName and permissions come from shop systems with roles; older ones leave them out.
private data class LoginResponse(
    val sessionToken: String,
    val displayName: String,
    val role: String,
    val employeeId: String = "",
    val roleName: String = "",
    val permissions: List<String> = emptyList()
)

// What a call to the shop system came to. Every outcome carries words an employee can read.
sealed class ShopResult<out T> {
    data class Ok<T>(val value: T) : ShopResult<T>()

    // The shop system answered and said no (missing details, duplicate VIN...). Retrying won't help.
    data class Refused(val code: String, val message: String, val details: List<String>) : ShopResult<Nothing>()

    // This phone needs to sign in (again). Pending changes are kept.
    data class NotSignedIn(val message: String) : ShopResult<Nothing>()

    // No answer: no signal, server down, or not set up yet. Safe to retry later.
    data class Unreachable(val message: String) : ShopResult<Nothing>()
}

data class ConnectionState(
    val serverUrl: String?,
    val enrolled: Boolean,
    val signedInAs: String?,
    val role: String?,
    // The signed-in person's id and permission names (server Permission, e.g. "SEE_REPORTS"), as
    // the shop system sent them at sign-in. Null when this sign-in predates roles: sign in again.
    val employeeId: String? = null,
    val permissions: Set<String>? = null
) {
    fun can(permission: String): Boolean = permissions?.contains(permission) ?: false
}

// Permission names the app reads (server Roles.kt). Only the shop system enforces them; the app
// uses them to decide what to show.
object Permissions {
    const val SEE_REPORTS = "SEE_REPORTS"
    const val EXPORT_REPORTS = "EXPORT_REPORTS"
    const val SEE_OWN_HISTORY = "SEE_OWN_HISTORY"
}

// The only door to the shop system. It alone holds the phone's device token and the employee's
// session token: they are stored privately here and never handed to any other part of the app.
class ShopClient(
    context: Context,
    private val http: OkHttpClient = defaultHttp(),
    private val allowPlainHttp: Boolean = com.example.BuildConfig.DEBUG,
    prefsName: String = "shop_connection"
) : ReportsApi {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val json = "application/json".toMediaType()

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    // Checks a typed address. Returns a plain message if it can't be used, null if it's fine.
    fun problemWithAddress(raw: String): String? {
        val url = raw.trim().trimEnd('/').toHttpUrlOrNull()
            ?: return "That doesn't look like a web address. It should start with https://"
        if (url.scheme != "https" && !(allowPlainHttp && url.host in LOCAL_DEV_HOSTS)) {
            return "The shop system address must start with https:// so customer details are encrypted on the way."
        }
        return null
    }

    suspend fun enroll(serverUrl: String, setupCode: String, phoneLabel: String): ShopResult<Unit> {
        problemWithAddress(serverUrl)?.let { return ShopResult.Refused("BAD_ADDRESS", it, emptyList()) }
        val base = serverUrl.trim().trimEnd('/')
        val result = call<EnrollResponse>(base, "POST", "/auth/enroll", EnrollRequest(setupCode.trim(), phoneLabel.trim()), auth = Auth.NONE)
        if (result is ShopResult.Ok) {
            prefs.edit().clear().putString(KEY_URL, base).putString(KEY_DEVICE, result.value.deviceToken).apply()
            _state.value = readState()
            return ShopResult.Ok(Unit)
        }
        @Suppress("UNCHECKED_CAST")
        return result as ShopResult<Unit>
    }

    suspend fun employeeNames(): ShopResult<List<String>> =
        call(baseOrNull(), "GET", "/auth/employees", null, auth = Auth.DEVICE, type = Types.newParameterizedType(List::class.java, String::class.java))

    suspend fun signIn(name: String, pin: String): ShopResult<Unit> {
        val result = call<LoginResponse>(baseOrNull(), "POST", "/auth/login", LoginRequest(name, pin), auth = Auth.DEVICE)
        if (result is ShopResult.Ok) {
            prefs.edit()
                .putString(KEY_SESSION, result.value.sessionToken)
                .putString(KEY_NAME, result.value.displayName)
                .putString(KEY_ROLE, result.value.role)
                .putString(KEY_EMPLOYEE, result.value.employeeId.ifBlank { null })
                .putString(KEY_PERMISSIONS, result.value.permissions.takeIf { it.isNotEmpty() }?.joinToString(","))
                .apply()
            _state.value = readState()
            return ShopResult.Ok(Unit)
        }
        @Suppress("UNCHECKED_CAST")
        return result as ShopResult<Unit>
    }

    fun signOut() {
        prefs.edit().remove(KEY_SESSION).remove(KEY_NAME).remove(KEY_ROLE).remove(KEY_EMPLOYEE).remove(KEY_PERMISSIONS).apply()
        _state.value = readState()
    }

    suspend fun listUnits(): ShopResult<List<UnitDto>> =
        call(baseOrNull(), "GET", "/units", null, type = Types.newParameterizedType(List::class.java, UnitDto::class.java))

    suspend fun byCode(code: String): ShopResult<ScanMatch> =
        call(baseOrNull(), "GET", "/units/by-code/" + java.net.URLEncoder.encode(code, "UTF-8").replace("+", "%20"), null)

    suspend fun createUnit(unit: NewUnit): ShopResult<UnitSaved> = call(baseOrNull(), "POST", "/units", unit)

    suspend fun updateUnit(id: String, changes: UnitChanges): ShopResult<UnitSaved> =
        call(baseOrNull(), "PATCH", "/units/$id", changes)

    suspend fun changeStatus(id: String, change: StatusChange): ShopResult<UnitSaved> =
        call(baseOrNull(), "POST", "/units/$id/status", change)

    suspend fun recordTag(unitId: String, tag: TagPrinted): ShopResult<TagDto> =
        call(baseOrNull(), "POST", "/units/$unitId/tags", tag)

    suspend fun confirmTag(unitId: String, confirm: TagConfirm): ShopResult<UnitSaved> =
        call(baseOrNull(), "POST", "/units/$unitId/tags/confirm", confirm)

    // ---------- Reports and My history (online only, D-RS3) ----------

    // Report shapes are kotlinx.serialization classes in core. Unknown fields from a newer shop
    // system are skipped; a filter is written back with every field, exactly as it came.
    private val reportJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val unitPageAdapter = moshi.adapter(UnitPage::class.java)

    override suspend fun reportSummary(): ShopResult<ReportSummary> =
        fetch("/reports/summary") { reportJson.decodeFromString(ReportSummary.serializer(), it) }

    override suspend fun report(key: String, range: RangeQuery): ShopResult<ReportDto> =
        fetch("/reports/" + enc(key) + range.query("?")) { reportJson.decodeFromString(ReportDto.serializer(), it) }

    override suspend fun unitQuery(filter: UnitFilter, cursor: String?, limit: Int): ShopResult<UnitPage> {
        val q = buildString {
            append("?filter=").append(enc(reportJson.encodeToString(UnitFilter.serializer(), filter)))
            if (cursor != null) append("&cursor=").append(enc(cursor))
            append("&limit=").append(limit)
        }
        return fetch("/units/query$q") { unitPageAdapter.fromJson(it) ?: throw JsonDataException("empty") }
    }

    override suspend fun history(personId: String?, range: RangeQuery, asOf: String?, cursor: String?): ShopResult<HistoryPage> {
        val path = if (personId == null) "/me/history" else "/people/" + enc(personId) + "/history"
        val q = buildString {
            append(range.query("?"))
            if (asOf != null) append(if (isEmpty()) "?" else "&").append("asOf=").append(enc(asOf))
            if (cursor != null) append(if (isEmpty()) "?" else "&").append("cursor=").append(enc(cursor))
        }
        return fetch(path + q) { reportJson.decodeFromString(HistoryPage.serializer(), it) }
    }

    // A POST: the export writes an audit row, which a retried GET would write twice (reports spec §8).
    // The body is streamed to the file only after the status is checked, because errors come back as JSON.
    override suspend fun exportCsv(key: String, request: ExportRequest, dir: File): ShopResult<File> {
        val body = reportJson.encodeToString(ExportRequest.serializer(), request).toRequestBody(json)
        val slowHttp = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
        return exchange("POST", "/reports/" + enc(key) + "/export", body, slowHttp) { response ->
            val name = Regex("filename=\"?([A-Za-z0-9._-]+)\"?").find(response.header("Content-Disposition").orEmpty())?.groupValues?.get(1)
                ?: "inscan-$key.csv"
            dir.mkdirs()
            val file = File(dir, name)
            file.sink().buffer().use { sink -> sink.writeAll(response.body!!.source()) }
            file
        }
    }

    private fun RangeQuery.query(lead: String): String = when {
        preset != null -> lead + "preset=" + enc(preset)
        from != null && to != null -> lead + "from=" + enc(from) + "&to=" + enc(to)
        else -> ""
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private suspend fun <T> fetch(path: String, decode: (String) -> T): ShopResult<T> =
        exchange("GET", path, null, http) { decode(it.body?.string().orEmpty()) }

    // One signed-in call that the caller's coroutine can cancel: cancelling it cancels the HTTP
    // call, and a reply that arrives for a cancelled request is dropped (reports spec §6).
    private suspend fun <T> exchange(method: String, path: String, body: okhttp3.RequestBody?, client: OkHttpClient, onOk: (okhttp3.Response) -> T): ShopResult<T> {
        val base = baseOrNull() ?: return ShopResult.Unreachable("This phone isn't connected to the shop system yet.")
        val session = prefs.getString(KEY_SESSION, null) ?: return ShopResult.NotSignedIn("Sign in with your PIN first.")
        val request = Request.Builder().url(base + path).header("Authorization", "Bearer $session").method(method, body).build()
        val call = client.newCall(request)
        return try {
            val response = suspendCancellableCoroutine<okhttp3.Response> { cont ->
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) { cont.resumeWith(Result.failure(e)) }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { cont.resumeWith(Result.success(response)) }
                })
            }
            withContext(Dispatchers.IO) {
                response.use {
                    if (it.isSuccessful) ShopResult.Ok(onOk(it)) else problemResult(it.code, it.body?.string().orEmpty())
                }
            }
        } catch (e: IOException) {
            ShopResult.Unreachable("Can't reach the shop system right now. Check the phone's Wi-Fi or signal.")
        } catch (e: JsonDataException) {
            ShopResult.Unreachable(UNEXPECTED)
        } catch (e: SerializationException) {
            ShopResult.Unreachable(UNEXPECTED)
        }
    }

    // The same refusal rules as call(): 401s sign out, 5xx and unreadable bodies are "unreachable".
    private fun problemResult(code: Int, text: String): ShopResult<Nothing> {
        val problem = runCatching { moshi.adapter(ProblemBody::class.java).fromJson(text) }.getOrNull()
        if (code == 401 && problem?.code in setOf("NOT_SIGNED_IN", "PHONE_NOT_ENROLLED")) {
            if (problem?.code == "NOT_SIGNED_IN") signOut()
            return ShopResult.NotSignedIn(problem!!.message)
        }
        if (code >= 500 || problem == null) return ShopResult.Unreachable(problem?.message ?: UNEXPECTED)
        return ShopResult.Refused(problem.code, problem.message, problem.details)
    }

    private enum class Auth { NONE, DEVICE, SESSION }

    private suspend inline fun <reified T> call(
        base: String?,
        method: String,
        path: String,
        body: Any?,
        auth: Auth = Auth.SESSION,
        type: Type = T::class.java
    ): ShopResult<T> = withContext(Dispatchers.IO) {
        if (base == null) return@withContext ShopResult.Unreachable("This phone isn't connected to the shop system yet.")
        val builder = Request.Builder().url(base + path)
        when (auth) {
            Auth.NONE -> Unit
            Auth.DEVICE -> builder.header("X-Device-Token", prefs.getString(KEY_DEVICE, null)
                ?: return@withContext ShopResult.NotSignedIn("This phone isn't set up for the shop system yet."))
            Auth.SESSION -> builder.header("Authorization", "Bearer " + (prefs.getString(KEY_SESSION, null)
                ?: return@withContext ShopResult.NotSignedIn("Sign in with your PIN first.")))
        }
        val requestBody = body?.let { moshi.adapter(it.javaClass).toJson(it).toRequestBody(json) }
        builder.method(method, requestBody)
        try {
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val value = moshi.adapter<T>(type).fromJson(text)
                        ?: return@withContext ShopResult.Refused("BAD_RESPONSE", UNEXPECTED, emptyList())
                    return@withContext ShopResult.Ok(value)
                }
                val problem = runCatching { moshi.adapter(ProblemBody::class.java).fromJson(text) }.getOrNull()
                if (response.code == 401 && problem?.code in setOf("NOT_SIGNED_IN", "PHONE_NOT_ENROLLED")) {
                    if (problem?.code == "NOT_SIGNED_IN") signOut()
                    return@withContext ShopResult.NotSignedIn(problem!!.message)
                }
                if (response.code >= 500 || problem == null) {
                    return@withContext ShopResult.Unreachable(problem?.message ?: UNEXPECTED)
                }
                ShopResult.Refused(problem.code, problem.message, problem.details)
            }
        } catch (e: IOException) {
            ShopResult.Unreachable("Can't reach the shop system right now. Check the phone's Wi-Fi or signal.")
        } catch (e: com.squareup.moshi.JsonDataException) {
            ShopResult.Unreachable(UNEXPECTED)
        }
    }

    private fun baseOrNull(): String? = prefs.getString(KEY_URL, null)

    private fun readState() = ConnectionState(
        serverUrl = prefs.getString(KEY_URL, null),
        enrolled = prefs.getString(KEY_DEVICE, null) != null,
        signedInAs = prefs.getString(KEY_NAME, null)?.takeIf { prefs.getString(KEY_SESSION, null) != null },
        role = prefs.getString(KEY_ROLE, null),
        employeeId = prefs.getString(KEY_EMPLOYEE, null),
        permissions = prefs.getString(KEY_PERMISSIONS, null)?.split(',')?.filter { it.isNotBlank() }?.toSet()
    )

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_DEVICE = "device_token"
        private const val KEY_SESSION = "session_token"
        private const val KEY_NAME = "employee_name"
        private const val KEY_ROLE = "employee_role"
        private const val KEY_EMPLOYEE = "employee_id"
        private const val KEY_PERMISSIONS = "employee_permissions"
        private const val UNEXPECTED = "The shop system sent back something this app doesn't understand. Update the app, then try again."

        // Plain http is accepted only in debug builds, and only for a server on this computer or the emulator's host.
        private val LOCAL_DEV_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
