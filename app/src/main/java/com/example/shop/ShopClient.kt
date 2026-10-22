package com.example.shop

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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
    val updatedBy: String
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
data class UnitSaved(val unit: UnitDto, val overwroteOtherChanges: Boolean = false)
data class ScanMatch(val matchedBy: String, val unit: UnitDto)
private data class ProblemBody(val code: String, val message: String, val details: List<String> = emptyList())
private data class EnrollRequest(val code: String, val label: String)
private data class EnrollResponse(val deviceToken: String)
private data class LoginRequest(val name: String, val pin: String)
private data class LoginResponse(val sessionToken: String, val displayName: String, val role: String)

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
    val role: String?
)

// The only door to the shop system. It alone holds the phone's device token and the employee's
// session token: they are stored privately here and never handed to any other part of the app.
class ShopClient(
    context: Context,
    private val http: OkHttpClient = defaultHttp(),
    private val allowPlainHttp: Boolean = com.example.BuildConfig.DEBUG,
    prefsName: String = "shop_connection"
) {
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
                .apply()
            _state.value = readState()
            return ShopResult.Ok(Unit)
        }
        @Suppress("UNCHECKED_CAST")
        return result as ShopResult<Unit>
    }

    fun signOut() {
        prefs.edit().remove(KEY_SESSION).remove(KEY_NAME).remove(KEY_ROLE).apply()
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
        role = prefs.getString(KEY_ROLE, null)
    )

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_DEVICE = "device_token"
        private const val KEY_SESSION = "session_token"
        private const val KEY_NAME = "employee_name"
        private const val KEY_ROLE = "employee_role"
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
