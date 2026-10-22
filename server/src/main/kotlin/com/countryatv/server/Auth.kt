package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.Problem
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class Caller(
    val employeeId: String,
    val displayName: String,
    val role: String,
    val deviceId: String
) {
    val isAdmin: Boolean get() = role == "admin"
}

// Phones are enrolled once with a one-time setup code, then each employee signs in with a PIN.
// Only hashes of codes, tokens and PINs are stored. Nothing secret is ever returned twice.
class Auth(private val db: Database, private val clock: () -> Instant = Instant::now) {
    private val random = SecureRandom()

    fun createEmployee(displayName: String, role: String, pin: String): String {
        require(role == "employee" || role == "admin") { "role must be employee or admin" }
        require(pin.length in 4..8 && pin.all { it.isDigit() }) { "PIN must be 4 to 8 digits" }
        val id = ItemId.new()
        val salt = randomToken(16)
        db.transaction { c ->
            c.prepareStatement(
                "INSERT INTO employees (id, display_name, role, pin_salt, pin_hash, created_at) VALUES (?,?,?,?,?,?)"
            ).use {
                it.setString(1, id); it.setString(2, displayName.trim()); it.setString(3, role)
                it.setString(4, salt); it.setString(5, hashPin(pin, salt)); it.setString(6, clock().toString())
                it.executeUpdate()
            }
            audit(c, "system", null, "employee.create", "employee", id, null, buildJsonObject { put("displayName", displayName.trim()); put("role", role) }.toString())
        }
        return id
    }

    // A one-time code the owner reads out or types on a new phone. Valid for 24 hours.
    fun newSetupCode(createdBy: String): String {
        val code = (1..8).map { "ABCDEFGHJKMNPQRSTVWXYZ23456789"[random.nextInt(30)] }.joinToString("")
        db.transaction { c ->
            c.prepareStatement("INSERT INTO setup_codes (code_hash, created_by, expires_at) VALUES (?,?,?)").use {
                it.setString(1, sha256(code)); it.setString(2, createdBy); it.setString(3, clock().plus(Duration.ofHours(24)).toString())
                it.executeUpdate()
            }
        }
        return code
    }

    fun enrollDevice(code: String, label: String): String = db.transaction { c ->
        val hash = sha256(code.trim().uppercase())
        val valid = c.prepareStatement("SELECT expires_at, used_at FROM setup_codes WHERE code_hash = ?").use {
            it.setString(1, hash)
            it.executeQuery().use { rs -> rs.next() && rs.getString("used_at") == null && Instant.parse(rs.getString("expires_at")).isAfter(clock()) }
        }
        if (!valid) throw ApiProblem(HttpStatusCode.Unauthorized, Problem.SETUP_CODE_INVALID)
        c.prepareStatement("UPDATE setup_codes SET used_at = ? WHERE code_hash = ?").use {
            it.setString(1, clock().toString()); it.setString(2, hash); it.executeUpdate()
        }
        val deviceId = ItemId.new()
        val token = randomToken(32)
        c.prepareStatement("INSERT INTO devices (id, label, token_hash, enrolled_at) VALUES (?,?,?,?)").use {
            it.setString(1, deviceId); it.setString(2, label.trim().ifEmpty { "Phone" }); it.setString(3, sha256(token)); it.setString(4, clock().toString())
            it.executeUpdate()
        }
        audit(c, "system", deviceId, "device.enroll", "device", deviceId, null, buildJsonObject { put("label", label.trim()) }.toString())
        token
    }

    fun deviceIdFor(deviceToken: String?): String {
        if (deviceToken.isNullOrBlank()) throw ApiProblem(HttpStatusCode.Unauthorized, Problem.PHONE_NOT_ENROLLED)
        return db.read { c ->
            c.prepareStatement("SELECT id FROM devices WHERE token_hash = ? AND revoked_at IS NULL").use {
                it.setString(1, sha256(deviceToken))
                it.executeQuery().use { rs -> if (rs.next()) rs.getString("id") else null }
            }
        } ?: throw ApiProblem(HttpStatusCode.Unauthorized, Problem.PHONE_NOT_ENROLLED)
    }

    fun employeeNames(): List<String> = db.read { c ->
        c.prepareStatement("SELECT display_name FROM employees WHERE active = 1 ORDER BY display_name").use {
            it.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    // Returns a session token valid for 12 hours on this phone. Five wrong PINs lock the employee for 5 minutes.
    fun login(deviceId: String, displayName: String, pin: String): String = db.transaction { c ->
        data class Row(val id: String, val salt: String, val hash: String, val failed: Int, val lockedUntil: String?)
        val row = c.prepareStatement(
            "SELECT id, pin_salt, pin_hash, failed_attempts, locked_until FROM employees WHERE display_name = ? AND active = 1"
        ).use {
            it.setString(1, displayName.trim())
            it.executeQuery().use { rs ->
                if (rs.next()) Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5)) else null
            }
        } ?: throw ApiProblem(HttpStatusCode.Unauthorized, Problem.WRONG_PIN)

        if (row.lockedUntil != null && Instant.parse(row.lockedUntil).isAfter(clock())) {
            throw ApiProblem(HttpStatusCode.TooManyRequests, Problem.PIN_LOCKED)
        }
        if (!MessageDigest.isEqual(hashPin(pin, row.salt).toByteArray(), row.hash.toByteArray())) {
            val failed = row.failed + 1
            val lock = if (failed >= 5) clock().plus(Duration.ofMinutes(5)).toString() else null
            c.prepareStatement("UPDATE employees SET failed_attempts = ?, locked_until = ? WHERE id = ?").use {
                it.setInt(1, if (lock != null) 0 else failed); it.setString(2, lock); it.setString(3, row.id); it.executeUpdate()
            }
            audit(c, row.id, deviceId, "login.failed", "employee", row.id, null, null)
            // Commit the failed attempt even though the request is refused.
            c.commit()
            throw ApiProblem(HttpStatusCode.Unauthorized, if (lock != null) Problem.PIN_LOCKED else Problem.WRONG_PIN)
        }
        c.prepareStatement("UPDATE employees SET failed_attempts = 0, locked_until = NULL WHERE id = ?").use {
            it.setString(1, row.id); it.executeUpdate()
        }
        val token = randomToken(32)
        c.prepareStatement("INSERT INTO sessions (token_hash, employee_id, device_id, expires_at) VALUES (?,?,?,?)").use {
            it.setString(1, sha256(token)); it.setString(2, row.id); it.setString(3, deviceId)
            it.setString(4, clock().plus(Duration.ofHours(12)).toString())
            it.executeUpdate()
        }
        audit(c, row.id, deviceId, "login", "employee", row.id, null, null)
        token
    }

    fun callerFor(sessionToken: String?): Caller {
        if (sessionToken.isNullOrBlank()) throw ApiProblem(HttpStatusCode.Unauthorized, Problem.NOT_SIGNED_IN)
        return db.read { c ->
            c.prepareStatement(
                """SELECT e.id, e.display_name, e.role, s.device_id, s.expires_at FROM sessions s
                   JOIN employees e ON e.id = s.employee_id
                   JOIN devices d ON d.id = s.device_id
                   WHERE s.token_hash = ? AND e.active = 1 AND d.revoked_at IS NULL"""
            ).use {
                it.setString(1, sha256(sessionToken))
                it.executeQuery().use { rs ->
                    if (rs.next() && Instant.parse(rs.getString(5)).isAfter(clock())) {
                        Caller(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))
                    } else {
                        null
                    }
                }
            }
        } ?: throw ApiProblem(HttpStatusCode.Unauthorized, Problem.NOT_SIGNED_IN)
    }

    private fun randomToken(bytes: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also { random.nextBytes(it) })

    companion object {
        fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        fun hashPin(pin: String, salt: String): String {
            val spec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 210_000, 256)
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Base64.getEncoder().encodeToString(key)
        }
    }
}

// Every write records who did it, from which phone, and the record before and after.
fun audit(
    c: java.sql.Connection,
    actor: String,
    deviceId: String?,
    action: String,
    entity: String,
    entityId: String,
    beforeJson: String?,
    afterJson: String?
) {
    c.prepareStatement(
        "INSERT INTO audit_log (at, actor, device_id, action, entity, entity_id, before_json, after_json) VALUES (?,?,?,?,?,?,?,?)"
    ).use {
        it.setString(1, now()); it.setString(2, actor); it.setString(3, deviceId); it.setString(4, action)
        it.setString(5, entity); it.setString(6, entityId); it.setString(7, beforeJson); it.setString(8, afterJson)
        it.executeUpdate()
    }
}
