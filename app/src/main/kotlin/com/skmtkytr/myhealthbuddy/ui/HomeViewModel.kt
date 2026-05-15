package com.skmtkytr.myhealthbuddy.ui

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.skmtkytr.myhealthbuddy.data.DbSummary
import com.skmtkytr.myhealthbuddy.data.HealthRepository
import com.skmtkytr.myhealthbuddy.data.SyncProgress
import com.skmtkytr.myhealthbuddy.data.db.DataType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class SyncStatus(
    val running: Boolean,
    val perType: Map<DataType, SyncProgress>,
)

sealed interface Gate {
    data object Loading : Gate
    data object Unavailable : Gate
    data object NeedsPermissions : Gate
    data object Ready : Gate
    data class Error(val message: String) : Gate
}

data class HomeUiState(
    val gate: Gate,
    val summary: DbSummary,
    val sync: SyncStatus,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = HealthRepository(application)
    val permissions: Set<String> = repo.permissions

    private val gateFlow = MutableStateFlow<Gate>(Gate.Loading)
    private val syncFlow = MutableStateFlow(SyncStatus(running = false, perType = emptyMap()))

    val state: StateFlow<HomeUiState> = combine(
        gateFlow,
        repo.observeSummary(),
        syncFlow,
    ) { gate, summary, sync ->
        HomeUiState(gate = gate, summary = summary, sync = sync)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(
            gate = Gate.Loading,
            summary = DbSummary(
                vitals = emptyList(),
                intervals = emptyList(),
                bloodPressure = emptyList(),
                sleepSessions = emptyList(),
                sleepStages = emptyList(),
                exercises = emptyList(),
            ),
            sync = SyncStatus(running = false, perType = emptyMap()),
        ),
    )

    init {
        kickOff()
    }

    fun kickOff() {
        viewModelScope.launch {
            gateFlow.value = Gate.Loading
            when (repo.availability()) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    if (!repo.hasAllPermissions()) {
                        gateFlow.value = Gate.NeedsPermissions
                    } else {
                        gateFlow.value = Gate.Ready
                        runSync()
                    }
                }
                else -> gateFlow.value = Gate.Unavailable
            }
        }
    }

    fun onPermissionResult(granted: Set<String>) {
        viewModelScope.launch {
            if (granted.containsAll(permissions)) {
                gateFlow.value = Gate.Ready
                runSync()
            } else {
                gateFlow.value = Gate.NeedsPermissions
            }
        }
    }

    fun resync() {
        viewModelScope.launch { runSync() }
    }

    private suspend fun runSync() {
        if (syncFlow.value.running) return
        syncFlow.value = SyncStatus(running = true, perType = emptyMap())
        try {
            repo.sync { p ->
                syncFlow.value = syncFlow.value.copy(
                    perType = syncFlow.value.perType + (p.type to p),
                )
            }
        } catch (e: Exception) {
            gateFlow.value = Gate.Error(e.message ?: e::class.simpleName.orEmpty())
        } finally {
            syncFlow.value = syncFlow.value.copy(running = false)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(this[APPLICATION_KEY] as Application)
            }
        }
    }
}
