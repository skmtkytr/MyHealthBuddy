package com.skmtkytr.myhealthbuddy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.skmtkytr.myhealthbuddy.data.ChatTurn
import com.skmtkytr.myhealthbuddy.data.ClaudeClient
import com.skmtkytr.myhealthbuddy.data.HealthRepository
import com.skmtkytr.myhealthbuddy.data.Settings
import com.skmtkytr.myhealthbuddy.data.SystemPromptComposer
import com.skmtkytr.myhealthbuddy.data.earliest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ChatUiState(
    val messages: List<ChatTurn> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    val snapshotDays: Int? = null,
    val snapshotChars: Int? = null,
    val snapshotPreview: String? = null,
    val lastRequestJson: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = HealthRepository(application)
    private val settings = Settings(application)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** Frozen at the first turn of each conversation so prompt-caching can hit. */
    private var snapshotSystem: String? = null

    fun onInputChange(v: String) {
        _state.value = _state.value.copy(input = v)
    }

    fun send(override: String? = null) {
        val current = _state.value
        val text = (override ?: current.input).trim()
        if (text.isEmpty() || current.sending) return
        val baseUrl = settings.baseUrl
        val apiKey = settings.apiKey
        val isAnthropicCloud = baseUrl.contains("api.anthropic.com")
        if (isAnthropicCloud && apiKey.isNullOrBlank()) {
            _state.value = current.copy(error = "Anthropic API キーが未設定です。設定タブから入力してください。")
            return
        }
        val newHistory = current.messages + ChatTurn(role = "user", content = text)
        _state.value = current.copy(messages = newHistory, input = "", sending = true, error = null)

        viewModelScope.launch {
            try {
                val system = snapshotSystem ?: run {
                    val summary = repo.observeSummary().first()
                    val zone = ZoneId.systemDefault()
                    val days = summary.earliest()
                        ?.atZone(zone)?.toLocalDate()
                        ?.let { ChronoUnit.DAYS.between(it, LocalDate.now(zone)).toInt() + 1 }
                        ?.coerceAtLeast(1)
                        ?: 14
                    val built = SystemPromptComposer.compose(
                        basePrompt = settings.systemPrompt,
                        profile = settings.profile,
                        summary = summary,
                        zone = zone,
                        recentDays = days,
                        maxSleepNights = Int.MAX_VALUE,
                        maxExercises = Int.MAX_VALUE,
                    )
                    snapshotSystem = built
                    _state.value = _state.value.copy(
                        snapshotDays = days,
                        snapshotChars = built.length,
                        snapshotPreview = built,
                    )
                    built
                }
                val client = ClaudeClient(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = settings.model,
                )
                val reply = client.send(
                    system = system,
                    history = newHistory,
                    onRequestBuilt = { body ->
                        _state.value = _state.value.copy(lastRequestJson = body)
                    },
                )
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatTurn(role = "assistant", content = reply),
                    sending = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    sending = false,
                    error = e.message ?: e::class.simpleName.orEmpty(),
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun newConversation() {
        snapshotSystem = null
        _state.value = ChatUiState()
    }

    fun analyzeOverall() = send(override = ANALYZE_OVERALL)

    fun useExample(text: String) {
        _state.value = _state.value.copy(input = text)
    }

    companion object {
        const val ANALYZE_OVERALL =
            "提供されている全ヘルスデータを横断的に分析してください。" +
                "最も気になるトレンド、好調な指標、改善すべき習慣を、" +
                "観測→解釈→推奨の順に挙げてください。"

        val EXAMPLES = listOf(
            "ここ最近の睡眠の質はどう？",
            "活動量が落ちてる日に共通点ある？",
            "ストレスや疲労のサインは出てる？",
            "来週意識すべき習慣を3つ提案して",
        )

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(this[APPLICATION_KEY] as Application)
            }
        }
    }
}
