package com.reminder.local.ui.screen.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.local.diagnostics.core.DiagnosticTraceSummary
import com.reminder.local.diagnostics.platform.DiagnosticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val enhancedUntil: Long = 0L,
    val summaries: List<DiagnosticTraceSummary> = emptyList()
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val repository: DiagnosticsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.enhancedUntil.collectLatest { until ->
                _uiState.value = _uiState.value.copy(enhancedUntil = until)
            }
        }
        refresh()
    }

    fun refresh() { _uiState.value = _uiState.value.copy(summaries = repository.summaries()) }

    fun setEnhanced(enabled: Boolean) = viewModelScope.launch { repository.setEnhanced(enabled) }

    fun clear() { repository.clear(); refresh() }

    fun exportIntent() = repository.exportIntent()
}
