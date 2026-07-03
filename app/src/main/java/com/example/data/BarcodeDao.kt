package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BarcodeDao {
    @Query("SELECT * FROM barcode_mappings ORDER BY lastUpdated DESC")
    fun getAllMappings(): Flow<List<BarcodeMapping>>

    @Query("SELECT * FROM barcode_mappings WHERE barcode = :barcode LIMIT 1")
    suspend fun getMappingByBarcode(barcode: String): BarcodeMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: BarcodeMapping)

    @Query("DELETE FROM barcode_mappings WHERE barcode = :barcode")
    suspend fun deleteMapping(barcode: String)

    @Query("SELECT * FROM inventory_listings ORDER BY scanTimestamp DESC")
    fun getAllListings(): Flow<List<InventoryListing>>

    @Query("SELECT * FROM inventory_listings WHERE id = :id LIMIT 1")
    suspend fun getListingById(id: Int): InventoryListing?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListing(listing: InventoryListing)

    @Query("DELETE FROM inventory_listings WHERE id = :id")
    suspend fun deleteListing(id: Int)

    @Query("UPDATE inventory_listings SET status = :status WHERE id = :id")
    suspend fun updateListingStatus(id: Int, status: String)

    // Custom Document Mapping
    @Query("SELECT * FROM custom_documents ORDER BY uploadTimestamp DESC")
    fun getAllCustomDocuments(): Flow<List<CustomDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomDocument(doc: CustomDocument): Long

    @Query("DELETE FROM custom_documents WHERE id = :id")
    suspend fun deleteCustomDocument(id: Int)

    @Query("SELECT * FROM document_zones WHERE documentId = :docId")
    fun getZonesForDocument(docId: Int): Flow<List<DocumentZone>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocumentZone(zone: DocumentZone)

    @Query("DELETE FROM document_zones WHERE id = :id")
    suspend fun deleteDocumentZone(id: Int)

    @Query("DELETE FROM document_zones WHERE documentId = :docId")
    suspend fun deleteZonesForDocument(docId: Int)
}
