package com.reminder.local.ui.screen.deleted

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.reminder.local.ui.components.ConfirmDialog
import com.reminder.local.util.TimeFormatter
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedRemindersScreen(
    onBack: () -> Unit,
    viewModel: DeletedRemindersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { snackbarHostState.showSnackbar(it) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已删除（${uiState.reminders.size}）") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::selectAll) {
                        Text(if (uiState.selectedIds.size == uiState.reminders.size && uiState.reminders.isNotEmpty()) "取消全选" else "全选")
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.selectedIds.isNotEmpty()) {
                BottomAppBar {
                    TextButton(onClick = viewModel::restoreSelected, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Text("恢复（${uiState.selectedIds.size}）")
                    }
                    TextButton(onClick = viewModel::requestPermanentDelete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null)
                        Text("永久删除")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.reminders.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center
            ) {
                Text("回收站为空", modifier = Modifier.padding(horizontal = 24.dp))
                Text(
                    "从首页删除的提醒会先保存在这里，可勾选恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 78.dp)
            ) {
                items(uiState.reminders, key = { it.id }) { reminder ->
                    Card(
                        onClick = { viewModel.toggleSelection(reminder.id) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Checkbox(
                                checked = reminder.id in uiState.selectedIds,
                                onCheckedChange = { viewModel.toggleSelection(reminder.id) }
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(reminder.title, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "删除于 ${TimeFormatter.format(reminder.deletedAt ?: reminder.updatedAt).text}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (uiState.confirmPermanentDelete) {
        ConfirmDialog(
            title = "永久删除",
            text = "将永久删除已选 ${uiState.selectedIds.size} 条提醒，且无法恢复。",
            confirmLabel = "永久删除",
            onConfirm = viewModel::confirmPermanentDelete,
            onDismiss = viewModel::dismissPermanentDelete
        )
    }
}
