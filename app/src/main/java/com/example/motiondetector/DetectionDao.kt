package com.example.motiondetector

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DetectionDao {

    @Insert
    suspend fun insert(record: DetectionRecord): Long

    @Query("SELECT * FROM detections ORDER BY timestampMillis DESC")
    suspend fun getAll(): List<DetectionRecord>

    /**
     * Searches the saved detection history.
     * - [keyword] filters on the Korean label (사람 / 자동차 / 트럭 ...). Pass "" to match all.
     * - [typeFilter] is "PERSON", "VEHICLE", or null to match both.
     * - [fromMillis]/[toMillis] bound the timestamp range.
     */
    @Query(
        """
        SELECT * FROM detections
        WHERE (:keyword = '' OR label LIKE '%' || :keyword || '%')
          AND (:typeFilter IS NULL OR type = :typeFilter)
          AND timestampMillis BETWEEN :fromMillis AND :toMillis
        ORDER BY timestampMillis DESC
        """
    )
    suspend fun search(
        keyword: String,
        typeFilter: String?,
        fromMillis: Long,
        toMillis: Long
    ): List<DetectionRecord>

    @Query("SELECT COUNT(*) FROM detections")
    suspend fun count(): Int

    @Query("DELETE FROM detections")
    suspend fun clearAll()
}
