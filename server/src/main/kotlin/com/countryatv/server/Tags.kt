package com.countryatv.server

import com.countryatv.core.ItemId
import com.countryatv.core.LabelPayload
import com.countryatv.core.Problem
import com.countryatv.core.UnitStatus
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.ResultSet

// A phone printed a tag. Sent after the print (possibly later, from the phone's queue).
@Serializable
data class TagPrinted(
    // Made on the phone, so a resend after a dropped connection doesn't record the print twice.
    val id: String,
    // Exactly what the QR code on the label says.
    val payload: String,
    // How it was printed, e.g. "printer:<model>" or "shared" (sent to another app to print).
    val printedVia: String,
    // When the phone printed it (phones may send later).
    val printedAt: String,
    val reprintReason: String = ""
)

// What the camera read off the label on the box when the employee confirmed it.
@Serializable
data class TagConfirm(val payload: String)

@Serializable
data class TagDto(
    val id: String,
    val unitId: String,
    val payload: String,
    val printedVia: String,
    val printedAt: String,
    val printedBy: String,
    val reprintReason: String,
    val confirmedAt: String?,
    val confirmedBy: String?
)

class TagStore(private val db: Database, private val units: UnitStore) {
    private val json = Json { encodeDefaults = true }

    fun recordPrint(unitId: String, input: TagPrinted, caller: Caller): TagDto = db.transaction { c ->
        units.find(c, unitId) ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.ITEM_NOT_FOUND)
        if (!ItemId.isValid(input.id)) throw ApiProblem(HttpStatusCode.BadRequest, Problem.ITEM_ID_INVALID)
        // The label must be one of ours and point at this item; anything else is a bug worth refusing.
        if (LabelPayload.decode(input.payload)?.id != unitId) throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST)
        find(c, input.id)?.let { existing ->
            if (existing.unitId == unitId && existing.payload == input.payload) return@transaction existing
            throw ApiProblem(HttpStatusCode.Conflict, Problem.ITEM_ID_TAKEN)
        }
        c.prepareStatement(
            """INSERT INTO tags (id, unit_id, payload, printed_via, printed_at, printed_by, device_id, reprint_reason)
               VALUES (?,?,?,?,?,?,?,?)"""
        ).use {
            it.setString(1, input.id); it.setString(2, unitId); it.setString(3, input.payload)
            it.setString(4, input.printedVia.trim().take(80)); it.setString(5, input.printedAt)
            it.setString(6, caller.employeeId); it.setString(7, caller.deviceId); it.setString(8, input.reprintReason.trim())
            it.executeUpdate()
        }
        val tag = find(c, input.id)!!
        audit(c, caller.employeeId, caller.deviceId, "tag.print", "tag", tag.id, null, json.encodeToString(tag))
        tag
    }

    // The employee scanned the label on the box and it read. A new (Incoming) item is now In Stock.
    // The QR code carries the item's ID, not the tag's, so this confirms the newest tag printed for
    // the item: the one most likely on the box.
    fun confirm(unitId: String, input: TagConfirm, caller: Caller): UnitSaved = db.transaction { c ->
        units.find(c, unitId) ?: throw ApiProblem(HttpStatusCode.NotFound, Problem.ITEM_NOT_FOUND)
        if (LabelPayload.decode(input.payload)?.id != unitId) throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST)
        val tag = c.prepareStatement("SELECT * FROM tags WHERE unit_id = ? ORDER BY printed_at DESC, rowid DESC LIMIT 1").use {
            it.setString(1, unitId)
            it.executeQuery().use { rs -> if (rs.next()) rs.toTag() else null }
        } ?: throw ApiProblem(HttpStatusCode.Conflict, Problem.TAG_NOT_RECORDED)
        val tagId = tag.id
        if (tag.confirmedAt == null) {
            c.prepareStatement("UPDATE tags SET confirmed_at = ?, confirmed_by = ? WHERE id = ?").use {
                it.setString(1, now()); it.setString(2, caller.employeeId); it.setString(3, tagId)
                it.executeUpdate()
            }
            audit(c, caller.employeeId, caller.deviceId, "tag.confirm", "tag", tagId, json.encodeToString(tag), json.encodeToString(find(c, tagId)))
        }
        val before = units.find(c, tag.unitId)!!
        if (before.status != UnitStatus.INCOMING.name) return@transaction UnitSaved(before)
        c.prepareStatement("UPDATE units SET status = ?, version = version + 1, updated_at = ?, updated_by = ? WHERE id = ?").use {
            it.setString(1, UnitStatus.IN_STOCK.name); it.setString(2, now()); it.setString(3, caller.employeeId); it.setString(4, tag.unitId)
            it.executeUpdate()
        }
        val after = units.find(c, tag.unitId)!!
        audit(c, caller.employeeId, caller.deviceId, "unit.status:IN_STOCK", "unit", tag.unitId, json.encodeToString(before), json.encodeToString(after))
        UnitSaved(after)
    }

    fun forUnit(unitId: String): List<TagDto> = db.read { c ->
        c.prepareStatement("SELECT * FROM tags WHERE unit_id = ? ORDER BY printed_at").use {
            it.setString(1, unitId)
            it.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toTag()) } }
        }
    }

    private fun find(c: java.sql.Connection, id: String): TagDto? =
        c.prepareStatement("SELECT * FROM tags WHERE id = ?").use {
            it.setString(1, id)
            it.executeQuery().use { rs -> if (rs.next()) rs.toTag() else null }
        }

    private fun ResultSet.toTag() = TagDto(
        id = getString("id"),
        unitId = getString("unit_id"),
        payload = getString("payload"),
        printedVia = getString("printed_via"),
        printedAt = getString("printed_at"),
        printedBy = getString("printed_by"),
        reprintReason = getString("reprint_reason"),
        confirmedAt = getString("confirmed_at"),
        confirmedBy = getString("confirmed_by")
    )
}
