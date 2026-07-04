package com.reminder.local.ui.screen.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.reminder.local.domain.model.Category
import com.reminder.local.ui.components.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAddDialog) {
                Icon(Icons.Filled.Add, contentDescription = "新建分类")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxWidth()) {
            items(uiState.categories, key = { it.id }) { category ->
                CategoryRow(
                    category = category,
                    count = uiState.reminderCounts[category.id] ?: 0,
                    onEdit = { viewModel.openEditDialog(category) },
                    onDelete = { viewModel.requestDelete(category) }
                )
            }
        }
    }

    uiState.errorMessage?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearError()
        }
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Snackbar { Text(message) }
        }
    }

    if (uiState.showAddDialog) {
        CategoryEditDialog(
            initialName = "",
            initialColor = CATEGORY_COLOR_SWATCHES.first(),
            onConfirm = { name, color -> viewModel.addCategory(name, color) },
            onDismiss = viewModel::dismissDialogs
        )
    }

    uiState.editingCategory?.let { category ->
        CategoryEditDialog(
            initialName = category.name,
            initialColor = category.colorHex,
            onConfirm = { name, color -> viewModel.updateCategory(category, name, color) },
            onDismiss = viewModel::dismissDialogs
        )
    }

    uiState.pendingDeleteCategory?.let { category ->
        val count = uiState.reminderCounts[category.id] ?: 0
        ConfirmDialog(
            title = "删除分类",
            text = if (count > 0) {
                "「${category.name}」下有 $count 条提醒，删除分类后这些提醒会变为未分类，不会被删除。确定继续吗？"
            } else {
                "确定要删除「${category.name}」吗？"
            },
            confirmLabel = "删除",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDialogs
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    count: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(category.name) },
        supportingContent = { Text("$count 条提醒" + if (category.isDefault) " · 内置分类" else "") },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(parseColor(category.colorHex))
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
                if (!category.isDefault) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
            }
        }
    )
}

@Composable
private fun CategoryEditDialog(
    initialName: String,
    initialColor: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "新建分类" else "编辑分类") },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 10) name = it },
                    label = { Text("分类名称") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("颜色", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CATEGORY_COLOR_SWATCHES.forEach { swatch ->
                        val isSelected = swatch == color
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 32.dp else 26.dp)
                                .clip(CircleShape)
                                .background(parseColor(swatch))
                                .clickable { color = swatch }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    Color.Gray
}
