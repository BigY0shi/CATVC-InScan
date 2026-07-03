package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "barcode_mappings")
data class BarcodeMapping(
    @PrimaryKey val barcode: String,
    val make: String,
    val model: String,
    val year: String,
    val color: String,
    val vehicleType: String, // "ATV", "Side-by-Side (UTV)", "Dirt Bike", "Snowmobile", "Jet Ski", "Other"
    val vin: String,
    val price: Double,
    val notes: String,
    val imageUrl: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "inventory_listings")
data class InventoryListing(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val barcode: String,
    val make: String,
    val model: String,
    val year: String,
    val color: String,
    val vehicleType: String,
    val vin: String,
    val price: Double,
    val notes: String,
    val imageUrl: String? = null,
    val scanTimestamp: Long = System.currentTimeMillis(),
    val status: String = "Incoming" // "Incoming", "In Stock", "Sold"
)

@Entity(tableName = "custom_documents")
data class CustomDocument(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val imageUrl: String,
    val uploadTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "document_zones")
data class DocumentZone(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val documentId: Int,
    val fieldName: String, // "make", "model", "year", "color", "vin", "price", "buyerName", "saleDate", etc.
    val leftPercent: Float,
    val topPercent: Float,
    val rightPercent: Float,
    val bottomPercent: Float
)
