package com.reminder.local.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reminder.local.data.datastore.AppSettings
import com.reminder.local.data.datastore.SettingsDataStore
import com.reminder.local.util.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val exactAlarmGranted: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val permissionRefreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsDataStore.settings,
        permissionRefreshTrigger
    ) { settings, _ ->
        SettingsUiState(
            settings = settings,
            exactAlarmGranted = PermissionUtils.canScheduleExactAlarms(context)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun refreshPermissionStatus() {
        permissionRefreshTrigger.value += 1
    }

    fun setDefaultVibrate(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDefaultVibrate(enabled) }
    }

    fun setDefaultSound(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDefaultSound(enabled) }
    }
}
