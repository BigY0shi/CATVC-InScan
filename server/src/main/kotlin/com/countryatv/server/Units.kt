package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.LabelPayload
import com.countryatv.core.Problem
import com.countryatv.core.UnitStatus
import com.countryatv.core.UnitType
import com.countryatv.core.Vin
import com.countryatv.core.VinCheck
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.sql.Connection
import java.sql.ResultSet

@Serializable
data class UnitDto(
    val id: String,
    val vin: String?,
    // ok | fail | not_applicable | none (no VIN entered)
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

// A new item, logged on a phone. The phone chooses the ID so it can log items while offline.
@Serializable
data class NewUnit(
    val id: String,
    val make: String,
    val model: String,
    val subModel: String = "",
    val year: Int? = null,
    val color: String = "",
    val unitType: String,
    val vin: String? = null,
    // Set only when the employee ticked "VIN matches the frame" for a VIN that fails the check.
    val vinFrameVerified: Boolean = false,
    val priceCents: Long,
    val notes: String = ""
)

// Changed fields only. baseVersion is the version the phone last saw, used to spot overlapping edits.
@Serializable
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

@Serializable
data class UnitSaved(
    val unit: UnitDto,
    // True when someone else changed this item after this phone last loaded it. The newer save
    // still won, and both versions are in the audit log; the app tells the employee.
    val overwroteOtherChanges: Boolean = false
)

@Serializable
data class ScanMatch(
    // "tag" (our QR tag), "id" (typed item ID), or "vin"
    val matchedBy: String,
    val unit: UnitDto
)

@Serializable
data class StatusChange(val status: String, val reason: String = "")

class UnitStore(private val db: Database) {
    private val json = Json { encodeDefaults = true }

    fun create(input: NewUnit, caller: Caller): UnitSaved = db.transaction { c ->
        if (!ItemId.isValid(input.id)) throw ApiProblem(HttpStatusCode.BadRequest, Problem.ITEM_ID_INVALID)
        val fields = validate(
            make = input.make, model = input.model, year = input.year, unitType = input.unitType,
            vin = input.vin, vinFrameVerified = input.vinFrameVerified, priceCents = input.priceCents
        )

        // Retrying the same save (e.g. after a dropped connection) is safe: same ID and same details
        // returns the item that is already there.
        find(c, input.id)?.let { existing ->
            val same = existing.make == input.make.trim() && existing.model == input.model.trim() &&
                existing.subModel == input.subModel.trim() && existing.year == input.year &&
                existing.color == input.color.trim() && existing.unitType == fields.unitType.label &&
                existing.vin == fields.vin && existing.priceCents == input.priceCents && existing.notes == input.notes.trim()
            if (same) return@transaction UnitSaved(existing)
            throw ApiProblem(HttpStatusCode.Conflict, Problem.ITEM_ID_TAKEN)
        }
        ensureVinFree(c, fields.vin, input.id)

        val at = now()
        c.prepareStatement(
            """INSERT INTO units (id, vin, vin_check, vin_frame_verified_by, year, make, model, sub_model, color, unit_type,
               price_cents, status, notes, version, created_at, created_by, updated_at, updated_by)
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?,?,?)"""
        ).use {
            it.setString(1, input.id); it.setString(2, fields.vin); it.setString(3, fields.vinCheck)
            it.setString(4, if (fields.needsFrameCheck) caller.employeeId else null)
            if (input.year == null) it.setNull(5, java.sql.Types.INTEGER) else it.setInt(5, input.year)
            it.setString(6, input.make.trim()); it.setString(7, input.model.trim()); it.setString(8, input.subModel.trim())
            it.setString(9, input.color.trim()); it.setString(10, fields.unitType.label); it.setLong(11, input.priceCents)
            it.setString(12, UnitStatus.INCOMING.name); it.setString(13, input.notes.trim())
            it.setString(14, at); it.setString(15, caller.employeeId); it.setString(16, at); it.setString(17, caller.employeeId)
            it.executeUpdate()
        }
        val created = find(c, input.id)!!
        audit(c, caller.employeeId, caller.deviceId, "unit.create", "unit", input.id, null, json.encodeToString(created))
        UnitSaved(created)
    }

    fun update(id: String, changes: UnitChanges, caller: Caller): UnitSaved = db.transaction { c ->
        val before = find(c, id) ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.ITEM_NOT_FOUND)
        val make = changes.make ?: before.make
        val model = changes.model ?: before.model
        val year = changes.year ?: before.year
        val unitType = changes.unitType ?: before.unitType
        val vinInput = changes.vin ?: before.vin
        val price = changes.priceCents ?: before.priceCents
        val vinChanged = changes.vin != null && Vin.normalize(changes.vin) != before.vin
        val fields = validate(
            make = make, model = model, year = year, unitType = unitType, vin = vinInput,
            // An unchanged VIN keeps its earlier frame confirmation.
            vinFrameVerified = if (vinChanged) changes.vinFrameVerified else (before.vinFrameVerifiedBy != null || changes.vinFrameVerified),
            priceCents = price
        )
        ensureVinFree(c, fields.vin, id)

        val frameVerifiedBy = when {
            !fields.needsFrameCheck -> null
            vinChanged || before.vinFrameVerifiedBy == null -> caller.employeeId
            else -> before.vinFrameVerifiedBy
        }
        c.prepareStatement(
            """UPDATE units SET vin = ?, vin_check = ?, vin_frame_verified_by = ?, year = ?, make = ?, model = ?, sub_model = ?,
               color = ?, unit_type = ?, price_cents = ?, notes = ?, version = version + 1, updated_at = ?, updated_by = ?
               WHERE id = ?"""
        ).use {
            it.setString(1, fields.vin); it.setString(2, fields.vinCheck); it.setString(3, frameVerifiedBy)
            if (year == null) it.setNull(4, java.sql.Types.INTEGER) else it.setInt(4, year)
            it.setString(5, make.trim()); it.setString(6, model.trim()); it.setString(7, (changes.subModel ?: before.subModel).trim())
            it.setString(8, (changes.color ?: before.color).trim()); it.setString(9, fields.unitType.label); it.setLong(10, price)
            it.setString(11, (changes.notes ?: before.notes).trim()); it.setString(12, now()); it.setString(13, caller.employeeId)
            it.setString(14, id)
            it.executeUpdate()
        }
        val after = find(c, id)!!
        val overlapped = changes.baseVersion != before.version
        audit(c, caller.employeeId, caller.deviceId, if (overlapped) "unit.update.overlapping" else "unit.update", "unit", id,
            json.encodeToString(before), json.encodeToString(after))
        UnitSaved(after, overwroteOtherChanges = overlapped)
    }

    fun changeStatus(id: String, change: StatusChange, caller: Caller): UnitSaved = db.transaction { c ->
        val before = find(c, id) ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.ITEM_NOT_FOUND)
        val next = UnitStatus.entries.firstOrNull { it.name == change.status }
            ?: throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST)
        val current = UnitStatus.valueOf(before.status)
        if (current == next) return@transaction UnitSaved(before)
        // Selling goes through the sale flow (milestone M3), never a bare status change.
        if (next == UnitStatus.SALE_PENDING || next == UnitStatus.SOLD || !current.canMoveTo(next)) {
            throw ApiProblem(HttpStatusCode.Conflict, Problem.STATUS_CHANGE_NOT_ALLOWED, listOf("${current.label} → ${next.label}"))
        }
        if ((next == UnitStatus.RETIRED || current == UnitStatus.SOLD) && !caller.isAdmin) {
            throw ApiProblem(HttpStatusCode.Forbidden, Problem.NOT_ALLOWED)
        }
        if (next == UnitStatus.RETIRED && change.reason.isBlank()) {
            throw ApiProblem(HttpStatusCode.UnprocessableEntity, Problem.MISSING_DETAILS, listOf("reason for retiring this item"))
        }
        c.prepareStatement("UPDATE units SET status = ?, version = version + 1, updated_at = ?, updated_by = ? WHERE id = ?").use {
            it.setString(1, next.name); it.setString(2, now()); it.setString(3, caller.employeeId); it.setString(4, id)
            it.executeUpdate()
        }
        val after = find(c, id)!!
        val afterJson = buildJsonObject {
            put("unit", json.encodeToJsonElement(after))
            if (change.reason.isNotBlank()) put("reason", change.reason.trim())
        }.toString()
        audit(c, caller.employeeId, caller.deviceId, "unit.status:${next.name}", "unit", id, json.encodeToString(before), afterJson)
        UnitSaved(after)
    }

    fun get(id: String): UnitDto = db.read { c -> find(c, id) } ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.ITEM_NOT_FOUND)

    fun list(status: String?, query: String?): List<UnitDto> = db.read { c ->
        val sql = StringBuilder("SELECT * FROM units WHERE 1=1")
        val args = mutableListOf<String>()
        if (!status.isNullOrBlank()) { sql.append(" AND status = ?"); args += status }
        if (!query.isNullOrBlank()) {
            sql.append(" AND (make LIKE ? OR model LIKE ? OR sub_model LIKE ? OR color LIKE ? OR vin LIKE ? OR id = ?)")
            val like = "%${query.trim()}%"
            args += listOf(like, like, like, like, like, query.trim())
        }
        sql.append(" ORDER BY created_at DESC LIMIT 500")
        c.prepareStatement(sql.toString()).use { st ->
            args.forEachIndexed { i, v -> st.setString(i + 1, v) }
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toUnit()) } }
        }
    }

    // Resolves whatever the camera read: our QR tag first, then a typed item ID, then a VIN.
    fun byCode(scanned: String): ScanMatch = db.read { c ->
        val raw = scanned.trim()
        LabelPayload.decode(raw)?.let { tag -> find(c, tag.id)?.let { return@read ScanMatch("tag", it) } }
        if (ItemId.isValid(raw)) find(c, raw)?.let { return@read ScanMatch("id", it) }
        val vin = Vin.normalize(raw)
        if (vin.isNotEmpty()) {
            c.prepareStatement("SELECT * FROM units WHERE vin = ?").use {
                it.setString(1, vin)
                it.executeQuery().use { rs -> if (rs.next()) return@read ScanMatch("vin", rs.toUnit()) }
            }
        }
        throw ApiProblem(HttpStatusCode.NotFound, Problem.ITEM_NOT_FOUND)
    }

    private data class Validated(val unitType: UnitType, val vin: String?, val vinCheck: String, val needsFrameCheck: Boolean)

    private fun validate(
        make: String, model: String, year: Int?, unitType: String, vin: String?, vinFrameVerified: Boolean, priceCents: Long
    ): Validated {
        val missing = buildList {
            if (make.isBlank()) add("make")
            if (model.isBlank()) add("model")
            if (priceCents <= 0) add("price")
        }
        if (missing.isNotEmpty()) throw ApiProblem(HttpStatusCode.UnprocessableEntity, Problem.MISSING_DETAILS, missing)
        if (year != null && year !in 1950..2100) {
            throw ApiProblem(HttpStatusCode.UnprocessableEntity, Problem.MISSING_DETAILS, listOf("a 4-digit model year"))
        }
        val type = UnitType.fromLabel(unitType)
            ?: throw ApiProblem(HttpStatusCode.UnprocessableEntity, Problem.MISSING_DETAILS, listOf("vehicle type"))
        val cleanVin = vin?.let { Vin.normalize(it) }?.takeIf { it.isNotEmpty() }
        val check = cleanVin?.let { Vin.check(it) }
        if (check is VinCheck.Fail && !vinFrameVerified) {
            throw ApiProblem(HttpStatusCode.UnprocessableEntity, Problem.VIN_NOT_CONFIRMED, listOf(check.message))
        }
        val checkName = when (check) {
            null -> "none"
            VinCheck.Ok -> "ok"
            VinCheck.NotApplicable -> "not_applicable"
            is VinCheck.Fail -> "fail"
        }
        return Validated(type, cleanVin, checkName, check is VinCheck.Fail)
    }

    private fun ensureVinFree(c: Connection, vin: String?, ownId: String) {
        if (vin == null) return
        c.prepareStatement("SELECT id FROM units WHERE vin = ? AND id <> ?").use {
            it.setString(1, vin); it.setString(2, ownId)
            it.executeQuery().use { rs -> if (rs.next()) throw ApiProblem(HttpStatusCode.Conflict, Problem.VIN_ALREADY_LOGGED, listOf(rs.getString(1))) }
        }
    }

    private fun find(c: Connection, id: String): UnitDto? =
        c.prepareStatement("SELECT * FROM units WHERE id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { rs -> if (rs.next()) rs.toUnit() else null }
        }

    private fun ResultSet.toUnit() = UnitDto(
        id = getString("id"),
        vin = getString("vin"),
        vinCheck = getString("vin_check"),
        vinFrameVerifiedBy = getString("vin_frame_verified_by"),
        year = intOrNull("year"),
        make = getString("make"),
        model = getString("model"),
        subModel = getString("sub_model"),
        color = getString("color"),
        unitType = getString("unit_type"),
        priceCents = getLong("price_cents"),
        status = getString("status"),
        notes = getString("notes"),
        version = getInt("version"),
        createdAt = getString("created_at"),
        createdBy = getString("created_by"),
        updatedAt = getString("updated_at"),
        updatedBy = getString("updated_by")
    )
}
