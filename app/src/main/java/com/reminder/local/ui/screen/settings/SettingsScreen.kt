package com.reminder.local.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reminder.local.BuildConfig
import com.reminder.local.util.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从系统设置页返回时，重新检查一次精确闹钟权限状态。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth().padding(16.dp)) {

            Text("新增提醒默认通知方式", style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("默认响铃")
                Switch(
                    checked = uiState.settings.defaultNotifySound,
                    onCheckedChange = viewModel::setDefaultSound
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("默认震动")
                Switch(
                    checked = uiState.settings.defaultNotifyVibrate,
                    onCheckedChange = viewModel::setDefaultVibrate
                )
            }
            Text(
                "仅影响新建提醒的默认值，不会改变已有提醒的设置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("系统权限", style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("精确闹钟")
                    Text(
                        if (uiState.exactAlarmGranted) "已开启" else "未开启，提醒可能不准时",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.exactAlarmGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
                if (!uiState.exactAlarmGranted) {
                    TextButton(onClick = {
                        context.startActivity(PermissionUtils.exactAlarmSettingsIntent(context))
                    }) { Text("去开启") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("全屏提醒")
                    Text(
                        if (uiState.fullScreenIntentGranted) "已开启，锁屏时可弹出强提醒" else "未开启，可能退化成普通通知",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.fullScreenIntentGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
                if (!uiState.fullScreenIntentGranted) {
                    TextButton(onClick = {
                        context.startActivity(PermissionUtils.fullScreenIntentSettingsIntent(context))
                    }) { Text("去开启") }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("小米/红米机型提示", style = MaterialTheme.typography.labelSmall)
            Text(
                "MIUI/HyperOS 系统电池策略较激进，为保证提醒准时到达，建议手动前往：\n" +
                    "设置 → 应用设置 → 应用管理 → 提醒事项 → 省电策略，选择「无限制」；\n" +
                    "并在「自启动管理」中允许本App自启动。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            TextButton(onClick = {
                context.startActivity(PermissionUtils.appDetailsSettingsIntent(context))
            }) { Text("打开应用详情设置页") }
            TextButton(onClick = {
                runCatching {
                    context.startActivity(PermissionUtils.miuiAutoStartSettingsIntent())
                }.onFailure {
                    context.startActivity(PermissionUtils.appDetailsSettingsIntent(context))
                }
            }) { Text("打开自启动管理") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("关于", style = MaterialTheme.typography.labelSmall)
            Text(
                "提醒事项 v${BuildConfig.VERSION_NAME}\n纯本地运行，不联网，不收集任何数据",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
