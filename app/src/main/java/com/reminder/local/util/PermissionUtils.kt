package com.reminder.local.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object PermissionUtils {

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.canUseFullScreenIntent()
    }

    /** 跳转到系统"闹钟和提醒"特殊权限设置页，引导用户手动开启精确闹钟。 */
    fun exactAlarmSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun fullScreenIntentSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            appDetailsSettingsIntent(context)
        }

    /** 跳转到系统应用详情页（用于引导用户去开通知权限/自启动/无限制后台等）。 */
    fun appDetailsSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun miuiAutoStartSettingsIntent(): Intent =
        Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * 系统标准的"应用通知设置"页（AOSP 原生 Intent，各厂商都支持）。
     * "允许在锁屏上显示通知内容"这个开关，在原生 Android 和大多数厂商 ROM 上都在这个页面里；
     * 即使本App把 NotificationCompat.setVisibility() 设成了 VISIBILITY_PUBLIC，
     * 如果这里被系统或用户设成了"不显示"或"隐藏敏感内容"，锁屏依然不会显示提醒的标题/备注。
     * 这是本App代码管不到的系统级开关，只能引导用户自己来这里确认。
     */
    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    fun notificationChannelSettingsIntent(context: Context, channelId: String): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        }

    /**
     * MIUI/HyperOS 的应用权限管理页（锁屏显示、悬浮窗/后台弹出界面等都在这里，
     * 而且这几项和"省电策略""自启动"是完全独立的开关，缺一样都可能导致：
     * - 在后台/锁屏时无法自动弹出全屏提醒页；
     * - 锁屏上看不到通知预览内容。
     * 不同 MIUI/HyperOS 版本这个 Activity 的包名/类名不完全一致，这里只是"尽力而为"，
     * 万一目标机型上没有这个 Activity，调用方要 catch ActivityNotFoundException 并
     * 退回到应用详情页，由用户自己在"权限管理"里找。
     */
    fun miuiAppPermissionEditorIntent(context: Context): Intent =
        Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
