package com.example.data

import kotlinx.coroutines.flow.Flow

class BarcodeRepository(private val barcodeDao: BarcodeDao) {
    val allMappings: Flow<List<BarcodeMapping>> = barcodeDao.getAllMappings()
    val allListings: Flow<List<InventoryListing>> = barcodeDao.getAllListings()

    suspend fun getMappingByBarcode(barcode: String): BarcodeMapping? {
        return barcodeDao.getMappingByBarcode(barcode)
    }

    suspend fun insertMapping(mapping: BarcodeMapping) {
        barcodeDao.insertMapping(mapping)
    }

    suspend fun deleteMapping(barcode: String) {
        barcodeDao.deleteMapping(barcode)
    }

    suspend fun getListingById(id: Int): InventoryListing? {
        return barcodeDao.getListingById(id)
    }

    suspend fun insertListing(listing: InventoryListing) {
        barcodeDao.insertListing(listing)
    }

    suspend fun deleteListing(id: Int) {
        barcodeDao.deleteListing(id)
    }

    suspend fun updateListingStatus(id: Int, status: String) {
        barcodeDao.updateListingStatus(id, status)
    }

    val allCustomDocuments: Flow<List<CustomDocument>> = barcodeDao.getAllCustomDocuments()

    suspend fun insertCustomDocument(doc: CustomDocument): Long {
        return barcodeDao.insertCustomDocument(doc)
    }

    suspend fun deleteCustomDocument(id: Int) {
        barcodeDao.deleteCustomDocument(id)
    }

    fun getZonesForDocument(docId: Int): Flow<List<DocumentZone>> {
        return barcodeDao.getZonesForDocument(docId)
    }

    suspend fun insertDocumentZone(zone: DocumentZone) {
        barcodeDao.insertDocumentZone(zone)
    }

    suspend fun deleteDocumentZone(id: Int) {
        barcodeDao.deleteDocumentZone(id)
    }

    suspend fun deleteZonesForDocument(docId: Int) {
        barcodeDao.deleteZonesForDocument(docId)
    }
}
