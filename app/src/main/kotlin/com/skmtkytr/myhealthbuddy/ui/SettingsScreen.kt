package com.skmtkytr.myhealthbuddy.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.skmtkytr.myhealthbuddy.data.Focus
import com.skmtkytr.myhealthbuddy.data.Gender
import com.skmtkytr.myhealthbuddy.data.Settings
import com.skmtkytr.myhealthbuddy.data.UserProfile

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = Settings(application)

    fun current(): SettingsSnapshot = SettingsSnapshot(
        baseUrl = settings.baseUrl,
        apiKey = settings.apiKey.orEmpty(),
        model = settings.model,
        profile = settings.profile,
        systemPrompt = settings.systemPrompt,
    )

    fun save(snapshot: SettingsSnapshot) {
        settings.baseUrl = snapshot.baseUrl
        settings.apiKey = snapshot.apiKey.takeIf { it.isNotBlank() }
        settings.model = snapshot.model
        settings.profile = snapshot.profile
        settings.systemPrompt = snapshot.systemPrompt
    }

    fun defaultSystemPrompt(): String = Settings.DEFAULT_SYSTEM_PROMPT

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(this[APPLICATION_KEY] as Application) }
        }
    }
}

data class SettingsSnapshot(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val profile: UserProfile,
    val systemPrompt: String,
)

private data class Preset(val label: String, val baseUrl: String, val model: String)

private val PRESETS = listOf(
    Preset("Anthropic Sonnet 4.6", "https://api.anthropic.com", "claude-sonnet-4-6"),
    Preset("Anthropic Opus 4.7", "https://api.anthropic.com", "claude-opus-4-7"),
    Preset("LM Studio (LAN)", "http://192.168.1.100:1234", "your-model"),
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val initial = remember { viewModel.current() }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var gender by remember { mutableStateOf(initial.profile.gender) }
    var ageText by remember { mutableStateOf(initial.profile.age?.toString().orEmpty()) }
    var heightText by remember { mutableStateOf(initial.profile.heightCm?.let { "%.1f".format(it) }.orEmpty()) }
    var weightText by remember { mutableStateOf(initial.profile.weightKg?.let { "%.1f".format(it) }.orEmpty()) }
    var notes by remember { mutableStateOf(initial.profile.notes) }
    var focus by remember { mutableStateOf(initial.profile.focus) }
    var systemPrompt by remember { mutableStateOf(initial.systemPrompt) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("設定", style = MaterialTheme.typography.headlineSmall)

        SectionTitle("バックエンド")
        Text(
            "Anthropic 互換エンドポイントを指定 (cloud / LM Studio / Ollama 等)。ローカルは API Key 空でOK。",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = baseUrl, onValueChange = { baseUrl = it; saved = false },
            label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it; saved = false },
            label = { Text("API Key (ローカルは空)") }, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it; saved = false },
            label = { Text("Model") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Text("プリセット:", style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PRESETS.forEach { p ->
                TextButton(onClick = {
                    baseUrl = p.baseUrl; model = p.model; saved = false
                }) { Text(p.label, style = MaterialTheme.typography.bodySmall) }
            }
        }

        HorizontalDivider()
        SectionTitle("プロフィール")
        Text("性別")
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Gender.entries.forEach { g ->
                RadioButton(selected = gender == g, onClick = { gender = g; saved = false })
                Text(g.label, modifier = Modifier.padding(end = 8.dp))
            }
        }
        OutlinedTextField(
            value = ageText, onValueChange = { ageText = it.filter { c -> c.isDigit() }; saved = false },
            label = { Text("年齢") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
        )
        OutlinedTextField(
            value = heightText, onValueChange = { heightText = it.filter { c -> c.isDigit() || c == '.' }; saved = false },
            label = { Text("身長 (cm)") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
        )
        OutlinedTextField(
            value = weightText, onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' }; saved = false },
            label = { Text("体重 (kg, Health Connect 優先・空でOK)") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
        )
        OutlinedTextField(
            value = notes, onValueChange = { notes = it; saved = false },
            label = { Text("メモ（持病・服薬・最近の状態など 任意）") },
            modifier = Modifier.fillMaxWidth(), minLines = 2,
        )

        HorizontalDivider()
        SectionTitle("関心領域 (アドバイスの方向性)")
        Focus.entries.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = focus == f, onClick = { focus = f; saved = false })
                Column {
                    Text(f.label)
                    Text(f.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider()
        SectionTitle("システムプロンプト")
        Text(
            "Claude に毎回投げる基本指示。ここの直後にプロフィール + ヘルスデータが連結されます。",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = systemPrompt, onValueChange = { systemPrompt = it; saved = false },
            modifier = Modifier.fillMaxWidth(), minLines = 6,
        )
        TextButton(onClick = {
            systemPrompt = viewModel.defaultSystemPrompt(); saved = false
        }) { Text("デフォルトに戻す") }

        HorizontalDivider()
        Button(onClick = {
            viewModel.save(
                SettingsSnapshot(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = model,
                    profile = UserProfile(
                        gender = gender,
                        age = ageText.toIntOrNull(),
                        heightCm = heightText.toDoubleOrNull(),
                        weightKg = weightText.toDoubleOrNull(),
                        notes = notes,
                        focus = focus,
                    ),
                    systemPrompt = systemPrompt,
                ),
            )
            saved = true
        }) { Text("保存") }
        if (saved) Text("保存しました。チャットは「新規会話」でスナップショットを再生成。", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}
