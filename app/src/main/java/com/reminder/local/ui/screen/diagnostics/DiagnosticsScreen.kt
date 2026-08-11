package com.reminder.local.ui.screen.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.reminder.local.diagnostics.core.DiagnosticEvidence
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }
    val enhanced = state.enhancedUntil > System.currentTimeMillis()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("诊断与排障") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("本机黑匣子", style = MaterialTheme.typography.titleMedium)
            Text(
                "记录提醒从调度、到点、前台服务、通知、全屏请求到页面生命周期的链路。不会记录提醒标题、备注或账号数据。",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("增强诊断（24 小时）")
                    Text(
                        if (enhanced) "已启用，至 ${formatTime(state.enhancedUntil)} 自动关闭" else "关闭；基础故障链路仍会保留",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enhanced, onCheckedChange = viewModel::setEnhanced)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    viewModel.exportIntent()?.let { context.startActivity(android.content.Intent.createChooser(it, "导出诊断包")) }
                }) { Text("导出诊断包") }
                OutlinedButton(onClick = viewModel::refresh) { Text("刷新") }
            }
            Text("导出包仅在你主动分享时离开本机；它能证明应用侧已发生的事件，不能直接证明系统桌面或锁屏界面最终如何呈现。", style = MaterialTheme.typography.labelSmall)
            HorizontalDivider()
            Text("最近提醒链路", style = MaterialTheme.typography.titleMedium)
            if (state.summaries.isEmpty()) {
                Text("暂未记录强提醒链路。设置一条测试提醒并触发后回到此页刷新。")
            }
            state.summaries.take(20).forEach { summary ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(formatTime(summary.startedAtMillis), style = MaterialTheme.typography.labelSmall)
                        Text(summary.conclusion, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            evidenceText(summary.evidence),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (summary.evidence) {
                                DiagnosticEvidence.CONFIRMED -> MaterialTheme.colorScheme.primary
                                DiagnosticEvidence.LIKELY -> MaterialTheme.colorScheme.tertiary
                                DiagnosticEvidence.INSUFFICIENT -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text("下一步：${summary.nextAction}", style = MaterialTheme.typography.labelSmall)
                        Text("事件：${summary.events.joinToString(" → ") { it.name }}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            TextButton(onClick = { showClearConfirm = true }) { Text("清除本机诊断记录") }
        }
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除诊断记录？") },
            text = { Text("只删除本机诊断日志，不会删除提醒事项。此操作无法撤销。") },
            confirmButton = { TextButton(onClick = { viewModel.clear(); showClearConfirm = false }) { Text("清除") } },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
}

private fun evidenceText(evidence: DiagnosticEvidence): String = when (evidence) {
    DiagnosticEvidence.CONFIRMED -> "应用侧证据：已确认"
    DiagnosticEvidence.LIKELY -> "应用侧推断：高可能"
    DiagnosticEvidence.INSUFFICIENT -> "证据不足：需继续采集"
}

private fun formatTime(millis: Long): String = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(millis))
