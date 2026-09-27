package com.example.motiondetector

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _detectionDao: Lazy<DetectionDao> = lazy {
    DetectionDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "02390b09930d80ffd12052b3a15a52ee", "63b23f3e032a951d5563709779febf61") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `detections` (`type` TEXT NOT NULL, `label` TEXT NOT NULL, `score` REAL NOT NULL, `timestampMillis` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '02390b09930d80ffd12052b3a15a52ee')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `detections`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsDetections: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDetections.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDetections.put("label", TableInfo.Column("label", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDetections.put("score", TableInfo.Column("score", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDetections.put("timestampMillis", TableInfo.Column("timestampMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDetections.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDetections: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDetections: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDetections: TableInfo = TableInfo("detections", _columnsDetections, _foreignKeysDetections, _indicesDetections)
        val _existingDetections: TableInfo = read(connection, "detections")
        if (!_infoDetections.equals(_existingDetections)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |detections(com.example.motiondetector.DetectionRecord).
              | Expected:
              |""".trimMargin() + _infoDetections + """
              |
              | Found:
              |""".trimMargin() + _existingDetections)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "detections")
  }

  public override fun clearAllTables() {
    super.performClear(false, "detections")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(DetectionDao::class, DetectionDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun detectionDao(): DetectionDao = _detectionDao.value
}
