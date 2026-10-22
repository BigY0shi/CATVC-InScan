package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// This phone's copy of an item in the shop system. The server is the source of truth; this copy
// lets the phone show and scan inventory with no signal, and holds items not yet sent.
@Entity(tableName = "shop_units", indices = [Index(value = ["id"], unique = true), Index(value = ["vin"])])
data class ShopUnitEntity(
    // Small number used by the existing screens to refer to an item.
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val id: String,
    val vin: String?,
    val vinCheck: String,
    val year: Int?,
    val make: String,
    val model: String,
    val subModel: String,
    val color: String,
    val unitType: String,
    val priceCents: Long,
    val status: String,
    val notes: String,
    // The server's version this copy is based on; 0 for an item the server hasn't seen yet.
    val version: Int,
    val updatedAt: String,
    val updatedBy: String
)

// A change made on this phone, waiting to be sent. Sent oldest first. A change the shop system
// refuses stays here as "needs_attention" with its reason; it is never dropped silently.
@Entity(tableName = "outbox", indices = [Index(value = ["itemId"])])
data class OutboxEntry(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val itemId: String,
    // create | update | status
    val kind: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    // pending | needs_attention
    val state: String = STATE_PENDING,
    // The shop system's reason code (e.g. VIN_NOT_CONFIRMED), so the app can offer the right fix.
    val problemCode: String? = null,
    val problemMessage: String? = null,
    val problemDetails: String? = null
) {
    companion object {
        const val STATE_PENDING = "pending"
        const val STATE_NEEDS_ATTENTION = "needs_attention"
    }
}

// Which items from before the shop system (the old inventory_listings table) this phone has
// already queued for upload, so the upload screen never sends one twice. Old rows are never deleted.
@Entity(tableName = "legacy_uploads")
data class LegacyUpload(
    @PrimaryKey val legacyId: Int,
    val itemId: String,
    val queuedAt: Long
)

@Dao
interface ShopDao {
    @Query("SELECT * FROM shop_units ORDER BY id DESC")
    fun units(): Flow<List<ShopUnitEntity>>

    @Query("SELECT * FROM shop_units ORDER BY id DESC")
    suspend fun allUnitsOnce(): List<ShopUnitEntity>

    @Query("SELECT * FROM shop_units WHERE id = :id LIMIT 1")
    suspend fun unit(id: String): ShopUnitEntity?

    @Query("SELECT * FROM shop_units WHERE localId = :localId LIMIT 1")
    suspend fun unitByLocalId(localId: Int): ShopUnitEntity?

    @Query("SELECT * FROM shop_units WHERE vin = :vin LIMIT 1")
    suspend fun unitByVin(vin: String): ShopUnitEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUnit(unit: ShopUnitEntity): Long

    @Query(
        """UPDATE shop_units SET vin = :vin, vinCheck = :vinCheck, year = :year, make = :make, model = :model,
           subModel = :subModel, color = :color, unitType = :unitType, priceCents = :priceCents, status = :status,
           notes = :notes, version = :version, updatedAt = :updatedAt, updatedBy = :updatedBy WHERE id = :id"""
    )
    suspend fun updateUnit(
        id: String, vin: String?, vinCheck: String, year: Int?, make: String, model: String, subModel: String,
        color: String, unitType: String, priceCents: Long, status: String, notes: String, version: Int,
        updatedAt: String, updatedBy: String
    ): Int

    // Keeps the localId the screens use when a fresher copy arrives.
    @Transaction
    suspend fun upsertUnit(u: ShopUnitEntity) {
        val changed = updateUnit(
            u.id, u.vin, u.vinCheck, u.year, u.make, u.model, u.subModel, u.color, u.unitType,
            u.priceCents, u.status, u.notes, u.version, u.updatedAt, u.updatedBy
        )
        if (changed == 0) insertUnit(u.copy(localId = 0))
    }

    @Query("UPDATE shop_units SET version = :version WHERE id = :id")
    suspend fun setVersion(id: String, version: Int)

    @Query("DELETE FROM shop_units WHERE id = :id")
    suspend fun deleteUnit(id: String)

    // The shop system accepted a change: drop it from the queue and take the server's copy, in one
    // step. With more changes still queued for the item, keep the phone's newer fields and take only
    // the server's version number; the last of them brings the full copy.
    @Transaction
    suspend fun recordSent(seq: Long, saved: ShopUnitEntity) {
        removeFromOutbox(seq)
        if (pendingFor(saved.id) == 0) upsertUnit(saved) else setVersion(saved.id, saved.version)
    }

    @Insert
    suspend fun enqueue(entry: OutboxEntry): Long

    @Query("SELECT * FROM outbox ORDER BY seq ASC")
    suspend fun outbox(): List<OutboxEntry>

    @Query("SELECT * FROM outbox ORDER BY seq ASC")
    fun outboxFlow(): Flow<List<OutboxEntry>>

    @Query("SELECT COUNT(*) FROM outbox WHERE itemId = :itemId")
    suspend fun pendingFor(itemId: String): Int

    @Query("DELETE FROM outbox WHERE seq = :seq")
    suspend fun removeFromOutbox(seq: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE seq = :seq")
    suspend fun countAttempt(seq: Long)

    @Query("UPDATE outbox SET state = 'needs_attention', problemCode = :code, problemMessage = :message, problemDetails = :details WHERE seq = :seq")
    suspend fun markNeedsAttention(seq: Long, code: String, message: String, details: String?)

    // The employee fixed the item and wants to try again.
    @Query("UPDATE outbox SET state = 'pending', problemCode = NULL, problemMessage = NULL, problemDetails = NULL, payloadJson = :payloadJson WHERE seq = :seq")
    suspend fun retry(seq: Long, payloadJson: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun recordLegacyUpload(upload: LegacyUpload)

    @Query("SELECT * FROM legacy_uploads WHERE legacyId = :legacyId")
    suspend fun legacyUploadFor(legacyId: Int): LegacyUpload?

    @Query("SELECT * FROM legacy_uploads")
    fun legacyUploads(): Flow<List<LegacyUpload>>
}
