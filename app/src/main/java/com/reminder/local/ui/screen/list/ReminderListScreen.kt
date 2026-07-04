package com.reminder.local.ui.screen.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.reminder.local.R
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
    viewModel: ReminderListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onCategoryClick) {
                        Icon(Icons.Filled.Category, contentDescription = "分类管理")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "添加提醒")
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

            if (!uiState.exactAlarmGranted) {
                PermissionBanner(
                    text = "精确提醒权限未开启，提醒可能无法准时触发",
                    onActionClick = {
                        context.startActivity(PermissionUtils.exactAlarmSettingsIntent(context))
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            CategoryFilterRow(
                categories = uiState.categories,
                selectedId = uiState.selectedCategoryId,
                onSelect = viewModel::selectCategory,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (uiState.pending.isEmpty() && uiState.done.isEmpty()) {
                EmptyState(text = "还没有提醒事项\n点击右下角 + 添加一个吧", modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    if (uiState.pending.isNotEmpty()) {
                        item {
                            SectionHeader(text = "未完成 (${uiState.pending.size})")
                        }
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
                        item {
                            SectionHeader(text = "已完成 (${uiState.done.size})")
                        }
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
                title = "删除提醒",
                text = "确定要删除「${reminder.title}」吗？此操作无法撤销。",
                confirmLabel = "删除",
                onConfirm = { viewModel.onConfirmDelete(RepeatActionScope.ALL) },
                onDismiss = viewModel::onDismissDeleteDialog
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
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}
