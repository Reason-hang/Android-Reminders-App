package com.reminder.local.ui.screen.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.reminder.local.domain.model.AdvanceReminderType
import com.reminder.local.domain.model.AdvanceReminderUnit
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.domain.usecase.ReminderContentValidator
import com.reminder.local.ui.components.ConfirmDialog
import com.reminder.local.ui.components.RepeatScopeDialog
import com.reminder.local.ui.components.WheelItemPosition
import com.reminder.local.ui.components.WheelPickerSelection
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReminderScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditReminderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val zone = ZoneId.systemDefault()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveSuccess, uiState.deleted) {
        if (uiState.saveSuccess || uiState.deleted) onSaved()
    }

    // 2026-07 迭代修复：generalError 之前只在页面底部展示一行文字，如果用户是通过
    // 顶部栏的"保存"触发保存并失败，页面停在顶部就完全看不到这行提示。
    // 改用 Snackbar 后，无论当前滚动到哪里都能看到失败原因。
    LaunchedEffect(uiState.generalError) {
        uiState.generalError?.let { snackbarHostState.showSnackbar(it) }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRepeatEndEditor by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var repeatMenuExpanded by remember { mutableStateOf(false) }
    var advanceMenuExpanded by remember { mutableStateOf(false) }
    var showCustomAdvancePicker by remember { mutableStateOf(false) }

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
                    // 2026-07 迭代修复（问题3）：原来页面底部还有一个同样触发 viewModel::save
                    // 的全宽按钮，用户必须滚动到底才能点到，这里已删除那个重复按钮
                    // （见下方表单末尾的注释）。现在"保存"只在顶部栏这一个入口，
                    // 打开页面立刻可见，不需要滚动；同时把原来不醒目的纯文字按钮
                    // 换成带图标的实心按钮，避免和旁边的删除图标一起被忽略。
                    Button(
                        onClick = viewModel::save,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (!uiState.isSaving) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(if (uiState.isSaving) "保存中" else "保存")
                    }
                    if (!uiState.isNew) {
                        IconButton(onClick = viewModel::requestDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                        uiState.titleError ?: "${uiState.title.length}/${ReminderContentValidator.TITLE_MAX_LENGTH}"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("备注（可选）") },
                supportingText = { Text("${uiState.note.length}/${ReminderContentValidator.NOTE_MAX_LENGTH}") },
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

            Text("提前提醒", style = MaterialTheme.typography.labelSmall)
            ExposedDropdownMenuBox(
                expanded = advanceMenuExpanded,
                onExpandedChange = { advanceMenuExpanded = it },
                modifier = Modifier.padding(top = 6.dp)
            ) {
                OutlinedTextField(
                    value = uiState.advanceReminderType.label,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = advanceMenuExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = advanceMenuExpanded,
                    onDismissRequest = { advanceMenuExpanded = false }
                ) {
                    AdvanceReminderType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                viewModel.onAdvanceReminderSelected(type)
                                if (type == AdvanceReminderType.CUSTOM) {
                                    showCustomAdvancePicker = true
                                }
                                advanceMenuExpanded = false
                            }
                        )
                    }
                }
            }
            if (uiState.advanceReminderType == AdvanceReminderType.CUSTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showCustomAdvancePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("自定义：${formatCustomAdvance(uiState.customAdvanceValue, uiState.customAdvanceUnit)}")
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
                OutlinedButton(onClick = { showRepeatEndEditor = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        uiState.repeatEndDate?.let { "结束重复：${formatDate(it, zone)}" } ?: "结束重复：永不"
                    )
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

            // 2026-07 迭代修复（问题3）：原来这里还有一个 fillMaxWidth 的"保存"按钮，
            // 和顶部栏的保存按钮功能完全重复，用户必须滚动到表单最底部才能点到。
            // 现在保存的唯一入口是顶部栏（见上方 TopAppBar.actions），打开页面就能看到，
            // 不需要滚动。generalError 也已经改成顶部栏下方的 Snackbar 展示（见页面顶部），
            // 这里不再需要单独展示。

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
        WheelTimePickerDialog(
            initialHour = current.hour,
            initialMinute = current.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                val oldDate = Instant.ofEpochMilli(uiState.triggerTime).atZone(zone).toLocalDate()
                val newTime = LocalTime.of(hour, minute)
                val combined = oldDate.atTime(newTime).atZone(zone).toInstant().toEpochMilli()
                viewModel.onDateTimeSelected(combined)
                showTimePicker = false
            }
        )
    }

    if (showRepeatEndEditor) {
        RepeatEndEditorDialog(
            initialEndDate = uiState.repeatEndDate,
            fallbackDate = uiState.triggerTime,
            zone = zone,
            onDismiss = { showRepeatEndEditor = false },
            onConfirm = {
                viewModel.onRepeatEndDateSelected(it)
                showRepeatEndEditor = false
            }
        )
    }

    if (showCustomAdvancePicker) {
        CustomAdvancePickerDialog(
            initialValue = uiState.customAdvanceValue,
            initialUnit = uiState.customAdvanceUnit,
            onDismiss = { showCustomAdvancePicker = false },
            onConfirm = { value, unit ->
                viewModel.onCustomAdvanceValueSelected(value)
                viewModel.onCustomAdvanceUnitSelected(unit)
                showCustomAdvancePicker = false
            }
        )
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

@Composable
private fun CustomAdvancePickerDialog(
    initialValue: Int,
    initialUnit: AdvanceReminderUnit,
    onDismiss: () -> Unit,
    onConfirm: (value: Int, unit: AdvanceReminderUnit) -> Unit
) {
    var selectedValue by remember { mutableStateOf(initialValue.coerceIn(1, 200)) }
    var selectedUnit by remember { mutableStateOf(initialUnit) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(24.dp)
        ) {
            Text("提前提醒", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${selectedValue}${selectedUnit.label}前",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    WheelNumberColumn(
                        values = (1..200).toList(),
                        selected = selectedValue,
                        onSelected = { selectedValue = it },
                        modifier = Modifier.width(112.dp)
                    )
                    WheelTextColumn(
                        values = AdvanceReminderUnit.entries,
                        selected = selectedUnit,
                        label = { it.label },
                        onSelected = { selectedUnit = it },
                        modifier = Modifier.width(132.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = { onConfirm(selectedValue, selectedUnit) }) { Text("确定") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WheelTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    var selectedHour by remember { mutableStateOf(initialHour) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(24.dp)
        ) {
            Text("时间", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    WheelNumberColumn(
                        values = (0..23).toList(),
                        selected = selectedHour,
                        onSelected = { selectedHour = it },
                        modifier = Modifier.width(104.dp)
                    )
                    Text(
                        ":",
                        modifier = Modifier
                            .height(280.dp)
                            .padding(top = 112.dp),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    WheelNumberColumn(
                        values = (0..59).toList(),
                        selected = selectedMinute,
                        onSelected = { selectedMinute = it },
                        modifier = Modifier.width(104.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = { onConfirm(selectedHour, selectedMinute) }) { Text("确定") }
            }
        }
    }
}

@Composable
private fun WheelNumberColumn(
    values: List<Int>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 56.dp
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val coroutineScope = rememberCoroutineScope()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val centeredIndex by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            WheelPickerSelection.nearestIndex(
                viewportStartOffset = layoutInfo.viewportStartOffset,
                viewportEndOffset = layoutInfo.viewportEndOffset,
                items = layoutInfo.visibleItemsInfo.map { item ->
                    WheelItemPosition(item.index, item.offset, item.size)
                }
            )?.coerceIn(values.indices) ?: selectedIndex
        }
    }

    LaunchedEffect(selected) {
        if (!listState.isScrollInProgress && centeredIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(centeredIndex) {
        onSelected(values[centeredIndex])
    }

    LazyColumn(
        state = listState,
        modifier = modifier.height(itemHeight * 5),
        contentPadding = PaddingValues(vertical = itemHeight * 2),
        flingBehavior = snapFlingBehavior,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(values.size) { index ->
            val value = values[index]
            val isSelected = index == centeredIndex
            Text(
                text = "%02d".format(value),
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .clickable {
                        onSelected(value)
                        coroutineScope.launch { listState.animateScrollToItem(index) }
                    }
                    .padding(top = 8.dp)
                    .alpha(if (isSelected) 1f else 0.42f),
                textAlign = TextAlign.Center,
                fontSize = if (isSelected) 32.sp else 26.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun <T> WheelTextColumn(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 56.dp
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val coroutineScope = rememberCoroutineScope()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val centeredIndex by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            WheelPickerSelection.nearestIndex(
                viewportStartOffset = layoutInfo.viewportStartOffset,
                viewportEndOffset = layoutInfo.viewportEndOffset,
                items = layoutInfo.visibleItemsInfo.map { item ->
                    WheelItemPosition(item.index, item.offset, item.size)
                }
            )?.coerceIn(values.indices) ?: selectedIndex
        }
    }

    LaunchedEffect(selected) {
        if (!listState.isScrollInProgress && centeredIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    LaunchedEffect(centeredIndex) {
        onSelected(values[centeredIndex])
    }

    LazyColumn(
        state = listState,
        modifier = modifier.height(itemHeight * 5),
        contentPadding = PaddingValues(vertical = itemHeight * 2),
        flingBehavior = snapFlingBehavior,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(values.size) { index ->
            val value = values[index]
            val isSelected = index == centeredIndex
            Text(
                text = label(value),
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth()
                    .clickable {
                        onSelected(value)
                        coroutineScope.launch { listState.animateScrollToItem(index) }
                    }
                    .padding(top = 8.dp)
                    .alpha(if (isSelected) 1f else 0.42f),
                textAlign = TextAlign.Center,
                fontSize = if (isSelected) 32.sp else 26.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatEndEditorDialog(
    initialEndDate: Long?,
    fallbackDate: Long,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    var useEndDate by remember { mutableStateOf(initialEndDate != null) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = startOfDayUtcMillis(initialEndDate ?: fallbackDate, zone)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("结束重复") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            if (!useEndDate) {
                                onConfirm(null)
                            } else {
                                val selected = datePickerState.selectedDateMillis
                                if (selected != null) {
                                    val newDate = Instant.ofEpochMilli(selected)
                                        .atZone(ZoneId.of("UTC"))
                                        .toLocalDate()
                                    onConfirm(newDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli())
                                }
                            }
                        }) { Text("完成") }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                EndRepeatOptionRow(
                    text = "一直重复",
                    selected = !useEndDate,
                    onClick = { useEndDate = false }
                )
                EndRepeatOptionRow(
                    text = "结束重复日期",
                    selected = useEndDate,
                    onClick = { useEndDate = true }
                )
                if (useEndDate) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DatePicker(state = datePickerState)
                }
            }
        }
    }
}

@Composable
private fun EndRepeatOptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (selected) {
            Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp)
        }
    }
}

private fun formatDate(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))

private fun formatTime(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun formatCustomAdvance(value: Int, unit: AdvanceReminderUnit): String =
    "${value.coerceIn(1, 200)}${unit.label}前"

/** DatePicker 内部用 UTC 存储所选日期，这里把本地日期转换成"UTC 当天零点"喂给它做初始值。 */
private fun startOfDayUtcMillis(localMillis: Long, zone: ZoneId): Long {
    val localDate = Instant.ofEpochMilli(localMillis).atZone(zone).toLocalDate()
    return localDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
}
