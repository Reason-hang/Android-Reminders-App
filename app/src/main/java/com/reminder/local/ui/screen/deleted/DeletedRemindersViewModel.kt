package com.reminder.local.ui.screen.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.usecase.PermanentlyDeleteReminderUseCase
import com.reminder.local.domain.usecase.RestoreReminderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeletedRemindersUiState(
    val reminders: List<Reminder> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val confirmPermanentDelete: Boolean = false
)

@HiltViewModel
class DeletedRemindersViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val restoreReminderUseCase: RestoreReminderUseCase,
    private val permanentlyDeleteReminderUseCase: PermanentlyDeleteReminderUseCase
) : ViewModel() {
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val confirmingPermanentDelete = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    val uiState: StateFlow<DeletedRemindersUiState> = combine(
        reminderRepository.observeDeleted(), selectedIds, confirmingPermanentDelete
    ) { reminders, selected, confirming ->
        DeletedRemindersUiState(reminders, selected.intersect(reminders.map { it.id }.toSet()), confirming)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeletedRemindersUiState())

    fun toggleSelection(id: Long) {
        selectedIds.value = selectedIds.value.toMutableSet().also {
            if (!it.add(id)) it.remove(id)
        }
    }

    fun selectAll() {
        val all = uiState.value.reminders.map { it.id }.toSet()
        selectedIds.value = if (selectedIds.value.size == all.size) emptySet() else all
    }

    fun restoreSelected() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val failed = ids.count { !restoreReminderUseCase(it) }
            selectedIds.value = emptySet()
            _messages.emit(if (failed == 0) "已恢复 ${ids.size} 条提醒" else "已恢复 ${ids.size - failed} 条，$failed 条恢复失败")
        }
    }

    fun requestPermanentDelete() {
        if (selectedIds.value.isNotEmpty()) confirmingPermanentDelete.value = true
    }

    fun dismissPermanentDelete() {
        confirmingPermanentDelete.value = false
    }

    fun confirmPermanentDelete() {
        val ids = selectedIds.value
        confirmingPermanentDelete.value = false
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val failed = ids.count { !permanentlyDeleteReminderUseCase(it) }
            selectedIds.value = emptySet()
            _messages.emit(if (failed == 0) "已永久删除 ${ids.size} 条" else "已永久删除 ${ids.size - failed} 条，$failed 条失败")
        }
    }
}
