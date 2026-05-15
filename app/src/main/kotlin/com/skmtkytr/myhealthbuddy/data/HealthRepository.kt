package com.skmtkytr.myhealthbuddy.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.room.withTransaction
import com.skmtkytr.myhealthbuddy.data.db.AppDatabase
import com.skmtkytr.myhealthbuddy.data.db.BloodPressureEntity
import com.skmtkytr.myhealthbuddy.data.db.DataType
import com.skmtkytr.myhealthbuddy.data.db.ExerciseSessionEntity
import com.skmtkytr.myhealthbuddy.data.db.IntervalMetricEntity
import com.skmtkytr.myhealthbuddy.data.db.IntervalType
import com.skmtkytr.myhealthbuddy.data.db.SleepSessionEntity
import com.skmtkytr.myhealthbuddy.data.db.SleepStageEntity
import com.skmtkytr.myhealthbuddy.data.db.SyncStateEntity
import com.skmtkytr.myhealthbuddy.data.db.VitalSampleEntity
import com.skmtkytr.myhealthbuddy.data.db.VitalType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import kotlin.reflect.KClass

data class SyncProgress(
    val type: DataType,
    val phase: Phase,
    val processed: Int,
) {
    enum class Phase { BulkRead, Changes, Done }
}

class HealthRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val vitalDao = db.vitalSampleDao()
    private val intervalDao = db.intervalMetricDao()
    private val bpDao = db.bloodPressureDao()
    private val sleepDao = db.sleepDao()
    private val exerciseDao = db.exerciseDao()
    private val syncStateDao = db.syncStateDao()

    val permissions: Set<String> = setOf(
        // Activity / intervals
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        // Vitals
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        // Body
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        // Sleep & exercise
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    fun availability(): Int = HealthConnectClient.getSdkStatus(appContext)
    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(appContext)

    suspend fun hasAllPermissions(): Boolean =
        client().permissionController.getGrantedPermissions().containsAll(permissions)

    fun observeSummary(): Flow<DbSummary> = combine(
        listOf(
            vitalDao.observeAll(),
            intervalDao.observeAll(),
            bpDao.observeAll(),
            sleepDao.observeSessions(),
            sleepDao.observeStages(),
            exerciseDao.observeAll(),
        ),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        DbSummary(
            vitals = values[0] as List<VitalSampleEntity>,
            intervals = values[1] as List<IntervalMetricEntity>,
            bloodPressure = values[2] as List<BloodPressureEntity>,
            sleepSessions = values[3] as List<SleepSessionEntity>,
            sleepStages = values[4] as List<SleepStageEntity>,
            exercises = values[5] as List<ExerciseSessionEntity>,
        )
    }

    // ---------- public entry ----------

    suspend fun sync(onProgress: (SyncProgress) -> Unit = {}) {
        // Intervals
        syncIntervalType(DataType.Steps, IntervalType.Steps, StepsRecord::class,
            valueOf = { it.count.toDouble() }, onProgress)
        syncIntervalType(DataType.Distance, IntervalType.Distance, DistanceRecord::class,
            valueOf = { it.distance.inMeters }, onProgress)
        syncIntervalType(DataType.FloorsClimbed, IntervalType.FloorsClimbed, FloorsClimbedRecord::class,
            valueOf = { it.floors }, onProgress)
        syncIntervalType(DataType.ActiveCalories, IntervalType.ActiveCalories, ActiveCaloriesBurnedRecord::class,
            valueOf = { it.energy.inKilocalories }, onProgress)
        syncIntervalType(DataType.TotalCalories, IntervalType.TotalCalories, TotalCaloriesBurnedRecord::class,
            valueOf = { it.energy.inKilocalories }, onProgress)
        syncIntervalType(DataType.Hydration, IntervalType.Hydration, HydrationRecord::class,
            valueOf = { it.volume.inLiters }, onProgress)

        // Vitals (single-time, single-value)
        syncVitalTypeSingle(DataType.RestingHeartRate, VitalType.RestingHeartRate, RestingHeartRateRecord::class,
            timeOf = { it.time }, valueOf = { it.beatsPerMinute.toDouble() }, onProgress)
        syncVitalTypeSingle(DataType.HeartRateVariability, VitalType.HeartRateVariability,
            HeartRateVariabilityRmssdRecord::class,
            timeOf = { it.time }, valueOf = { it.heartRateVariabilityMillis }, onProgress)
        syncVitalTypeSingle(DataType.OxygenSaturation, VitalType.OxygenSaturation, OxygenSaturationRecord::class,
            timeOf = { it.time }, valueOf = { it.percentage.value }, onProgress)
        syncVitalTypeSingle(DataType.RespiratoryRate, VitalType.RespiratoryRate, RespiratoryRateRecord::class,
            timeOf = { it.time }, valueOf = { it.rate }, onProgress)
        syncVitalTypeSingle(DataType.BodyTemperature, VitalType.BodyTemperature, BodyTemperatureRecord::class,
            timeOf = { it.time }, valueOf = { it.temperature.inCelsius }, onProgress)
        syncVitalTypeSingle(DataType.BloodGlucose, VitalType.BloodGlucose, BloodGlucoseRecord::class,
            timeOf = { it.time }, valueOf = { it.level.inMilligramsPerDeciliter }, onProgress)
        syncVitalTypeSingle(DataType.Weight, VitalType.Weight, WeightRecord::class,
            timeOf = { it.time }, valueOf = { it.weight.inKilograms }, onProgress)
        syncVitalTypeSingle(DataType.Height, VitalType.Height, HeightRecord::class,
            timeOf = { it.time }, valueOf = { it.height.inMeters }, onProgress)
        syncVitalTypeSingle(DataType.BodyFat, VitalType.BodyFat, BodyFatRecord::class,
            timeOf = { it.time }, valueOf = { it.percentage.value }, onProgress)
        syncVitalTypeSingle(DataType.LeanBodyMass, VitalType.LeanBodyMass, LeanBodyMassRecord::class,
            timeOf = { it.time }, valueOf = { it.mass.inKilograms }, onProgress)

        // Heart rate (sample-bearing)
        syncHeartRate(onProgress)

        // Blood pressure (2-value)
        syncBloodPressure(onProgress)

        // Sleep (with stages)
        syncSleep(onProgress)

        // Exercise sessions
        syncExercise(onProgress)
    }

    // ---------- generic sync skeleton ----------

    private suspend fun <T : Record> genericSync(
        dataType: DataType,
        recordClass: KClass<T>,
        upsert: suspend (List<T>) -> Unit,
        deleteByIds: suspend (List<String>) -> Unit,
        clearAll: suspend () -> Unit,
        onProgress: (SyncProgress) -> Unit,
    ) {
        val c = client()
        val state = syncStateDao.get(dataType.name)
        if (state == null || !state.backfillCompleted || state.changesToken == null) {
            bulkBackfill(dataType, recordClass, upsert, onProgress)
            val token = c.getChangesToken(ChangesTokenRequest(setOf(recordClass)))
            syncStateDao.upsert(SyncStateEntity(dataType.name, token, Instant.now(), true))
        } else {
            applyChanges(dataType, recordClass, state.changesToken, upsert, deleteByIds, clearAll, onProgress)
        }
        onProgress(SyncProgress(dataType, SyncProgress.Phase.Done, 0))
    }

    private suspend fun <T : Record> bulkBackfill(
        dataType: DataType,
        recordClass: KClass<T>,
        upsert: suspend (List<T>) -> Unit,
        onProgress: (SyncProgress) -> Unit,
    ) {
        val c = client()
        val range = TimeRangeFilter.after(Instant.EPOCH)
        var token: String? = null
        var processed = 0
        do {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = recordClass,
                    timeRangeFilter = range,
                    pageSize = PAGE_SIZE,
                    pageToken = token,
                ),
            )
            if (resp.records.isNotEmpty()) upsert(resp.records)
            processed += resp.records.size
            onProgress(SyncProgress(dataType, SyncProgress.Phase.BulkRead, processed))
            token = resp.pageToken
        } while (token != null)
        Log.d(TAG, "bulkBackfill $dataType processed=$processed")
    }

    private suspend fun <T : Record> applyChanges(
        dataType: DataType,
        recordClass: KClass<T>,
        startToken: String,
        upsert: suspend (List<T>) -> Unit,
        deleteByIds: suspend (List<String>) -> Unit,
        clearAll: suspend () -> Unit,
        onProgress: (SyncProgress) -> Unit,
    ) {
        val c = client()
        var token = startToken
        var processed = 0
        while (true) {
            val resp = c.getChanges(token)
            if (resp.changesTokenExpired) {
                Log.w(TAG, "changes token expired for $dataType — re-backfilling")
                clearAll()
                syncStateDao.upsert(SyncStateEntity(dataType.name, null, null, false))
                bulkBackfill(dataType, recordClass, upsert, onProgress)
                val fresh = c.getChangesToken(ChangesTokenRequest(setOf(recordClass)))
                syncStateDao.upsert(SyncStateEntity(dataType.name, fresh, Instant.now(), true))
                return
            }
            @Suppress("UNCHECKED_CAST")
            val upserts = resp.changes.filterIsInstance<UpsertionChange>()
                .mapNotNull { it.record as? T }
            val deletes = resp.changes.filterIsInstance<DeletionChange>().map { it.recordId }
            if (upserts.isNotEmpty()) upsert(upserts)
            if (deletes.isNotEmpty()) deleteByIds(deletes)
            processed += resp.changes.size
            onProgress(SyncProgress(dataType, SyncProgress.Phase.Changes, processed))
            token = resp.nextChangesToken
            if (!resp.hasMore) break
        }
        syncStateDao.upsert(SyncStateEntity(dataType.name, token, Instant.now(), true))
    }

    // ---------- per-type adapters ----------

    private suspend fun <T : Record> syncIntervalType(
        dataType: DataType,
        intervalType: IntervalType,
        recordClass: KClass<T>,
        valueOf: (T) -> Double,
        onProgress: (SyncProgress) -> Unit,
    ) {
        val typeName = intervalType.name
        genericSync(
            dataType = dataType,
            recordClass = recordClass,
            upsert = { records ->
                intervalDao.upsertAll(records.map { r ->
                    val (start, end) = startEndOf(r)
                    IntervalMetricEntity(
                        id = r.metadata.id,
                        type = typeName,
                        startTime = start,
                        endTime = end,
                        value = valueOf(r),
                        sourceApp = r.metadata.dataOrigin.packageName,
                    )
                })
            },
            deleteByIds = { ids -> intervalDao.deleteByTypeAndIds(typeName, ids) },
            clearAll = { intervalDao.deleteAllOfType(typeName) },
            onProgress = onProgress,
        )
    }

    private suspend fun <T : Record> syncVitalTypeSingle(
        dataType: DataType,
        vitalType: VitalType,
        recordClass: KClass<T>,
        timeOf: (T) -> Instant,
        valueOf: (T) -> Double,
        onProgress: (SyncProgress) -> Unit,
    ) {
        val typeName = vitalType.name
        genericSync(
            dataType = dataType,
            recordClass = recordClass,
            upsert = { records ->
                vitalDao.upsertAll(records.map { r ->
                    VitalSampleEntity(
                        id = r.metadata.id,
                        type = typeName,
                        time = timeOf(r),
                        value = valueOf(r),
                        sourceApp = r.metadata.dataOrigin.packageName,
                    )
                })
            },
            deleteByIds = { ids -> vitalDao.deleteByTypeAndIds(typeName, ids) },
            clearAll = { vitalDao.deleteAllOfType(typeName) },
            onProgress = onProgress,
        )
    }

    private suspend fun syncHeartRate(onProgress: (SyncProgress) -> Unit) {
        val typeName = VitalType.HeartRate.name
        genericSync(
            dataType = DataType.HeartRate,
            recordClass = HeartRateRecord::class,
            upsert = { records ->
                db.withTransaction {
                    for (r in records) {
                        vitalDao.deleteByTypeAndPrefix(typeName, "${r.metadata.id}:%")
                    }
                    val rows = records.flatMap { r ->
                        r.samples.map { s ->
                            VitalSampleEntity(
                                id = "${r.metadata.id}:${s.time.toEpochMilli()}",
                                type = typeName,
                                time = s.time,
                                value = s.beatsPerMinute.toDouble(),
                                sourceApp = r.metadata.dataOrigin.packageName,
                            )
                        }
                    }
                    if (rows.isNotEmpty()) vitalDao.upsertAll(rows)
                }
            },
            deleteByIds = { recordIds ->
                for (rid in recordIds) vitalDao.deleteByTypeAndPrefix(typeName, "$rid:%")
            },
            clearAll = { vitalDao.deleteAllOfType(typeName) },
            onProgress = onProgress,
        )
    }

    private suspend fun syncBloodPressure(onProgress: (SyncProgress) -> Unit) {
        genericSync(
            dataType = DataType.BloodPressure,
            recordClass = BloodPressureRecord::class,
            upsert = { records ->
                bpDao.upsertAll(records.map { r ->
                    BloodPressureEntity(
                        id = r.metadata.id,
                        time = r.time,
                        systolic = r.systolic.inMillimetersOfMercury,
                        diastolic = r.diastolic.inMillimetersOfMercury,
                        sourceApp = r.metadata.dataOrigin.packageName,
                    )
                })
            },
            deleteByIds = { ids -> bpDao.deleteByIds(ids) },
            clearAll = { bpDao.deleteAll() },
            onProgress = onProgress,
        )
    }

    private suspend fun syncSleep(onProgress: (SyncProgress) -> Unit) {
        genericSync(
            dataType = DataType.Sleep,
            recordClass = SleepSessionRecord::class,
            upsert = { records ->
                db.withTransaction {
                    val sessionIds = records.map { it.metadata.id }
                    sleepDao.deleteStagesBySessionIds(sessionIds)
                    sleepDao.upsertSessions(records.map { r ->
                        SleepSessionEntity(
                            id = r.metadata.id,
                            startTime = r.startTime,
                            endTime = r.endTime,
                            title = r.title,
                            sourceApp = r.metadata.dataOrigin.packageName,
                        )
                    })
                    val stages = records.flatMap { r ->
                        r.stages.map { s ->
                            SleepStageEntity(
                                id = "${r.metadata.id}:${s.startTime.toEpochMilli()}",
                                sessionId = r.metadata.id,
                                startTime = s.startTime,
                                endTime = s.endTime,
                                stage = sleepStageName(s.stage),
                                sourceApp = r.metadata.dataOrigin.packageName,
                            )
                        }
                    }
                    if (stages.isNotEmpty()) sleepDao.upsertStages(stages)
                }
            },
            deleteByIds = { ids ->
                db.withTransaction {
                    sleepDao.deleteStagesBySessionIds(ids)
                    sleepDao.deleteSessionsByIds(ids)
                }
            },
            clearAll = {
                db.withTransaction {
                    sleepDao.deleteAllStages()
                    sleepDao.deleteAllSessions()
                }
            },
            onProgress = onProgress,
        )
    }

    private suspend fun syncExercise(onProgress: (SyncProgress) -> Unit) {
        genericSync(
            dataType = DataType.Exercise,
            recordClass = ExerciseSessionRecord::class,
            upsert = { records ->
                exerciseDao.upsertAll(records.map { r ->
                    ExerciseSessionEntity(
                        id = r.metadata.id,
                        startTime = r.startTime,
                        endTime = r.endTime,
                        exerciseType = exerciseTypeName(r.exerciseType),
                        title = r.title,
                        sourceApp = r.metadata.dataOrigin.packageName,
                    )
                })
            },
            deleteByIds = { ids -> exerciseDao.deleteByIds(ids) },
            clearAll = { exerciseDao.deleteAll() },
            onProgress = onProgress,
        )
    }

    private fun startEndOf(record: Record): Pair<Instant, Instant> = when (record) {
        is StepsRecord -> record.startTime to record.endTime
        is DistanceRecord -> record.startTime to record.endTime
        is FloorsClimbedRecord -> record.startTime to record.endTime
        is ActiveCaloriesBurnedRecord -> record.startTime to record.endTime
        is TotalCaloriesBurnedRecord -> record.startTime to record.endTime
        is HydrationRecord -> record.startTime to record.endTime
        else -> error("startEndOf not implemented for $record")
    }

    companion object {
        private const val TAG = "HealthRepository"
        private const val PAGE_SIZE = 1000
    }
}

data class DbSummary(
    val vitals: List<VitalSampleEntity>,
    val intervals: List<IntervalMetricEntity>,
    val bloodPressure: List<BloodPressureEntity>,
    val sleepSessions: List<SleepSessionEntity>,
    val sleepStages: List<SleepStageEntity>,
    val exercises: List<ExerciseSessionEntity>,
)

fun sleepStageName(stage: Int): String = when (stage) {
    SleepSessionRecord.STAGE_TYPE_AWAKE -> "AWAKE"
    SleepSessionRecord.STAGE_TYPE_SLEEPING -> "SLEEPING"
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "OUT_OF_BED"
    SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
    SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
    SleepSessionRecord.STAGE_TYPE_REM -> "REM"
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "AWAKE_IN_BED"
    else -> "UNKNOWN"
}

fun exerciseTypeName(type: Int): String = "type_$type"
