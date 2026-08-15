package com.reminder.local.ui.screen.list

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reminder.local.R
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.ui.components.CategoryFilterRow
import com.reminder.local.ui.components.ConfirmDialog
import com.reminder.local.ui.components.EmptyState
import com.reminder.local.ui.components.PermissionBanner
import com.reminder.local.ui.components.ReminderListItem
import com.reminder.local.ui.components.RepeatScopeDialog
import com.reminder.local.util.PermissionUtils
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    onAddClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onCategoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeletedClick: () -> Unit,
    viewModel: ReminderListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner) {
        viewModel.refreshExactAlarmPermission()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshExactAlarmPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ListEvent.ShowUndoComplete -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "已完成「${event.reminder.title}」",
                        actionLabel = "撤销"
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.undoComplete(event.reminder)
                    }
                }
                is ListEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is ListEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isManaging) "整理提醒（已选 ${uiState.selectedIds.size}）"
                        else stringResource(R.string.app_name)
                    )
                },
                actions = {
                    if (uiState.isManaging) {
                        TextButton(onClick = viewModel::selectAllPending) {
                            Text(if (uiState.selectedIds.size == uiState.pending.size) "取消全选" else "全选")
                        }
                        TextButton(onClick = viewModel::exitManagement) { Text("完成") }
                    } else {
                        TextButton(onClick = viewModel::enterManagement) { Text("整理") }
                        IconButton(onClick = onDeletedClick) {
                            Icon(Icons.Filled.RestoreFromTrash, contentDescription = "已删除")
                        }
                        IconButton(onClick = onCategoryClick) {
                            Icon(Icons.Filled.Category, contentDescription = "分类管理")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!uiState.isManaging) {
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Filled.Add, contentDescription = "添加提醒")
                }
            }
        },
        bottomBar = {
            if (uiState.isManaging && uiState.selectedIds.isNotEmpty()) {
                androidx.compose.material3.BottomAppBar {
                    TextButton(onClick = viewModel::moveSelectedToTop, modifier = Modifier.weight(1f)) {
                        Text("置顶")
                    }
                    TextButton(onClick = viewModel::moveSelectedToBottom, modifier = Modifier.weight(1f)) {
                        Text("置底")
                    }
                    TextButton(onClick = viewModel::onRequestBatchDelete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("删除（${uiState.selectedIds.size}）")
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    action = {
                        data.visuals.actionLabel?.let { label ->
                            TextButton(onClick = { data.performAction() }) { Text(label) }
                        }
                    }
                ) { Text(data.visuals.message) }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (!uiState.isManaging) {
                if (!uiState.exactAlarmGranted) {
                    PermissionBanner(
                        text = "精确提醒权限未开启，提醒可能无法准时触发",
                        onActionClick = { context.startActivity(PermissionUtils.exactAlarmSettingsIntent(context)) },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
                if (!uiState.overlayGranted) {
                    PermissionBanner(
                        text = "解锁强提醒页未开启，解锁使用手机时只会显示系统横幅",
                        onActionClick = {
                            runCatching { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) }
                                .onFailure { context.startActivity(PermissionUtils.appDetailsSettingsIntent(context)) }
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
                CategoryFilterRow(
                    categories = uiState.categories,
                    selectedId = uiState.selectedCategoryId,
                    onSelect = viewModel::selectCategory,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }

            if (uiState.pending.isEmpty() && (!uiState.isManaging && uiState.done.isEmpty())) {
                EmptyState(text = "还没有提醒事项\n点击右下角 + 添加一个吧", modifier = Modifier.fillMaxSize())
            } else if (uiState.isManaging) {
                ReorderablePendingList(
                    reminders = uiState.pending,
                    categories = uiState.categoryMap,
                    selectedIds = uiState.selectedIds,
                    onToggleSelection = viewModel::toggleManagedSelection,
                    onPersistOrder = viewModel::persistOrder
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    if (uiState.pending.isNotEmpty()) {
                        item { SectionHeader("未完成 (${uiState.pending.size})") }
                        items(uiState.pending, key = { it.id }) { reminder ->
                            ReminderListItem(
                                reminder = reminder,
                                category = reminder.categoryId?.let { uiState.categoryMap[it] },
                                onClick = { onEditClick(reminder.id) },
                                onToggleComplete = { viewModel.onToggleComplete(reminder) },
                                onSwipeComplete = { viewModel.onToggleComplete(reminder) },
                                onRequestDelete = { viewModel.onRequestDelete(reminder) }
                            )
                        }
                    }
                    if (uiState.done.isNotEmpty()) {
                        item { SectionHeader("已完成 (${uiState.done.size})") }
                        items(uiState.done, key = { it.id }) { reminder ->
                            ReminderListItem(
                                reminder = reminder,
                                category = reminder.categoryId?.let { uiState.categoryMap[it] },
                                onClick = { onEditClick(reminder.id) },
                                onToggleComplete = { viewModel.onToggleComplete(reminder) },
                                onSwipeComplete = { viewModel.onToggleComplete(reminder) },
                                onRequestDelete = { viewModel.onRequestDelete(reminder) }
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.pendingCompleteScopeReminder?.let {
        RepeatScopeDialog(
            title = "完成重复提醒",
            onOnce = { viewModel.onConfirmCompleteScope(RepeatActionScope.ONCE) },
            onAll = { viewModel.onConfirmCompleteScope(RepeatActionScope.ALL) },
            onDismiss = viewModel::onDismissCompleteScopeDialog
        )
    }
    uiState.pendingDeleteReminder?.let { reminder ->
        if (reminder.isRepeating) {
            RepeatScopeDialog(
                title = "删除重复提醒",
                onOnce = { viewModel.onConfirmDelete(RepeatActionScope.ONCE) },
                onAll = { viewModel.onConfirmDelete(RepeatActionScope.ALL) },
                onDismiss = viewModel::onDismissDeleteDialog
            )
        } else {
            ConfirmDialog(
                title = "移入回收站",
                text = "确定将「${reminder.title}」移入回收站吗？可在“已删除”恢复。",
                confirmLabel = "移入回收站",
                onConfirm = { viewModel.onConfirmDelete(RepeatActionScope.ALL) },
                onDismiss = viewModel::onDismissDeleteDialog
            )
        }
    }
    if (uiState.pendingBatchDelete.isNotEmpty()) {
        val hasRepeating = uiState.pendingBatchDelete.any { it.isRepeating }
        if (hasRepeating) {
            RepeatScopeDialog(
                title = "删除已选重复提醒",
                onOnce = { viewModel.onConfirmBatchDelete(RepeatActionScope.ONCE) },
                onAll = { viewModel.onConfirmBatchDelete(RepeatActionScope.ALL) },
                onDismiss = viewModel::onDismissDeleteDialog
            )
        } else {
            ConfirmDialog(
                title = "移入回收站",
                text = "确定将已选 ${uiState.pendingBatchDelete.size} 条提醒移入回收站吗？可在“已删除”恢复。",
                confirmLabel = "移入回收站",
                onConfirm = { viewModel.onConfirmBatchDelete(RepeatActionScope.ALL) },
                onDismiss = viewModel::onDismissDeleteDialog
            )
        }
    }
}

@Composable
private fun ReorderablePendingList(
    reminders: List<Reminder>,
    categories: Map<Long, com.reminder.local.domain.model.Category>,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onPersistOrder: (List<Reminder>) -> Unit
) {
    var displayItems by remember(reminders) { mutableStateOf(reminders) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 78.dp)
    ) {
        item { SectionHeader("拖住右侧把手精细调整；勾选后可批量置顶、置底或删除") }
        items(displayItems, key = { it.id }) { reminder ->
            val dragModifier = Modifier.pointerInput(reminder.id, displayItems) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { draggingId = reminder.id; dragOffset = 0f },
                    onDragCancel = { draggingId = null; dragOffset = 0f },
                    onDragEnd = {
                        if (draggingId != null) onPersistOrder(displayItems)
                        draggingId = null
                        dragOffset = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (draggingId != reminder.id) return@detectDragGesturesAfterLongPress
                        dragOffset += amount.y
                        val source = displayItems.indexOfFirst { it.id == reminder.id }
                        val sourceInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == reminder.id }
                            ?: return@detectDragGesturesAfterLongPress
                        val center = sourceInfo.offset + sourceInfo.size / 2 + dragOffset
                        val targetInfo = listState.layoutInfo.visibleItemsInfo
                            .filter { it.key is Long }
                            .minByOrNull { info -> kotlin.math.abs((info.offset + info.size / 2) - center) }
                            ?: return@detectDragGesturesAfterLongPress
                        val target = displayItems.indexOfFirst { it.id == targetInfo.key as Long }
                        if (source >= 0 && target >= 0 && source != target) {
                            displayItems = displayItems.toMutableList().also { list ->
                                val moved = list.removeAt(source)
                                list.add(target, moved)
                            }
                            dragOffset = 0f
                        }
                    }
                )
            }
            ReminderListItem(
                reminder = reminder,
                category = reminder.categoryId?.let { categories[it] },
                onClick = {},
                onToggleComplete = {},
                onSwipeComplete = {},
                onRequestDelete = {},
                isManagementMode = true,
                isSelected = reminder.id in selectedIds,
                onManageSelect = { onToggleSelection(reminder.id) },
                dragHandleModifier = dragModifier,
                modifier = Modifier.graphicsLayer {
                    translationY = if (draggingId == reminder.id) dragOffset else 0f
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
