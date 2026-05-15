package com.skmtkytr.myhealthbuddy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        VitalSampleEntity::class,
        IntervalMetricEntity::class,
        BloodPressureEntity::class,
        SleepSessionEntity::class,
        SleepStageEntity::class,
        ExerciseSessionEntity::class,
        SyncStateEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vitalSampleDao(): VitalSampleDao
    abstract fun intervalMetricDao(): IntervalMetricDao
    abstract fun bloodPressureDao(): BloodPressureDao
    abstract fun sleepDao(): SleepDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "myhealthbuddy.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
