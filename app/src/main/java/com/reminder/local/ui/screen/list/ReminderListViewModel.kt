package com.reminder.local.ui.screen.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.local.data.repository.CategoryRepository
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.model.ALL_CATEGORY_FILTER_ID
import com.reminder.local.domain.model.Category
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.UNCATEGORIZED_FILTER_ID
import com.reminder.local.domain.usecase.CompleteReminderUseCase
import com.reminder.local.domain.usecase.DeleteReminderUseCase
import com.reminder.local.notification.NotificationHelper
import com.reminder.local.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReminderListUiState(
    val pending: List<Reminder> = emptyList(),
    val done: List<Reminder> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoryMap: Map<Long, Category> = emptyMap(),
    val selectedCategoryId: Long = ALL_CATEGORY_FILTER_ID,
    val exactAlarmGranted: Boolean = true,
    val pendingDeleteReminder: Reminder? = null,
    val pendingCompleteScopeReminder: Reminder? = null
)

sealed interface ListEvent {
    data class ShowUndoComplete(val reminder: Reminder) : ListEvent
}

@HiltViewModel
class ReminderListViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val categoryRepository: CategoryRepository,
    private val completeReminderUseCase: CompleteReminderUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val notificationHelper: NotificationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow(ALL_CATEGORY_FILTER_ID)
    private val dialogState = MutableStateFlow(Pair<Reminder?, Reminder?>(null, null))

    private val _events = MutableSharedFlow<ListEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<ReminderListUiState> = combine(
        reminderRepository.observeAll(),
        categoryRepository.observeAll(),
        selectedCategoryId,
        dialogState
    ) { reminders, categories, selectedId, dialogs ->
        val filtered = when (selectedId) {
            ALL_CATEGORY_FILTER_ID -> reminders
            UNCATEGORIZED_FILTER_ID -> reminders.filter { it.categoryId == null }
            else -> reminders.filter { it.categoryId == selectedId }
        }
        val pending = filtered.filter { it.status != ReminderStatus.DONE }
            .sortedWith(compareBy({ it.priority.sortWeight }, { it.effectiveTime }))
        val done = filtered.filter { it.status == ReminderStatus.DONE }
            .sortedByDescending { it.completedAt ?: it.updatedAt }

        ReminderListUiState(
            pending = pending,
            done = done,
            categories = categories,
            categoryMap = categories.associateBy { it.id },
            selectedCategoryId = selectedId,
            exactAlarmGranted = PermissionUtils.canScheduleExactAlarms(context),
            pendingDeleteReminder = dialogs.first,
            pendingCompleteScopeReminder = dialogs.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderListUiState())

    fun selectCategory(id: Long) {
        selectedCategoryId.value = id
    }

    /** 点击勾选框 或 右滑：非重复直接完成/取消完成；重复提醒需要先问清楚"仅本次"还是"全部"。 */
    fun onToggleComplete(reminder: Reminder) {
        if (reminder.status == ReminderStatus.DONE) {
            viewModelScope.launch { completeReminderUseCase.markPending(reminder) }
            return
        }
        if (reminder.isRepeating) {
            dialogState.value = dialogState.value.copy(second = reminder)
        } else {
            viewModelScope.launch {
                completeReminderUseCase.markDone(reminder, RepeatActionScope.ALL)
                notificationHelper.cancelNotification(reminder)
                _events.emit(ListEvent.ShowUndoComplete(reminder))
            }
        }
    }

    fun onConfirmCompleteScope(scope: RepeatActionScope) {
        val reminder = dialogState.value.second ?: return
        viewModelScope.launch {
            completeReminderUseCase.markDone(reminder, scope)
            notificationHelper.cancelNotification(reminder)
        }
        dialogState.value = dialogState.value.copy(second = null)
    }

    fun onDismissCompleteScopeDialog() {
        dialogState.value = dialogState.value.copy(second = null)
    }

    fun onRequestDelete(reminder: Reminder) {
        dialogState.value = dialogState.value.copy(first = reminder)
    }

    fun onDismissDeleteDialog() {
        dialogState.value = dialogState.value.copy(first = null)
    }

    fun onConfirmDelete(scope: RepeatActionScope) {
        val reminder = dialogState.value.first ?: return
        viewModelScope.launch {
            deleteReminderUseCase(reminder, scope)
            notificationHelper.cancelNotification(reminder)
        }
        dialogState.value = dialogState.value.copy(first = null)
    }

    fun undoComplete(reminder: Reminder) {
        viewModelScope.launch { completeReminderUseCase.markPending(reminder) }
    }
}
