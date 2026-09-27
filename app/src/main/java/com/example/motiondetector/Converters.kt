package com.example.motiondetector

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromDetectionType(type: DetectionType): String = type.name

    @TypeConverter
    fun toDetectionType(value: String): DetectionType = DetectionType.valueOf(value)
}
