package com.example.shop

import com.countryatv.core.ItemId
import com.countryatv.core.LabelPayload
import com.countryatv.core.UnitStatus
import com.countryatv.core.Vin
import com.countryatv.core.VinCheck
import com.example.data.OutboxEntry
import com.example.data.ShopDao
import com.example.data.ShopUnitEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

// What one round of sending and fetching came to. The app turns this into the banner and messages.
data class SyncReport(
    val sent: Int,
    // Changes the shop system refused this round; each one is kept, marked for attention.
    val refused: Int,
    val stillWaiting: Int,
    // Items where someone else had saved newer details first. Our save still went through.
    val overlapped: List<String>,
    // Null when the round finished; otherwise why it stopped (no signal, needs sign-in).
    val stoppedBecause: String?,
    val needsSignIn: Boolean
)

// The fields an employee can change on an item. Null means "leave as it is".
data class ItemEdits(
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

// Keeps this phone's copy of the inventory and the shop system in step. Every change is written
// to the phone first (so it works with no signal), then sent oldest first when the shop system
// can be reached. Nothing is dropped: a refused change waits, with its reason, for an employee.
class ShopSync(
    private val dao: ShopDao,
    private val client: ShopClient,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val newUnitJson = moshi.adapter(NewUnit::class.java)
    private val editsJson = moshi.adapter(ItemEdits::class.java)
    private val statusJson = moshi.adapter(StatusChange::class.java)
    private val tagJson = moshi.adapter(TagPrinted::class.java)
    private val tagConfirmJson = moshi.adapter(TagConfirm::class.java)
    private val oneRoundAtATime = Mutex()

    // Logs a new item on this phone. It shows in the inventory straight away and is sent later.
    suspend fun logNew(unit: NewUnit): ShopUnitEntity {
        require(ItemId.isValid(unit.id)) { "Item IDs come from ItemId.new()" }
        val vin = unit.vin?.let { Vin.normalize(it) }?.takeIf { it.isNotEmpty() }
        val local = ShopUnitEntity(
            id = unit.id, vin = vin, vinCheck = vinCheckName(vin), year = unit.year, make = unit.make.trim(),
            model = unit.model.trim(), subModel = unit.subModel.trim(), color = unit.color.trim(),
            unitType = unit.unitType, priceCents = unit.priceCents, status = UnitStatus.INCOMING.name,
            notes = unit.notes.trim(), version = 0, updatedAt = Instant.ofEpochMilli(clock()).toString(),
            updatedBy = client.state.value.signedInAs.orEmpty()
        )
        dao.addLocally(local, OutboxEntry(itemId = unit.id, kind = KIND_CREATE, payloadJson = newUnitJson.toJson(unit), createdAt = clock()))
        return dao.unit(unit.id)!!
    }

    suspend fun logEdits(id: String, edits: ItemEdits) {
        val entry = OutboxEntry(itemId = id, kind = KIND_UPDATE, payloadJson = editsJson.toJson(edits), createdAt = clock())
        dao.changeLocally(id, entry) { current ->
            current.copy(
                make = edits.make?.trim() ?: current.make,
                model = edits.model?.trim() ?: current.model,
                subModel = edits.subModel?.trim() ?: current.subModel,
                year = edits.year ?: current.year,
                color = edits.color?.trim() ?: current.color,
                unitType = edits.unitType ?: current.unitType,
                vin = if (edits.vin != null) Vin.normalize(edits.vin).takeIf { it.isNotEmpty() } else current.vin,
                priceCents = edits.priceCents ?: current.priceCents,
                notes = edits.notes?.trim() ?: current.notes
            )
        }
    }

    suspend fun logStatus(id: String, status: UnitStatus, reason: String = "") {
        val entry = OutboxEntry(itemId = id, kind = KIND_STATUS, payloadJson = statusJson.toJson(StatusChange(status.name, reason)), createdAt = clock())
        dao.changeLocally(id, entry) { it.copy(status = status.name) }
    }

    // A tag was printed (or handed to another app to print) for this item. Recorded with the
    // shop system later if there's no signal. Returns the tag's ID.
    suspend fun logTagPrinted(itemId: String, payload: String, printedVia: String, reprintReason: String = ""): String {
        val tag = TagPrinted(
            id = ItemId.new(clock()), payload = payload, printedVia = printedVia,
            printedAt = Instant.ofEpochMilli(clock()).toString(), reprintReason = reprintReason.trim()
        )
        dao.enqueue(OutboxEntry(itemId = itemId, kind = KIND_TAG_PRINT, payloadJson = tagJson.toJson(tag), createdAt = clock()))
        return tag.id
    }

    // The employee scanned the label on the box and it read: a new item is now In Stock.
    suspend fun logTagConfirmed(itemId: String, scannedPayload: String) {
        val entry = OutboxEntry(itemId = itemId, kind = KIND_TAG_CONFIRM, payloadJson = tagConfirmJson.toJson(TagConfirm(scannedPayload)), createdAt = clock())
        dao.changeLocally(itemId, entry) { if (it.status == UnitStatus.INCOMING.name) it.copy(status = UnitStatus.IN_STOCK.name) else null }
    }

    sealed class PhoneMatch {
        data class Found(val unit: ShopUnitEntity, val byTag: Boolean) : PhoneMatch()
        // More than one item ends in the typed 8 characters.
        object ShortIdClash : PhoneMatch()
        object NotFound : PhoneMatch()
    }

    // Finds what the camera read (or the employee typed) in this phone's copy: our QR tag, an item
    // ID, the 8-character ID printed on a tag, then a VIN.
    suspend fun matchOnPhone(scanned: String): PhoneMatch {
        val raw = scanned.trim()
        LabelPayload.decode(raw)?.let { tag -> dao.unit(tag.id)?.let { return PhoneMatch.Found(it, byTag = true) } }
        if (ItemId.isValid(raw)) dao.unit(raw)?.let { return PhoneMatch.Found(it, byTag = false) }
        ItemId.normalizeShort(raw)?.let { short ->
            val hits = dao.unitsByShortId(short)
            if (hits.size > 1) return PhoneMatch.ShortIdClash
            hits.singleOrNull()?.let { return PhoneMatch.Found(it, byTag = false) }
        }
        val vin = Vin.normalize(raw)
        return dao.unitByVin(vin).takeIf { vin.isNotEmpty() }?.let { PhoneMatch.Found(it, byTag = false) } ?: PhoneMatch.NotFound
    }

    suspend fun findOnPhone(scanned: String): ShopUnitEntity? = (matchOnPhone(scanned) as? PhoneMatch.Found)?.unit

    // Asks the shop system about a code this phone doesn't know, and keeps a copy of the answer.
    suspend fun findOnServer(scanned: String): ShopResult<ShopUnitEntity> =
        when (val r = client.byCode(scanned.trim())) {
            is ShopResult.Ok -> {
                dao.refreshFromServer(r.value.unit.toEntity())
                ShopResult.Ok(dao.unit(r.value.unit.id)!!)
            }
            is ShopResult.Refused -> r
            is ShopResult.NotSignedIn -> r
            is ShopResult.Unreachable -> r
        }

    // The employee corrected a refused change (or just wants it tried again). The corrected
    // details also replace what this phone shows for the item.
    suspend fun retry(entry: OutboxEntry, correctedUnit: NewUnit? = null, correctedEdits: ItemEdits? = null) {
        val payload = when {
            correctedUnit != null && entry.kind == KIND_CREATE -> {
                require(correctedUnit.id == entry.itemId) { "A corrected item keeps its ID" }
                dao.unit(entry.itemId)?.let { local ->
                    dao.upsertUnit(
                        local.copy(
                            vin = correctedUnit.vin?.let { Vin.normalize(it) }?.takeIf { it.isNotEmpty() },
                            year = correctedUnit.year, make = correctedUnit.make.trim(), model = correctedUnit.model.trim(),
                            subModel = correctedUnit.subModel.trim(), color = correctedUnit.color.trim(),
                            unitType = correctedUnit.unitType, priceCents = correctedUnit.priceCents, notes = correctedUnit.notes.trim()
                        )
                    )
                }
                newUnitJson.toJson(correctedUnit)
            }
            correctedEdits != null && entry.kind == KIND_UPDATE -> editsJson.toJson(correctedEdits)
            else -> entry.payloadJson
        }
        dao.retry(entry.seq, payload)
    }

    // The new-item details a refused "new item" change carried, so a screen can show them for fixing.
    fun newUnitIn(entry: OutboxEntry): NewUnit? =
        if (entry.kind == KIND_CREATE) newUnitJson.fromJson(entry.payloadJson) else null

    fun editsIn(entry: OutboxEntry): ItemEdits? =
        if (entry.kind == KIND_UPDATE) editsJson.fromJson(entry.payloadJson) else null

    // The employee chose to throw away a refused change. Only ever called from an explicit button.
    // A refused new item that never reached the shop system is removed from this phone too.
    suspend fun discard(entry: OutboxEntry) {
        dao.removeFromOutbox(entry.seq)
        val local = dao.unit(entry.itemId)
        if (entry.kind == KIND_CREATE && local != null && local.version == 0) {
            dao.outbox().filter { it.itemId == entry.itemId }.forEach { dao.removeFromOutbox(it.seq) }
            dao.deleteUnit(entry.itemId)
        }
    }

    suspend fun syncNow(): SyncReport = oneRoundAtATime.withLock {
        var sent = 0
        var refused = 0
        val overlapped = mutableListOf<String>()
        var stopped: String? = null
        var needsSignIn = false
        // Once a change to an item is refused, later changes to that item wait behind it.
        val blocked = mutableSetOf<String>()

        for (entry in dao.outbox()) {
            if (entry.state == OutboxEntry.STATE_NEEDS_ATTENTION || entry.itemId in blocked) {
                blocked += entry.itemId
                continue
            }
            val result = send(entry)
            when (result) {
                is ShopResult.Ok -> {
                    val saved = result.value
                    if (saved == null) {
                        dao.removeFromOutbox(entry.seq)
                    } else {
                        dao.recordSent(entry.seq, saved.unit.toEntity())
                        if (saved.overwroteOtherChanges) overlapped += saved.unit.id
                    }
                    sent++
                }
                is ShopResult.Refused -> {
                    dao.markNeedsAttention(entry.seq, result.code, result.message, result.details.joinToString("; ").ifEmpty { null })
                    blocked += entry.itemId
                    refused++
                }
                is ShopResult.NotSignedIn -> {
                    stopped = result.message
                    needsSignIn = true
                }
                is ShopResult.Unreachable -> {
                    dao.countAttempt(entry.seq)
                    stopped = result.message
                }
            }
            if (stopped != null) break
        }

        if (stopped == null) {
            when (val list = client.listUnits()) {
                is ShopResult.Ok -> list.value.forEach { unit ->
                    // Never overwrite an item with changes still waiting to go out.
                    dao.refreshFromServer(unit.toEntity())
                }
                is ShopResult.Refused -> stopped = list.message
                is ShopResult.NotSignedIn -> { stopped = list.message; needsSignIn = true }
                is ShopResult.Unreachable -> stopped = list.message
            }
        }

        SyncReport(
            sent = sent,
            refused = refused,
            stillWaiting = dao.outbox().count { it.state == OutboxEntry.STATE_PENDING },
            overlapped = overlapped,
            stoppedBecause = stopped,
            needsSignIn = needsSignIn
        )
    }

    // Ok(null) means the shop system accepted it and there's no new copy of the item to keep.
    private suspend fun send(entry: OutboxEntry): ShopResult<UnitSaved?> = when (entry.kind) {
        KIND_CREATE -> client.createUnit(newUnitJson.fromJson(entry.payloadJson)!!)
        KIND_UPDATE -> {
            val e = editsJson.fromJson(entry.payloadJson)!!
            // The version this phone last got from the shop system, so overlapping edits are spotted.
            val base = dao.unit(entry.itemId)?.version ?: 0
            client.updateUnit(
                entry.itemId,
                UnitChanges(
                    baseVersion = base, make = e.make, model = e.model, subModel = e.subModel, year = e.year,
                    color = e.color, unitType = e.unitType, vin = e.vin, vinFrameVerified = e.vinFrameVerified,
                    priceCents = e.priceCents, notes = e.notes
                )
            )
        }
        KIND_STATUS -> client.changeStatus(entry.itemId, statusJson.fromJson(entry.payloadJson)!!)
        KIND_TAG_PRINT -> when (val r = client.recordTag(entry.itemId, tagJson.fromJson(entry.payloadJson)!!)) {
            is ShopResult.Ok -> ShopResult.Ok(null)
            is ShopResult.Refused -> r
            is ShopResult.NotSignedIn -> r
            is ShopResult.Unreachable -> r
        }
        KIND_TAG_CONFIRM -> client.confirmTag(entry.itemId, tagConfirmJson.fromJson(entry.payloadJson)!!)
        else -> ShopResult.Refused("UNKNOWN_CHANGE", "This phone saved a kind of change this version of the app can't send. Update the app.", listOf(entry.kind))
    }

    companion object {
        const val KIND_CREATE = "create"
        const val KIND_UPDATE = "update"
        const val KIND_STATUS = "status"
        const val KIND_TAG_PRINT = "tag_print"
        const val KIND_TAG_CONFIRM = "tag_confirm"
    }
}

// Same names the shop system stores, so the phone's copy reads the same before and after sending.
private fun vinCheckName(vin: String?): String = when (vin?.let { Vin.check(it) }) {
    null -> "none"
    VinCheck.Ok -> "ok"
    VinCheck.NotApplicable -> "not_applicable"
    is VinCheck.Fail -> "fail"
}

fun UnitDto.toEntity() = ShopUnitEntity(
    id = id, vin = vin, vinCheck = vinCheck, year = year, make = make, model = model, subModel = subModel,
    color = color, unitType = unitType, priceCents = priceCents, status = status, notes = notes,
    version = version, updatedAt = updatedAt, updatedBy = updatedBy
)
