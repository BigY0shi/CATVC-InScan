package com.example

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {

  @get:Rule
  val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

  @Test
  fun `v2 to v3 keeps everything already on the phone and matches a fresh install`() {
    helper.createDatabase("migration-test", 2).use { db ->
      db.execSQL(
        "INSERT INTO inventory_listings (id, barcode, make, model, year, color, vehicleType, vin, price, notes, imageUrl, scanTimestamp, status) " +
          "VALUES (7, 'B1', 'Honda', 'CRF450R', '2025', 'Red', 'Dirt Bike', 'VIN1', 8499.0, '', NULL, 1, 'In Stock')"
      )
      db.execSQL(
        "INSERT INTO barcode_mappings (barcode, make, model, year, color, vehicleType, vin, price, notes, imageUrl, lastUpdated) " +
          "VALUES ('B1', 'Honda', 'CRF450R', '2025', 'Red', 'Dirt Bike', 'VIN1', 8499.0, '', NULL, 1)"
      )
    }
    // validateDroppedTables = true: fails if the migrated schema differs from v3's exported schema.
    helper.runMigrationsAndValidate("migration-test", 3, true, AppDatabase.MIGRATION_2_3).use { db ->
      db.query("SELECT make, model, status FROM inventory_listings WHERE id = 7").use { c ->
        c.moveToFirst()
        assertEquals("Honda", c.getString(0))
        assertEquals("In Stock", c.getString(2))
      }
      db.query("SELECT COUNT(*) FROM barcode_mappings").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
      db.query("SELECT COUNT(*) FROM shop_units").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
      db.query("SELECT COUNT(*) FROM outbox").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
      db.query("SELECT COUNT(*) FROM legacy_uploads").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
    }
  }

  @Test
  fun `v3 to v4 keeps the phone's units and leaves when they went in stock empty until the shop system says`() {
    helper.createDatabase("migration-test-4", 3).use { db ->
      db.execSQL(
        "INSERT INTO shop_units (localId, id, vin, vinCheck, year, make, model, subModel, color, unitType, priceCents, status, notes, version, updatedAt, updatedBy) " +
          "VALUES (1, '01J0000000AAAAAAAA5NVA9HQC', NULL, 'none', 2026, 'Polaris', 'Sportsman 570', '', 'Grey', 'ATV', 949900, 'IN_STOCK', '', 3, '2026-11-01T17:00:00Z', '')"
      )
    }
    helper.runMigrationsAndValidate("migration-test-4", 4, true, AppDatabase.MIGRATION_3_4).use { db ->
      db.query("SELECT make, status, inStockSince FROM shop_units WHERE localId = 1").use { c ->
        c.moveToFirst()
        assertEquals("Polaris", c.getString(0))
        assertEquals("IN_STOCK", c.getString(1))
        assertEquals(true, c.isNull(2))
      }
    }
  }
}
