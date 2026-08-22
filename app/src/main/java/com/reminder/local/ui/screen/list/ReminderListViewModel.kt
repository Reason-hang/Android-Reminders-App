package com.reminder.local.ui.screen.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.local.data.datastore.SettingsDataStore
import com.reminder.local.data.repository.CategoryRepository
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.model.ALL_CATEGORY_FILTER_ID
import com.reminder.local.domain.model.Category
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.ReminderListSortMode
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.model.UNCATEGORIZED_FILTER_ID
import com.reminder.local.domain.usecase.CompleteReminderUseCase
import com.reminder.local.domain.usecase.DeleteReminderUseCase
import com.reminder.local.domain.usecase.ReorderRemindersUseCase
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
    val overlayGranted: Boolean = false,
    val pendingDeleteReminder: Reminder? = null,
    val pendingCompleteScopeReminder: Reminder? = null,
    val pendingBatchDelete: List<Reminder> = emptyList(),
    val isManaging: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val sortMode: ReminderListSortMode = ReminderListSortMode.TIME
)

sealed interface ListEvent {
    data class ShowUndoComplete(val reminder: Reminder) : ListEvent
    data class ShowError(val message: String) : ListEvent
    data class ShowMessage(val message: String) : ListEvent
}

private data class DialogState(
    val deleteReminder: Reminder? = null,
    val completeReminder: Reminder? = null,
    val batchDelete: List<Reminder> = emptyList()
)

private data class ManagementState(
    val enabled: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
)

private data class ListInputs(
    val reminders: List<Reminder>,
    val categories: List<Category>,
    val selectedCategoryId: Long,
    val dialogs: DialogState,
    val permissions: PermissionStatus,
    val management: ManagementState
)

private data class PermissionStatus(
    val exactAlarmGranted: Boolean,
    val overlayGranted: Boolean
)

private data class ListControls(
    val dialogs: DialogState,
    val permissions: PermissionStatus,
    val management: ManagementState
)

internal fun sortPendingReminders(
    reminders: List<Reminder>,
    mode: ReminderListSortMode
): List<Reminder> = when (mode) {
    ReminderListSortMode.TIME -> reminders.sortedWith(
        compareBy<Reminder> { it.effectiveTime }
            .thenBy { it.manualSortOrder }
            .thenBy { it.createdAt }
            .thenBy { it.id }
    )
    ReminderListSortMode.MANUAL -> reminders.sortedWith(
        compareBy<Reminder> { it.manualSortOrder }
            .thenBy { it.createdAt }
            .thenBy { it.id }
    )
    ReminderListSortMode.CREATED -> reminders.sortedWith(
        compareByDescending<Reminder> { it.createdAt }
            .thenByDescending { it.id }
    )
}

@HiltViewModel
class ReminderListViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val completeReminderUseCase: CompleteReminderUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val reorderRemindersUseCase: ReorderRemindersUseCase,
    private val notificationHelper: NotificationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow(ALL_CATEGORY_FILTER_ID)
    private val dialogState = MutableStateFlow(DialogState())
    private val permissionStatus = MutableStateFlow(currentPermissionStatus())
    private val managementState = MutableStateFlow(ManagementState())

    private val _events = MutableSharedFlow<ListEvent>()
    val events = _events.asSharedFlow()

    private val controls = combine(dialogState, permissionStatus, managementState) {
            dialogs, permissions, management ->
        ListControls(dialogs, permissions, management)
    }

    private val inputs = combine(
        reminderRepository.observeAll(), categoryRepository.observeAll(), selectedCategoryId, controls
    ) { reminders, categories, selectedId, controls ->
        ListInputs(reminders, categories, selectedId, controls.dialogs, controls.permissions, controls.management)
    }

    val uiState: StateFlow<ReminderListUiState> = combine(inputs, settingsDataStore.settings) { input, settings ->
        val filtered = when (input.selectedCategoryId) {
            ALL_CATEGORY_FILTER_ID -> input.reminders
            UNCATEGORIZED_FILTER_ID -> input.reminders.filter { it.categoryId == null }
            else -> input.reminders.filter { it.categoryId == input.selectedCategoryId }
        }
        val pendingBase = filtered.filter { it.status != ReminderStatus.DONE }
        val pending = sortPendingReminders(pendingBase, settings.reminderListSortMode)
        ReminderListUiState(
            pending = pending,
            done = filtered.filter { it.status == ReminderStatus.DONE }
                .sortedByDescending { it.completedAt ?: it.updatedAt },
            categories = input.categories,
            categoryMap = input.categories.associateBy { it.id },
            selectedCategoryId = input.selectedCategoryId,
            exactAlarmGranted = input.permissions.exactAlarmGranted,
            overlayGranted = input.permissions.overlayGranted,
            pendingDeleteReminder = input.dialogs.deleteReminder,
            pendingCompleteScopeReminder = input.dialogs.completeReminder,
            pendingBatchDelete = input.dialogs.batchDelete,
            isManaging = input.management.enabled,
            selectedIds = input.management.selectedIds,
            sortMode = settings.reminderListSortMode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderListUiState())

    fun selectCategory(id: Long) {
        if (managementState.value.enabled) return
        selectedCategoryId.value = id
    }

    fun refreshExactAlarmPermission() {
        permissionStatus.value = currentPermissionStatus()
    }

    fun selectSortMode(mode: ReminderListSortMode) {
        viewModelScope.launch { settingsDataStore.setReminderListSortMode(mode) }
    }

    private fun currentPermissionStatus() = PermissionStatus(
        exactAlarmGranted = PermissionUtils.canScheduleExactAlarms(context),
        overlayGranted = PermissionUtils.canDrawOverlays(context)
    )

    fun enterManagement() {
        if (selectedCategoryId.value != ALL_CATEGORY_FILTER_ID) {
            viewModelScope.launch { _events.emit(ListEvent.ShowError("请先切换到“全部”后整理全局顺序")) }
            return
        }
        managementState.value = ManagementState(enabled = true)
    }

    fun exitManagement() {
        managementState.value = ManagementState()
    }

    fun toggleManagedSelection(id: Long) {
        val selected = managementState.value.selectedIds.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        managementState.value = managementState.value.copy(selectedIds = selected)
    }

    fun selectAllPending() {
        val current = uiState.value.pending.map { it.id }.toSet()
        managementState.value = managementState.value.copy(
            selectedIds = if (managementState.value.selectedIds.size == current.size) emptySet() else current
        )
    }

    fun moveSelectedToTop() = moveSelected(toTop = true)

    fun moveSelectedToBottom() = moveSelected(toTop = false)

    private fun moveSelected(toTop: Boolean) {
        val current = uiState.value.pending
        val selected = managementState.value.selectedIds
        if (selected.isEmpty()) return
        val chosen = current.filter { it.id in selected }
        val rest = current.filterNot { it.id in selected }
        persistOrder(if (toTop) chosen + rest else rest + chosen)
    }

    fun persistOrder(ordered: List<Reminder>) {
        if (ordered.isEmpty()) return
        viewModelScope.launch {
            if (reorderRemindersUseCase(ordered.map { it.id })) {
                settingsDataStore.setReminderListSortMode(ReminderListSortMode.MANUAL)
            } else {
                _events.emit(ListEvent.ShowError("保存顺序失败，请重试"))
            }
        }
    }

    /** 点击勾选框或右滑：非重复直接完成；重复提醒需要明确“仅本次/全部”。 */
    fun onToggleComplete(reminder: Reminder) {
        if (reminder.status == ReminderStatus.DONE) {
            viewModelScope.launch {
                if (!completeReminderUseCase.markPending(reminder)) {
                    _events.emit(ListEvent.ShowError("撤销完成失败，请重试"))
                }
            }
            return
        }
        if (reminder.isRepeating) {
            dialogState.value = dialogState.value.copy(completeReminder = reminder)
        } else {
            viewModelScope.launch {
                if (completeReminderUseCase.markDone(reminder, RepeatActionScope.ALL)) {
                    notificationHelper.cancelNotification(reminder)
                    _events.emit(ListEvent.ShowUndoComplete(reminder))
                } else {
                    _events.emit(ListEvent.ShowError("标为完成失败，请重试"))
                }
            }
        }
    }

    fun onConfirmCompleteScope(scope: RepeatActionScope) {
        val reminder = dialogState.value.completeReminder ?: return
        viewModelScope.launch {
            if (completeReminderUseCase.markDone(reminder, scope)) {
                notificationHelper.cancelNotification(reminder)
            } else {
                _events.emit(ListEvent.ShowError("完成重复提醒失败，请重试"))
            }
        }
        dialogState.value = dialogState.value.copy(completeReminder = null)
    }

    fun onDismissCompleteScopeDialog() {
        dialogState.value = dialogState.value.copy(completeReminder = null)
    }

    fun onRequestDelete(reminder: Reminder) {
        dialogState.value = dialogState.value.copy(deleteReminder = reminder)
    }

    fun onRequestBatchDelete() {
        val selected = uiState.value.pending.filter { it.id in managementState.value.selectedIds }
        if (selected.isNotEmpty()) dialogState.value = dialogState.value.copy(batchDelete = selected)
    }

    fun onDismissDeleteDialog() {
        dialogState.value = dialogState.value.copy(deleteReminder = null, batchDelete = emptyList())
    }

    fun onConfirmDelete(scope: RepeatActionScope) {
        val reminder = dialogState.value.deleteReminder ?: return
        deleteReminders(listOf(reminder), scope)
        dialogState.value = dialogState.value.copy(deleteReminder = null)
    }

    fun onConfirmBatchDelete(scope: RepeatActionScope) {
        val reminders = dialogState.value.batchDelete
        deleteReminders(reminders, scope)
        dialogState.value = dialogState.value.copy(batchDelete = emptyList())
    }

    private fun deleteReminders(reminders: List<Reminder>, scope: RepeatActionScope) {
        viewModelScope.launch {
            val failed = reminders.count { reminder ->
                val deleted = deleteReminderUseCase(reminder, scope)
                if (deleted) notificationHelper.cancelNotification(reminder)
                !deleted
            }
            managementState.value = managementState.value.copy(selectedIds = emptySet())
            when {
                failed == 0 && reminders.size > 1 -> _events.emit(ListEvent.ShowMessage("已移入回收站 ${reminders.size} 条"))
                failed > 0 -> _events.emit(ListEvent.ShowError("${reminders.size - failed} 条已移入回收站，$failed 条失败"))
            }
        }
    }

    fun undoComplete(reminder: Reminder) {
        viewModelScope.launch {
            if (!completeReminderUseCase.markPending(reminder)) {
                _events.emit(ListEvent.ShowError("撤销完成失败，请重试"))
            }
        }
    }
}
