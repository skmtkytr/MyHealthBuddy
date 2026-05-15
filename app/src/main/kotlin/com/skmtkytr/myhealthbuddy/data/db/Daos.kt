package com.skmtkytr.myhealthbuddy.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalSampleDao {
    @Upsert suspend fun upsertAll(rows: List<VitalSampleEntity>)
    @Query("DELETE FROM vital_samples WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)
    @Query("DELETE FROM vital_samples WHERE type = :type AND id IN (:ids)")
    suspend fun deleteByTypeAndIds(type: String, ids: List<String>)
    @Query("DELETE FROM vital_samples WHERE type = :type") suspend fun deleteAllOfType(type: String)
    @Query("DELETE FROM vital_samples WHERE type = :type AND id LIKE :prefix")
    suspend fun deleteByTypeAndPrefix(type: String, prefix: String)
    @Query("SELECT id FROM vital_samples WHERE type = :type") suspend fun idsForType(type: String): List<String>
    @Query("SELECT * FROM vital_samples") fun observeAll(): Flow<List<VitalSampleEntity>>
}

@Dao
interface IntervalMetricDao {
    @Upsert suspend fun upsertAll(rows: List<IntervalMetricEntity>)
    @Query("DELETE FROM interval_metrics WHERE type = :type AND id IN (:ids)")
    suspend fun deleteByTypeAndIds(type: String, ids: List<String>)
    @Query("DELETE FROM interval_metrics WHERE type = :type") suspend fun deleteAllOfType(type: String)
    @Query("SELECT id FROM interval_metrics WHERE type = :type") suspend fun idsForType(type: String): List<String>
    @Query("SELECT * FROM interval_metrics") fun observeAll(): Flow<List<IntervalMetricEntity>>
}

@Dao
interface BloodPressureDao {
    @Upsert suspend fun upsertAll(rows: List<BloodPressureEntity>)
    @Query("DELETE FROM blood_pressure WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)
    @Query("DELETE FROM blood_pressure") suspend fun deleteAll()
    @Query("SELECT id FROM blood_pressure") suspend fun allIds(): List<String>
    @Query("SELECT * FROM blood_pressure") fun observeAll(): Flow<List<BloodPressureEntity>>
}

@Dao
interface SleepDao {
    @Upsert suspend fun upsertSessions(rows: List<SleepSessionEntity>)
    @Upsert suspend fun upsertStages(rows: List<SleepStageEntity>)
    @Query("DELETE FROM sleep_sessions WHERE id IN (:ids)") suspend fun deleteSessionsByIds(ids: List<String>)
    @Query("DELETE FROM sleep_stages WHERE sessionId IN (:sessionIds)")
    suspend fun deleteStagesBySessionIds(sessionIds: List<String>)
    @Query("DELETE FROM sleep_sessions") suspend fun deleteAllSessions()
    @Query("DELETE FROM sleep_stages") suspend fun deleteAllStages()
    @Query("SELECT id FROM sleep_sessions") suspend fun allSessionIds(): List<String>
    @Query("SELECT * FROM sleep_sessions") fun observeSessions(): Flow<List<SleepSessionEntity>>
    @Query("SELECT * FROM sleep_stages") fun observeStages(): Flow<List<SleepStageEntity>>
}

@Dao
interface ExerciseDao {
    @Upsert suspend fun upsertAll(rows: List<ExerciseSessionEntity>)
    @Query("DELETE FROM exercise_sessions WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)
    @Query("DELETE FROM exercise_sessions") suspend fun deleteAll()
    @Query("SELECT id FROM exercise_sessions") suspend fun allIds(): List<String>
    @Query("SELECT * FROM exercise_sessions") fun observeAll(): Flow<List<ExerciseSessionEntity>>
}

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)
    @Query("SELECT * FROM sync_state WHERE dataType = :dataType") suspend fun get(dataType: String): SyncStateEntity?
}
