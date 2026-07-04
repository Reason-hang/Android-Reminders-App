package com.reminder.local.ui.screen.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.reminder.local.domain.model.Priority
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.ui.components.ConfirmDialog
import com.reminder.local.ui.components.RepeatScopeDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReminderScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditReminderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val zone = ZoneId.systemDefault()

    LaunchedEffect(uiState.saveSuccess, uiState.deleted) {
        if (uiState.saveSuccess || uiState.deleted) onSaved()
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRepeatEndPicker by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var repeatMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNew) "新增提醒" else "编辑提醒") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!uiState.isNew) {
                        IconButton(onClick = viewModel::requestDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("标题") },
                isError = uiState.titleError != null,
                supportingText = {
                    Text(
                        uiState.titleError ?: "${uiState.title.length}/$TITLE_MAX_LENGTH"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("备注（可选）") },
                supportingText = { Text("${uiState.note.length}/$NOTE_MAX_LENGTH") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("提醒时间", style = MaterialTheme.typography.labelSmall)
            Row(modifier = Modifier.padding(top = 6.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(formatDate(uiState.triggerTime, zone))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(formatTime(uiState.triggerTime, zone))
                }
            }
            uiState.timeError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("分类", style = MaterialTheme.typography.labelSmall)
            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = it },
                modifier = Modifier.padding(top = 6.dp)
            ) {
                OutlinedTextField(
                    value = uiState.categories.firstOrNull { it.id == uiState.categoryId }?.name ?: "未分类",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("未分类") },
                        onClick = {
                            viewModel.onCategorySelected(null)
                            categoryMenuExpanded = false
                        }
                    )
                    uiState.categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                viewModel.onCategorySelected(category.id)
                                categoryMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("优先级", style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Priority.entries.forEach { priority ->
                    FilterChip(
                        selected = uiState.priority == priority,
                        onClick = { viewModel.onPrioritySelected(priority) },
                        label = { Text(priority.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("重复", style = MaterialTheme.typography.labelSmall)
            ExposedDropdownMenuBox(
                expanded = repeatMenuExpanded,
                onExpandedChange = { repeatMenuExpanded = it },
                modifier = Modifier.padding(top = 6.dp)
            ) {
                OutlinedTextField(
                    value = uiState.repeatType.label,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = repeatMenuExpanded,
                    onDismissRequest = { repeatMenuExpanded = false }
                ) {
                    RepeatType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                viewModel.onRepeatTypeSelected(type)
                                repeatMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (uiState.repeatType != RepeatType.NONE) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showRepeatEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        uiState.repeatEndDate?.let { "重复截止：${formatDate(it, zone)}" } ?: "设置重复截止日期（可选，不设置则一直重复）"
                    )
                }
                if (uiState.repeatEndDate != null) {
                    TextButton(onClick = { viewModel.onRepeatEndDateSelected(null) }) {
                        Text("清除截止日期")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("响铃提醒")
                Switch(checked = uiState.notifySound, onCheckedChange = viewModel::onNotifySoundToggle)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("震动提醒")
                Switch(checked = uiState.notifyVibrate, onCheckedChange = viewModel::onNotifyVibrateToggle)
            }

            uiState.generalError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::save,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isSaving) "保存中..." else "保存")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startOfDayUtcMillis(uiState.triggerTime, zone)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { pickedUtcMillis ->
                        val newDate = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val oldTime = Instant.ofEpochMilli(uiState.triggerTime).atZone(zone).toLocalTime()
                        val combined = newDate.atTime(oldTime).atZone(zone).toInstant().toEpochMilli()
                        viewModel.onDateTimeSelected(combined)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val current = Instant.ofEpochMilli(uiState.triggerTime).atZone(zone).toLocalTime()
        val timePickerState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
            ) {
                TimePicker(state = timePickerState)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showTimePicker = false }) { Text("取消") }
                    TextButton(onClick = {
                        val oldDate = Instant.ofEpochMilli(uiState.triggerTime).atZone(zone).toLocalDate()
                        val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        val combined = oldDate.atTime(newTime).atZone(zone).toInstant().toEpochMilli()
                        viewModel.onDateTimeSelected(combined)
                        showTimePicker = false
                    }) { Text("确定") }
                }
            }
        }
    }

    if (showRepeatEndPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startOfDayUtcMillis(
                uiState.repeatEndDate ?: uiState.triggerTime, zone
            )
        )
        DatePickerDialog(
            onDismissRequest = { showRepeatEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { pickedUtcMillis ->
                        val newDate = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val endOfDay = newDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
                        viewModel.onRepeatEndDateSelected(endOfDay)
                    }
                    showRepeatEndPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRepeatEndPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (uiState.pendingDeleteScope) {
        if (uiState.repeatType != RepeatType.NONE) {
            RepeatScopeDialog(
                title = "删除重复提醒",
                onOnce = { viewModel.confirmDelete(RepeatActionScope.ONCE) },
                onAll = { viewModel.confirmDelete(RepeatActionScope.ALL) },
                onDismiss = viewModel::dismissDeleteDialog
            )
        } else {
            ConfirmDialog(
                title = "删除提醒",
                text = "确定要删除「${uiState.title}」吗？此操作无法撤销。",
                confirmLabel = "删除",
                onConfirm = { viewModel.confirmDelete(RepeatActionScope.ALL) },
                onDismiss = viewModel::dismissDeleteDialog
            )
        }
    }
}

private fun formatDate(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))

private fun formatTime(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

/** DatePicker 内部用 UTC 存储所选日期，这里把本地日期转换成"UTC 当天零点"喂给它做初始值。 */
private fun startOfDayUtcMillis(localMillis: Long, zone: ZoneId): Long {
    val localDate = Instant.ofEpochMilli(localMillis).atZone(zone).toLocalDate()
    return localDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
}
