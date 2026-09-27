package com.example.motiondetector

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class DetectionDao_Impl(
  __db: RoomDatabase,
) : DetectionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfDetectionRecord: EntityInsertAdapter<DetectionRecord>

  private val __converters: Converters = Converters()
  init {
    this.__db = __db
    this.__insertAdapterOfDetectionRecord = object : EntityInsertAdapter<DetectionRecord>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `detections` (`type`,`label`,`score`,`timestampMillis`,`id`) VALUES (?,?,?,?,nullif(?, 0))"

      protected override fun bind(statement: SQLiteStatement, entity: DetectionRecord) {
        val _tmp: String = __converters.fromDetectionType(entity.type)
        statement.bindText(1, _tmp)
        statement.bindText(2, entity.label)
        statement.bindDouble(3, entity.score.toDouble())
        statement.bindLong(4, entity.timestampMillis)
        statement.bindLong(5, entity.id)
      }
    }
  }

  public override suspend fun insert(record: DetectionRecord): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfDetectionRecord.insertAndReturnId(_connection, record)
    _result
  }

  public override suspend fun getAll(): List<DetectionRecord> {
    val _sql: String = "SELECT * FROM detections ORDER BY timestampMillis DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfScore: Int = getColumnIndexOrThrow(_stmt, "score")
        val _columnIndexOfTimestampMillis: Int = getColumnIndexOrThrow(_stmt, "timestampMillis")
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _result: MutableList<DetectionRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: DetectionRecord
          val _tmpType: DetectionType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfType)
          _tmpType = __converters.toDetectionType(_tmp)
          val _tmpLabel: String
          _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          val _tmpScore: Float
          _tmpScore = _stmt.getDouble(_columnIndexOfScore).toFloat()
          val _tmpTimestampMillis: Long
          _tmpTimestampMillis = _stmt.getLong(_columnIndexOfTimestampMillis)
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          _item = DetectionRecord(_tmpType,_tmpLabel,_tmpScore,_tmpTimestampMillis,_tmpId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun search(
    keyword: String,
    typeFilter: String?,
    fromMillis: Long,
    toMillis: Long,
  ): List<DetectionRecord> {
    val _sql: String = """
        |
        |        SELECT * FROM detections
        |        WHERE (? = '' OR label LIKE '%' || ? || '%')
        |          AND (? IS NULL OR type = ?)
        |          AND timestampMillis BETWEEN ? AND ?
        |        ORDER BY timestampMillis DESC
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, keyword)
        _argIndex = 2
        _stmt.bindText(_argIndex, keyword)
        _argIndex = 3
        if (typeFilter == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, typeFilter)
        }
        _argIndex = 4
        if (typeFilter == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, typeFilter)
        }
        _argIndex = 5
        _stmt.bindLong(_argIndex, fromMillis)
        _argIndex = 6
        _stmt.bindLong(_argIndex, toMillis)
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfLabel: Int = getColumnIndexOrThrow(_stmt, "label")
        val _columnIndexOfScore: Int = getColumnIndexOrThrow(_stmt, "score")
        val _columnIndexOfTimestampMillis: Int = getColumnIndexOrThrow(_stmt, "timestampMillis")
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _result: MutableList<DetectionRecord> = mutableListOf()
        while (_stmt.step()) {
          val _item: DetectionRecord
          val _tmpType: DetectionType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfType)
          _tmpType = __converters.toDetectionType(_tmp)
          val _tmpLabel: String
          _tmpLabel = _stmt.getText(_columnIndexOfLabel)
          val _tmpScore: Float
          _tmpScore = _stmt.getDouble(_columnIndexOfScore).toFloat()
          val _tmpTimestampMillis: Long
          _tmpTimestampMillis = _stmt.getLong(_columnIndexOfTimestampMillis)
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          _item = DetectionRecord(_tmpType,_tmpLabel,_tmpScore,_tmpTimestampMillis,_tmpId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun count(): Int {
    val _sql: String = "SELECT COUNT(*) FROM detections"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearAll() {
    val _sql: String = "DELETE FROM detections"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
