package com.reminder.local.ui.screen.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.local.data.datastore.SettingsDataStore
import com.reminder.local.data.repository.CategoryRepository
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.model.Category
import com.reminder.local.domain.model.Priority
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.usecase.AddReminderUseCase
import com.reminder.local.domain.usecase.DeleteReminderUseCase
import com.reminder.local.domain.usecase.EditReminderUseCase
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

const val TITLE_MAX_LENGTH = 50
const val NOTE_MAX_LENGTH = 200

data class EditReminderUiState(
    val id: Long = -1L,
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val title: String = "",
    val note: String = "",
    val triggerTime: Long = 0L,
    val categoryId: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatEndDate: Long? = null,
    val notifyVibrate: Boolean = true,
    val notifySound: Boolean = true,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val categories: List<Category> = emptyList(),
    val titleError: String? = null,
    val timeError: String? = null,
    val generalError: String? = null,
    val isSaving: Boolean = false,
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
                        priority = reminder.priority,
                        repeatType = reminder.repeatType,
                        repeatEndDate = reminder.repeatEndDate,
                        notifyVibrate = reminder.notifyVibrate,
                        notifySound = reminder.notifySound,
                        status = reminder.status,
                        loaded = true
                    )
                }
            } else {
                val defaults = settingsDataStore.settings.first()
                _uiState.value = _uiState.value.copy(
                    triggerTime = defaultInitialTime(),
                    notifyVibrate = defaults.defaultNotifyVibrate,
                    notifySound = defaults.defaultNotifySound,
                    loaded = true
                )
            }
        }
    }

    fun onTitleChange(value: String) {
        if (value.length <= TITLE_MAX_LENGTH) {
            _uiState.value = _uiState.value.copy(title = value, titleError = null)
        }
    }

    fun onNoteChange(value: String) {
        if (value.length <= NOTE_MAX_LENGTH) {
            _uiState.value = _uiState.value.copy(note = value)
        }
    }

    fun onDateTimeSelected(millis: Long) {
        _uiState.value = _uiState.value.copy(triggerTime = millis, timeError = null)
    }

    fun onCategorySelected(categoryId: Long?) {
        _uiState.value = _uiState.value.copy(categoryId = categoryId)
    }

    fun onPrioritySelected(priority: Priority) {
        _uiState.value = _uiState.value.copy(priority = priority)
    }

    fun onRepeatTypeSelected(type: RepeatType) {
        _uiState.value = _uiState.value.copy(
            repeatType = type,
            repeatEndDate = if (type == RepeatType.NONE) null else _uiState.value.repeatEndDate
        )
    }

    fun onRepeatEndDateSelected(millis: Long?) {
        _uiState.value = _uiState.value.copy(repeatEndDate = millis)
    }

    fun onNotifyVibrateToggle(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notifyVibrate = enabled)
    }

    fun onNotifySoundToggle(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notifySound = enabled)
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
                priority = state.priority,
                repeatType = state.repeatType,
                repeatEndDate = state.repeatEndDate,
                notifyVibrate = state.notifyVibrate,
                notifySound = state.notifySound,
                status = state.status
            )

            if (state.isNew) {
                when (val result = addReminderUseCase(reminder)) {
                    is SaveResult.Success -> _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
                    is SaveResult.TimeAlreadyPassed -> _uiState.value =
                        _uiState.value.copy(isSaving = false, timeError = result.message)
                    is SaveResult.Failure -> _uiState.value =
                        _uiState.value.copy(isSaving = false, generalError = result.message)
                }
            } else {
                when (val result = editReminderUseCase(reminder)) {
                    is EditResult.Success -> _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveSuccess = true,
                        reactivated = result.reactivated
                    )
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
            deleteReminderUseCase(reminder, scope)
            _uiState.value = _uiState.value.copy(pendingDeleteScope = false, deleted = true)
        }
    }

    companion object {
        /** 新增提醒默认时间：当前时间往后取整到下一个整点再加一小时，避免默认就是"过去时间"。 */
        fun defaultInitialTime(): Long {
            val now = java.time.LocalDateTime.now()
            return now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}
