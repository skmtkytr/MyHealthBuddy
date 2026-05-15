package com.skmtkytr.myhealthbuddy.data

import com.skmtkytr.myhealthbuddy.data.db.BloodPressureEntity
import com.skmtkytr.myhealthbuddy.data.db.ExerciseSessionEntity
import com.skmtkytr.myhealthbuddy.data.db.IntervalMetricEntity
import com.skmtkytr.myhealthbuddy.data.db.IntervalType
import com.skmtkytr.myhealthbuddy.data.db.SleepSessionEntity
import com.skmtkytr.myhealthbuddy.data.db.SleepStageEntity
import com.skmtkytr.myhealthbuddy.data.db.VitalSampleEntity
import com.skmtkytr.myhealthbuddy.data.db.VitalType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun DbSummary.vitalsOf(type: VitalType): List<VitalSampleEntity> =
    vitals.filter { it.type == type.name }

fun DbSummary.intervalsOf(type: IntervalType): List<IntervalMetricEntity> =
    intervals.filter { it.type == type.name }

fun DbSummary.latestVital(type: VitalType): VitalSampleEntity? =
    vitalsOf(type).maxByOrNull { it.time }

fun DbSummary.avgVital(type: VitalType): Double? =
    vitalsOf(type).map { it.value }.takeIf { it.isNotEmpty() }?.average()

fun DbSummary.totalInterval(type: IntervalType): Double =
    intervalsOf(type).sumOf { it.value }

fun DbSummary.intervalByDay(
    type: IntervalType,
    zone: ZoneId = ZoneId.systemDefault(),
): Map<LocalDate, Double> =
    intervalsOf(type).groupBy { it.startTime.atZone(zone).toLocalDate() }
        .mapValues { (_, v) -> v.sumOf { it.value } }

fun DbSummary.vitalByDayAvg(
    type: VitalType,
    zone: ZoneId = ZoneId.systemDefault(),
): Map<LocalDate, Double> =
    vitalsOf(type).groupBy { it.time.atZone(zone).toLocalDate() }
        .mapValues { (_, v) -> v.map { it.value }.average() }

fun DbSummary.latestBloodPressure(): BloodPressureEntity? =
    bloodPressure.maxByOrNull { it.time }

fun DbSummary.earliest(): Instant? = sequence {
    yieldAll(vitals.map { it.time })
    yieldAll(intervals.map { it.startTime })
    yieldAll(bloodPressure.map { it.time })
    yieldAll(sleepSessions.map { it.startTime })
    yieldAll(exercises.map { it.startTime })
}.minOrNull()

fun DbSummary.latest(): Instant? = sequence {
    yieldAll(vitals.map { it.time })
    yieldAll(intervals.map { it.endTime })
    yieldAll(bloodPressure.map { it.time })
    yieldAll(sleepSessions.map { it.endTime })
    yieldAll(exercises.map { it.endTime })
}.maxOrNull()

data class SleepNight(
    val session: SleepSessionEntity,
    val stages: List<SleepStageEntity>,
    val duration: Duration,
    val stageBreakdown: Map<String, Duration>,
)

fun DbSummary.sleepNights(): List<SleepNight> {
    val stagesBySession = sleepStages.groupBy { it.sessionId }
    return sleepSessions.sortedByDescending { it.endTime }.map { session ->
        val stages = (stagesBySession[session.id] ?: emptyList()).sortedBy { it.startTime }
        val breakdown = stages.groupBy { it.stage }.mapValues { (_, v) ->
            v.fold(Duration.ZERO) { acc, s -> acc.plus(Duration.between(s.startTime, s.endTime)) }
        }
        SleepNight(
            session = session,
            stages = stages,
            duration = Duration.between(session.startTime, session.endTime),
            stageBreakdown = breakdown,
        )
    }
}

fun DbSummary.recentExercises(limit: Int = 10): List<ExerciseSessionEntity> =
    exercises.sortedByDescending { it.startTime }.take(limit)

fun DbSummary.observedDays(zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> {
    val days = mutableSetOf<LocalDate>()
    vitals.forEach { days += it.time.atZone(zone).toLocalDate() }
    intervals.forEach { days += it.startTime.atZone(zone).toLocalDate() }
    bloodPressure.forEach { days += it.time.atZone(zone).toLocalDate() }
    sleepSessions.forEach { days += it.endTime.atZone(zone).toLocalDate() }
    exercises.forEach { days += it.startTime.atZone(zone).toLocalDate() }
    return days
}
