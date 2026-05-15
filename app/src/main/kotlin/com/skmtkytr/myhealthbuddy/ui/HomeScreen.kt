package com.skmtkytr.myhealthbuddy.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skmtkytr.myhealthbuddy.data.DbSummary
import com.skmtkytr.myhealthbuddy.data.SyncProgress
import com.skmtkytr.myhealthbuddy.data.db.DataType
import com.skmtkytr.myhealthbuddy.data.db.IntervalType
import com.skmtkytr.myhealthbuddy.data.db.VitalType
import com.skmtkytr.myhealthbuddy.data.earliest
import com.skmtkytr.myhealthbuddy.data.intervalByDay
import com.skmtkytr.myhealthbuddy.data.latest
import com.skmtkytr.myhealthbuddy.data.latestBloodPressure
import com.skmtkytr.myhealthbuddy.data.latestVital
import com.skmtkytr.myhealthbuddy.data.recentExercises
import com.skmtkytr.myhealthbuddy.data.sleepNights
import com.skmtkytr.myhealthbuddy.data.totalInterval
import com.skmtkytr.myhealthbuddy.data.vitalByDayAvg
import com.skmtkytr.myhealthbuddy.data.vitalsOf
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    when (val gate = state.gate) {
        Gate.Loading -> CenteredColumn { CircularProgressIndicator(); Text("起動中…") }
        Gate.Unavailable -> CenteredColumn {
            Text("Health Connect が利用できません")
            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                }
                context.startActivity(intent)
            }) { Text("Health Connect をインストール") }
        }
        Gate.NeedsPermissions -> CenteredColumn {
            Text("ヘルスデータの読み取り権限が必要です")
            Button(onClick = { permissionLauncher.launch(viewModel.permissions) }) {
                Text("権限を許可")
            }
        }
        is Gate.Error -> CenteredColumn {
            Text("エラー: ${gate.message}")
            Button(onClick = viewModel::kickOff) { Text("再試行") }
        }
        Gate.Ready -> ReadyContent(state.summary, state.sync.running, state.sync.perType, viewModel::resync)
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun ReadyContent(
    summary: DbSummary,
    syncRunning: Boolean,
    syncPerType: Map<DataType, SyncProgress>,
    onSync: () -> Unit,
) {
    val zone = ZoneId.systemDefault()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("MyHealthBuddy", style = MaterialTheme.typography.headlineMedium) }
        item { SyncRow(syncRunning, syncPerType, onSync) }
        item {
            val earliest = summary.earliest()?.atZone(zone)?.toLocalDate()?.format(DATE) ?: "-"
            val latest = summary.latest()?.atZone(zone)?.toLocalDate()?.format(DATE) ?: "-"
            Text("データ範囲: $earliest 〜 $latest", style = MaterialTheme.typography.titleMedium)
        }

        item { Card("Activity") { ActivityCard(summary, zone) } }
        item { Card("Vitals") { VitalsCard(summary, zone) } }
        item { Card("Body") { BodyCard(summary, zone) } }
        item { Card("Sleep") { SleepCard(summary, zone) } }
        item { Card("Exercise") { ExerciseCard(summary, zone) } }

        item { HorizontalDivider() }
        item { Text("日別 (直近 14 日)", style = MaterialTheme.typography.titleMedium) }
        items(summary.dailyRows(zone, days = 14)) { row -> Text(row) }
    }
}

@Composable
private fun SyncRow(
    syncRunning: Boolean,
    syncPerType: Map<DataType, SyncProgress>,
    onSync: () -> Unit,
) {
    if (syncRunning) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        val parts = syncPerType.entries.joinToString("  ") { (k, v) ->
            "$k:${v.phase.name.take(4)}(${v.processed})"
        }
        Text("同期中  $parts", style = MaterialTheme.typography.bodySmall)
    } else {
        Button(onClick = onSync) { Text("同期") }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ActivityCard(summary: DbSummary, zone: ZoneId) {
    val today = java.time.LocalDate.now(zone)
    val stepsToday = (summary.intervalByDay(IntervalType.Steps, zone)[today] ?: 0.0).toLong()
    val distToday = summary.intervalByDay(IntervalType.Distance, zone)[today] ?: 0.0
    val floorsToday = (summary.intervalByDay(IntervalType.FloorsClimbed, zone)[today] ?: 0.0).toInt()
    val activeToday = (summary.intervalByDay(IntervalType.ActiveCalories, zone)[today] ?: 0.0).toInt()
    val totalToday = (summary.intervalByDay(IntervalType.TotalCalories, zone)[today] ?: 0.0).toInt()
    val hydrationToday = summary.intervalByDay(IntervalType.Hydration, zone)[today] ?: 0.0

    Text("本日 歩数: $stepsToday")
    Text("距離: %.2f km".format(distToday / 1000.0))
    Text("階段: $floorsToday")
    Text("活動カロリー: $activeToday kcal")
    if (totalToday > 0) Text("総消費: $totalToday kcal")
    if (hydrationToday > 0) Text("水分: %.2f L".format(hydrationToday))
    Text("総歩数(全期間): ${summary.totalInterval(IntervalType.Steps).toLong()}")
}

@Composable
private fun VitalsCard(summary: DbSummary, zone: ZoneId) {
    val today = java.time.LocalDate.now(zone)
    val hrAvgToday = summary.vitalByDayAvg(VitalType.HeartRate, zone)[today]
    val hrTotal = summary.vitalsOf(VitalType.HeartRate).size
    Text("HR 本日平均: ${hrAvgToday?.let { "%.0f bpm".format(it) } ?: "-"}  (samples=$hrTotal)")
    line(summary.latestVital(VitalType.RestingHeartRate), "安静時HR", "bpm", zone)
    line(summary.latestVital(VitalType.HeartRateVariability), "HRV", "ms", zone)
    line(summary.latestVital(VitalType.OxygenSaturation), "SpO2", "%", zone)
    line(summary.latestVital(VitalType.RespiratoryRate), "呼吸数", "rpm", zone)
    line(summary.latestVital(VitalType.BodyTemperature), "体温", "°C", zone)
    line(summary.latestVital(VitalType.BloodGlucose), "血糖", "mg/dL", zone)
    summary.latestBloodPressure()?.let {
        Text("血圧: ${it.systolic.toInt()}/${it.diastolic.toInt()} mmHg  (${it.time.atZone(zone).format(HM)})")
    }
}

@Composable
private fun BodyCard(summary: DbSummary, zone: ZoneId) {
    line(summary.latestVital(VitalType.Weight), "体重", "kg", zone)
    line(summary.latestVital(VitalType.Height), "身長", "m", zone)
    line(summary.latestVital(VitalType.BodyFat), "体脂肪率", "%", zone)
    line(summary.latestVital(VitalType.LeanBodyMass), "除脂肪体重", "kg", zone)
}

@Composable
private fun SleepCard(summary: DbSummary, zone: ZoneId) {
    val nights = summary.sleepNights().take(3)
    if (nights.isEmpty()) {
        Text("(no data)")
        return
    }
    for (n in nights) {
        val start = n.session.startTime.atZone(zone)
        val end = n.session.endTime.atZone(zone)
        Text(
            "${end.toLocalDate().format(DATE)}: ${start.toLocalDate().format(DATE)} ${start.toLocalTime().format(HM)} → ${end.toLocalTime().format(HM)}  (${formatDuration(n.duration)})",
        )
        if (n.stageBreakdown.isNotEmpty()) {
            val parts = n.stageBreakdown.entries
                .sortedByDescending { it.value }
                .joinToString(", ") { (s, d) -> "$s ${formatDuration(d)}" }
            Text("  $parts", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExerciseCard(summary: DbSummary, zone: ZoneId) {
    val recent = summary.recentExercises(5)
    if (recent.isEmpty()) { Text("(no data)"); return }
    for (e in recent) {
        val start = e.startTime.atZone(zone)
        val dur = Duration.between(e.startTime, e.endTime)
        Text(
            "${start.toLocalDate().format(DATE)} ${start.toLocalTime().format(HM)}  ${e.exerciseType}  ${formatDuration(dur)}  ${e.title.orEmpty()}",
        )
    }
}

@Composable
private fun line(
    v: com.skmtkytr.myhealthbuddy.data.db.VitalSampleEntity?,
    label: String,
    unit: String,
    zone: ZoneId,
) {
    if (v == null) return
    Text("$label: %.2f $unit  (${v.time.atZone(zone).format(HM)})".format(v.value))
}

private fun DbSummary.dailyRows(zone: ZoneId, days: Int): List<String> {
    val today = java.time.LocalDate.now(zone)
    val stepsByDay = intervalByDay(IntervalType.Steps, zone)
    val distByDay = intervalByDay(IntervalType.Distance, zone)
    val activeByDay = intervalByDay(IntervalType.ActiveCalories, zone)
    val hrByDay = vitalByDayAvg(VitalType.HeartRate, zone)
    val sleepEndDay = sleepNights().associate { it.session.endTime.atZone(zone).toLocalDate() to it.duration }
    val rows = mutableListOf<String>()
    var d = today
    repeat(days) {
        val st = (stepsByDay[d] ?: 0.0).toLong()
        val km = distByDay[d]?.let { "%.1fkm".format(it / 1000.0) } ?: "-"
        val cal = (activeByDay[d] ?: 0.0).toInt()
        val sl = sleepEndDay[d]?.let { formatDuration(it) } ?: "-"
        val hr = hrByDay[d]?.let { "%.0f".format(it) } ?: "-"
        rows += "${d.format(DATE)}  steps=$st  $km  cal=$cal  sleep=$sl  hr=$hr"
        d = d.minusDays(1)
    }
    return rows
}

private fun formatDuration(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return "${h}h${m}m"
}
