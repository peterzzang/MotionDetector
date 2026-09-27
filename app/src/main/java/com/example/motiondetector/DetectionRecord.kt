package com.example.motiondetector

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single logged detection event (a person or vehicle entering the frame).
 * Also doubles as the Room entity persisted to the local database so past
 * detections can be searched later from [HistoryActivity].
 */
@Entity(tableName = "detections")
data class DetectionRecord(
    val type: DetectionType,
    val label: String,
    val score: Float,
    val timestampMillis: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true) val id: Long = 0
)

enum class DetectionType {
    PERSON,
    VEHICLE
}
