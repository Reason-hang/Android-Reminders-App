package com.reminder.local.ui.screen.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.local.data.datastore.SettingsDataStore
import com.reminder.local.data.repository.CategoryRepository
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.model.Category
import com.reminder.local.domain.model.AdvanceReminderType
import com.reminder.local.domain.model.AdvanceReminderUnit
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.usecase.AddReminderUseCase
import com.reminder.local.domain.usecase.DeleteReminderUseCase
import com.reminder.local.domain.usecase.EditReminderUseCase
import com.reminder.local.domain.usecase.ReminderContentValidator
import com.reminder.local.domain.usecase.SaveResult
import com.reminder.local.domain.usecase.EditResult
import com.reminder.local.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditReminderUiState(
    val id: Long = -1L,
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val title: String = "",
    val note: String = "",
    val triggerTime: Long = 0L,
    val categoryId: Long? = null,
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatEndDate: Long? = null,
    val advanceReminderType: AdvanceReminderType = AdvanceReminderType.NONE,
    val customAdvanceValue: Int = 1,
    val customAdvanceUnit: AdvanceReminderUnit = AdvanceReminderUnit.HOURS,
    val notifyVibrate: Boolean = true,
    val notifySound: Boolean = true,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val categories: List<Category> = emptyList(),
    val titleError: String? = null,
    val timeError: String? = null,
    val generalError: String? = null,
    val isSaving: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val saveSuccess: Boolean = false,
    val reactivated: Boolean = false,
    val pendingDeleteScope: Boolean = false,
    val deleted: Boolean = false
)

@HiltViewModel
class EditReminderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reminderRepository: ReminderRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsDataStore: SettingsDataStore,
    private val addReminderUseCase: AddReminderUseCase,
    private val editReminderUseCase: EditReminderUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase
) : ViewModel() {

    private val reminderId: Long = savedStateHandle.get<Long>(Routes.EDIT_ARG_ID) ?: -1L

    private val _uiState = MutableStateFlow(EditReminderUiState(id = reminderId, isNew = reminderId < 0))
    val uiState: StateFlow<EditReminderUiState> = _uiState.asStateFlow()
    private val editHistory = EditReminderHistory()

    init {
        viewModelScope.launch {
            categoryRepository.observeAll().collect { categories ->
                _uiState.value = _uiState.value.copy(categories = categories)
            }
        }
        viewModelScope.launch {
            if (reminderId >= 0) {
                val reminder = reminderRepository.getById(reminderId)
                if (reminder != null) {
                    _uiState.value = _uiState.value.copy(
                        title = reminder.title,
                        note = reminder.note ?: "",
                        triggerTime = reminder.triggerTime,
                        categoryId = reminder.categoryId,
                        repeatType = reminder.repeatType,
                        repeatEndDate = reminder.repeatEndDate,
                        advanceReminderType = reminder.advanceReminderType,
                        customAdvanceValue = reminder.customAdvanceValue,
                        customAdvanceUnit = reminder.customAdvanceUnit,
                        notifyVibrate = reminder.notifyVibrate,
                        notifySound = reminder.notifySound,
                        status = reminder.status,
                        loaded = true
                    )
                    editHistory.reset(_uiState.value.toEditReminderSnapshot())
                }
            } else {
                val defaults = settingsDataStore.settings.first()
                _uiState.value = _uiState.value.copy(
                    triggerTime = defaultInitialTime(),
                    notifyVibrate = defaults.defaultNotifyVibrate,
                    notifySound = defaults.defaultNotifySound,
                    loaded = true
                )
                editHistory.reset(_uiState.value.toEditReminderSnapshot())
            }
        }
    }

    fun onTitleChange(value: String) {
        if (value.length <= ReminderContentValidator.TITLE_MAX_LENGTH) {
            updateForm(EditReminderChangeKind.TITLE_TEXT) { it.copy(title = value, titleError = null) }
        }
    }

    fun onNoteChange(value: String) {
        if (value.length <= ReminderContentValidator.NOTE_MAX_LENGTH) {
            updateForm(EditReminderChangeKind.NOTE_TEXT) { it.copy(note = value) }
        }
    }

    fun onDateTimeSelected(millis: Long) {
        updateForm { it.copy(triggerTime = millis, timeError = null) }
    }

    fun onCategorySelected(categoryId: Long?) {
        updateForm { it.copy(categoryId = categoryId) }
    }

    fun onRepeatTypeSelected(type: RepeatType) {
        updateForm { state -> state.copy(
            repeatType = type,
            repeatEndDate = if (type == RepeatType.NONE) null else state.repeatEndDate
        ) }
    }

    fun onRepeatEndDateSelected(millis: Long?) {
        updateForm { it.copy(repeatEndDate = millis) }
    }

    fun onAdvanceReminderSelected(type: AdvanceReminderType) {
        updateForm { it.copy(advanceReminderType = type) }
    }

    fun onCustomAdvanceValueSelected(value: Int) {
        updateForm { it.copy(customAdvanceValue = value.coerceIn(1, 200)) }
    }

    fun onCustomAdvanceUnitSelected(unit: AdvanceReminderUnit) {
        updateForm { it.copy(customAdvanceUnit = unit) }
    }

    fun onNotifyVibrateToggle(enabled: Boolean) {
        updateForm { it.copy(notifyVibrate = enabled) }
    }

    fun onNotifySoundToggle(enabled: Boolean) {
        updateForm { it.copy(notifySound = enabled) }
    }

    fun undo() {
        if (_uiState.value.isSaving) return
        editHistory.undo()?.let(::applyEditReminderSnapshot)
    }

    fun redo() {
        if (_uiState.value.isSaving) return
        editHistory.redo()?.let(::applyEditReminderSnapshot)
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.value = state.copy(titleError = "标题不能为空")
            return
        }

        _uiState.value = state.copy(isSaving = true, generalError = null)

        viewModelScope.launch {
            val reminder = Reminder(
                id = if (state.isNew) 0L else state.id,
                title = state.title.trim(),
                note = state.note.trim().ifBlank { null },
                triggerTime = state.triggerTime,
                categoryId = state.categoryId,
                repeatType = state.repeatType,
                repeatEndDate = state.repeatEndDate,
                advanceReminderType = state.advanceReminderType,
                customAdvanceValue = state.customAdvanceValue,
                customAdvanceUnit = state.customAdvanceUnit,
                notifyVibrate = state.notifyVibrate,
                notifySound = state.notifySound,
                status = state.status
            )

            if (state.isNew) {
                when (val result = addReminderUseCase(reminder)) {
                    is SaveResult.Success -> {
                        editHistory.reset(state.toEditReminderSnapshot())
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            canUndo = false,
                            canRedo = false,
                            saveSuccess = true
                        )
                    }
                    is SaveResult.TimeAlreadyPassed -> _uiState.value =
                        _uiState.value.copy(isSaving = false, timeError = result.message)
                    is SaveResult.Failure -> _uiState.value =
                        _uiState.value.copy(isSaving = false, generalError = result.message)
                }
            } else {
                when (val result = editReminderUseCase(reminder)) {
                    is EditResult.Success -> {
                        editHistory.reset(state.toEditReminderSnapshot())
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            canUndo = false,
                            canRedo = false,
                            saveSuccess = true,
                            reactivated = result.reactivated
                        )
                    }
                    is EditResult.TimeAlreadyPassed -> _uiState.value =
                        _uiState.value.copy(isSaving = false, timeError = result.message)
                    is EditResult.Failure -> _uiState.value =
                        _uiState.value.copy(isSaving = false, generalError = result.message)
                }
            }
        }
    }

    fun requestDelete() {
        _uiState.value = _uiState.value.copy(pendingDeleteScope = true)
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(pendingDeleteScope = false)
    }

    fun confirmDelete(scope: RepeatActionScope) {
        val state = _uiState.value
        viewModelScope.launch {
            val reminder = reminderRepository.getById(state.id) ?: return@launch
            val deleted = deleteReminderUseCase(reminder, scope)
            _uiState.value = _uiState.value.copy(
                pendingDeleteScope = false,
                deleted = deleted,
                generalError = if (deleted) null else "删除失败，请重试"
            )
        }
    }

    private fun updateForm(
        changeKind: EditReminderChangeKind = EditReminderChangeKind.FORM,
        transform: (EditReminderUiState) -> EditReminderUiState
    ) {
        val current = _uiState.value
        val next = transform(current)
        if (current.toEditReminderSnapshot() == next.toEditReminderSnapshot()) return
        editHistory.record(next.toEditReminderSnapshot(), changeKind)
        _uiState.value = next.copy(canUndo = editHistory.canUndo, canRedo = editHistory.canRedo)
    }

    private fun applyEditReminderSnapshot(snapshot: EditReminderSnapshot) {
        _uiState.value = _uiState.value.copy(
            title = snapshot.title,
            note = snapshot.note,
            triggerTime = snapshot.triggerTime,
            categoryId = snapshot.categoryId,
            repeatType = snapshot.repeatType,
            repeatEndDate = snapshot.repeatEndDate,
            advanceReminderType = snapshot.advanceReminderType,
            customAdvanceValue = snapshot.customAdvanceValue,
            customAdvanceUnit = snapshot.customAdvanceUnit,
            notifyVibrate = snapshot.notifyVibrate,
            notifySound = snapshot.notifySound,
            titleError = null,
            timeError = null,
            generalError = null,
            canUndo = editHistory.canUndo,
            canRedo = editHistory.canRedo
        )
    }

    private fun EditReminderUiState.toEditReminderSnapshot() = EditReminderSnapshot(
        title = title,
        note = note,
        triggerTime = triggerTime,
        categoryId = categoryId,
        repeatType = repeatType,
        repeatEndDate = repeatEndDate,
        advanceReminderType = advanceReminderType,
        customAdvanceValue = customAdvanceValue,
        customAdvanceUnit = customAdvanceUnit,
        notifyVibrate = notifyVibrate,
        notifySound = notifySound
    )

    companion object {
        /** 新增提醒默认时间：当前时间往后取整到下一个整点再加一小时，避免默认就是"过去时间"。 */
        fun defaultInitialTime(): Long {
            val now = java.time.LocalDateTime.now()
            return now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}
