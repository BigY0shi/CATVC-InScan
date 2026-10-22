package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BarcodeMapping::class, InventoryListing::class, CustomDocument::class, DocumentZone::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun barcodeDao(): BarcodeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dealer_scan_database"
                )
                // No destructive fallback: a schema change without a migration must fail loudly,
                // never wipe the shop's records. Add a Migration for every version bump.
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
