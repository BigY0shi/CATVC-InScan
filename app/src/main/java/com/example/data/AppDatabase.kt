package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BarcodeMapping::class, InventoryListing::class, CustomDocument::class, DocumentZone::class,
        ShopUnitEntity::class, OutboxEntry::class, LegacyUpload::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun barcodeDao(): BarcodeDao
    abstract fun shopDao(): ShopDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v3 adds this phone's copy of shop-system items, the queue of changes waiting to send, and
        // the record of which old items have been queued for upload.
        // The v2 tables are left exactly as they were, so nothing already on the phone is lost.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
            }
        }

        // Tests only: close and forget the open database so the next test starts from a clean one.
        @androidx.annotation.VisibleForTesting
        internal fun resetForTests() = synchronized(this) {
            INSTANCE?.close()
            INSTANCE = null
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dealer_scan_database"
                )
                // No destructive fallback: a schema change without a migration must fail loudly,
                // never wipe the shop's records. Add a Migration for every version bump.
                .addMigrations(MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Copied from the schema Room generated for version 3 (app/schemas/.../3.json), so the migrated
// database matches a fresh install exactly. AppDatabaseMigrationTest checks this.
internal val MIGRATION_2_3_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `shop_units` (`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `id` TEXT NOT NULL, `vin` TEXT, `vinCheck` TEXT NOT NULL, `year` INTEGER, `make` TEXT NOT NULL, `model` TEXT NOT NULL, `subModel` TEXT NOT NULL, `color` TEXT NOT NULL, `unitType` TEXT NOT NULL, `priceCents` INTEGER NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, `version` INTEGER NOT NULL, `updatedAt` TEXT NOT NULL, `updatedBy` TEXT NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_shop_units_id` ON `shop_units` (`id`)",
    "CREATE INDEX IF NOT EXISTS `index_shop_units_vin` ON `shop_units` (`vin`)",
    "CREATE TABLE IF NOT EXISTS `outbox` (`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` TEXT NOT NULL, `kind` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `state` TEXT NOT NULL, `problemCode` TEXT, `problemMessage` TEXT, `problemDetails` TEXT)",
    "CREATE INDEX IF NOT EXISTS `index_outbox_itemId` ON `outbox` (`itemId`)",
    "CREATE TABLE IF NOT EXISTS `legacy_uploads` (`legacyId` INTEGER NOT NULL, `itemId` TEXT NOT NULL, `queuedAt` INTEGER NOT NULL, PRIMARY KEY(`legacyId`))"
)
