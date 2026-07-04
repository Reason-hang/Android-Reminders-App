package com.reminder.local.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "确认",
    dismissLabel: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

/** 对重复提醒执行"完成"或"删除"时，弹出选择：仅本次 / 全部。 */
@Composable
fun RepeatScopeDialog(
    title: String,
    onOnce: () -> Unit,
    onAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("这是一条重复提醒，要如何处理？") },
        confirmButton = {
            TextButton(onClick = onAll) { Text("停止所有重复") }
        },
        dismissButton = {
            TextButton(onClick = onOnce) { Text("仅本次") }
        }
    )
}
