package com.skmtkytr.myhealthbuddy.data

import com.skmtkytr.myhealthbuddy.data.db.IntervalType
import com.skmtkytr.myhealthbuddy.data.db.VitalType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object HealthContextBuilder {

    fun build(
        summary: DbSummary,
        zone: ZoneId = ZoneId.systemDefault(),
        recentDays: Int = 14,
        maxSleepNights: Int = 5,
        maxExercises: Int = 5,
    ): String {
        if (summary.vitals.isEmpty() && summary.intervals.isEmpty() && summary.sleepSessions.isEmpty() &&
            summary.bloodPressure.isEmpty() && summary.exercises.isEmpty()
        ) {
            return "ヘルスデータはまだ取得されていません。"
        }

        val today = LocalDate.now(zone)
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val sb = StringBuilder()

        sb.appendLine("# ユーザーのヘルスデータ要約")
        sb.appendLine("- データ範囲: ${summary.earliest()?.atZone(zone)?.toLocalDate()?.format(dateFmt) ?: "-"} 〜 ${summary.latest()?.atZone(zone)?.toLocalDate()?.format(dateFmt) ?: "-"}")
        sb.appendLine("- 観測日数: ${summary.observedDays(zone).size}")
        sb.appendLine()

        // 直近データ (各 vital の最新値)
        sb.appendLine("## 最新計測値")
        latestVitalLine(sb, summary, VitalType.Weight, "体重", "kg", zone, dateTimeFmt)
        latestVitalLine(sb, summary, VitalType.Height, "身長", "m", zone, dateTimeFmt)
        latestVitalLine(sb, summary, VitalType.BodyFat, "体脂肪率", "%", zone, dateTimeFmt)
        latestVitalLine(sb, summary, VitalType.LeanBodyMass, "除脂肪体重", "kg", zone, dateTimeFmt)
        latestVitalLine(sb, summary, VitalType.RestingHeartRate, "安静時心拍", "bpm", zone, dateTimeFmt)
        latestVitalLine(sb, summary, VitalType.HeartRateVariability, "HRV (RMSSD)", "ms", zone, dateTimeFmt)
        latestVitalLine(sb, summary, VitalType.OxygenSaturation, "SpO2", "%", zone, dateTimeFmt)
        latestVitalLine(sb, summary, VitalType.RespiratoryRate, "呼吸数", "rpm", zone, dateTimeFmt)
        latestVitalLine(sb, summary, VitalType.BodyTemperature, "体温", "°C", zone, dateTimeFmt)
        latestVitalLine(sb, summary, VitalType.BloodGlucose, "血糖", "mg/dL", zone, dateTimeFmt)
        summary.latestBloodPressure()?.let {
            sb.appendLine("- 血圧: ${it.systolic.toInt()}/${it.diastolic.toInt()} mmHg  (${it.time.atZone(zone).format(dateTimeFmt)})")
        }
        sb.appendLine()

        // 睡眠（指定上限まで）
        val nights = summary.sleepNights().take(maxSleepNights)
        if (nights.isNotEmpty()) {
            sb.appendLine("## 直近の睡眠")
            for (n in nights) {
                val start = n.session.startTime.atZone(zone)
                val end = n.session.endTime.atZone(zone)
                sb.appendLine(
                    "- ${start.toLocalDate().format(dateFmt)} 入眠 ${start.toLocalTime().format(HM)} → 起床 ${end.toLocalDate().format(dateFmt)} ${end.toLocalTime().format(HM)}  合計 ${formatDuration(n.duration)}",
                )
                if (n.stageBreakdown.isNotEmpty()) {
                    val parts = n.stageBreakdown.entries
                        .sortedByDescending { it.value }
                        .joinToString(", ") { (stage, dur) -> "$stage ${formatDuration(dur)}" }
                    sb.appendLine("    ステージ: $parts")
                }
            }
            sb.appendLine()
        }

        // 運動（指定上限まで）
        val recentExercises = summary.recentExercises(maxExercises)
        if (recentExercises.isNotEmpty()) {
            sb.appendLine("## 直近の運動")
            for (e in recentExercises) {
                val start = e.startTime.atZone(zone)
                val dur = Duration.between(e.startTime, e.endTime)
                sb.appendLine(
                    "- ${start.format(dateTimeFmt)}  ${e.exerciseType}  ${formatDuration(dur)}  ${e.title.orEmpty()}",
                )
            }
            sb.appendLine()
        }

        // 日別アクティビティ（範囲分）
        sb.appendLine("## 日別 (直近 $recentDays 日)")
        sb.appendLine("| 日付 | 歩数 | 距離(km) | 階段 | 活動kcal | 睡眠 | 平均HR |")
        sb.appendLine("|---|---|---|---|---|---|---|")
        val stepsByDay = summary.intervalByDay(IntervalType.Steps, zone)
        val distanceByDay = summary.intervalByDay(IntervalType.Distance, zone)
        val floorsByDay = summary.intervalByDay(IntervalType.FloorsClimbed, zone)
        val activeCalByDay = summary.intervalByDay(IntervalType.ActiveCalories, zone)
        val hrByDay = summary.vitalByDayAvg(VitalType.HeartRate, zone)
        val sleepEndDay = summary.sleepNights().associate { it.session.endTime.atZone(zone).toLocalDate() to it.duration }
        var d = today
        repeat(recentDays) {
            val st = stepsByDay[d]?.toLong() ?: 0
            val km = distanceByDay[d]?.let { "%.2f".format(it / 1000.0) } ?: "-"
            val fl = floorsByDay[d]?.toInt() ?: 0
            val cal = activeCalByDay[d]?.toInt() ?: 0
            val sl = sleepEndDay[d]?.let { formatDuration(it) } ?: "-"
            val hr = hrByDay[d]?.let { "%.0f".format(it) } ?: "-"
            sb.appendLine("| ${d.format(dateFmt)} | $st | $km | $fl | $cal | $sl | $hr |")
            d = d.minusDays(1)
        }

        return sb.toString()
    }

    private fun latestVitalLine(
        sb: StringBuilder,
        summary: DbSummary,
        type: VitalType,
        label: String,
        unit: String,
        zone: ZoneId,
        fmt: DateTimeFormatter,
    ) {
        summary.latestVital(type)?.let {
            sb.appendLine("- $label: ${"%.2f".format(it.value)} $unit  (${it.time.atZone(zone).format(fmt)})")
        }
    }

    private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private fun formatDuration(d: Duration): String {
        val h = d.toHours()
        val m = d.toMinutes() % 60
        return "${h}h${m}m"
    }
}
