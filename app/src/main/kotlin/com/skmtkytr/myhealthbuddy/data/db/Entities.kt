package com.skmtkytr.myhealthbuddy.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.Instant

enum class VitalType {
    HeartRate,
    RestingHeartRate,
    HeartRateVariability,
    OxygenSaturation,
    RespiratoryRate,
    BodyTemperature,
    BloodGlucose,
    Weight,
    Height,
    BodyFat,
    LeanBodyMass,
}

enum class IntervalType {
    Steps,
    Distance,
    FloorsClimbed,
    ActiveCalories,
    TotalCalories,
    Hydration,
}

enum class DataType {
    HeartRate, RestingHeartRate, HeartRateVariability,
    OxygenSaturation, RespiratoryRate, BodyTemperature,
    BloodGlucose, BloodPressure,
    Weight, Height, BodyFat, LeanBodyMass,
    Steps, Distance, FloorsClimbed, ActiveCalories, TotalCalories, Hydration,
    Sleep, Exercise,
}

@Entity(
    tableName = "vital_samples",
    indices = [Index("type"), Index("time")],
)
data class VitalSampleEntity(
    @PrimaryKey val id: String,
    val type: String,
    val time: Instant,
    val value: Double,
    val sourceApp: String,
)

@Entity(
    tableName = "interval_metrics",
    indices = [Index("type"), Index("startTime")],
)
data class IntervalMetricEntity(
    @PrimaryKey val id: String,
    val type: String,
    val startTime: Instant,
    val endTime: Instant,
    val value: Double,
    val sourceApp: String,
)

@Entity(tableName = "blood_pressure")
data class BloodPressureEntity(
    @PrimaryKey val id: String,
    val time: Instant,
    val systolic: Double,
    val diastolic: Double,
    val sourceApp: String,
)

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val title: String?,
    val sourceApp: String,
)

@Entity(
    tableName = "sleep_stages",
    indices = [Index("sessionId")],
)
data class SleepStageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val startTime: Instant,
    val endTime: Instant,
    val stage: String,
    val sourceApp: String,
)

@Entity(tableName = "exercise_sessions")
data class ExerciseSessionEntity(
    @PrimaryKey val id: String,
    val startTime: Instant,
    val endTime: Instant,
    val exerciseType: String,
    val title: String?,
    val sourceApp: String,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val dataType: String,
    val changesToken: String?,
    val lastSyncedAt: Instant?,
    val backfillCompleted: Boolean,
)

class Converters {
    @TypeConverter fun fromInstant(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun toInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)
}
