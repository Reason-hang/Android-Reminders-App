package com.reminder.local

import android.app.Application
import com.reminder.local.domain.usecase.RescheduleAllAlarmsUseCase
import com.reminder.local.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var rescheduleAllAlarmsUseCase: RescheduleAllAlarmsUseCase

    /** 应用级协程作用域，用于冷启动时的一次性后台任务，不跟任何 UI 生命周期绑定。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannels()

        // 冷启动时：把已过期的一次性提醒标记为 EXPIRED，并兜底重新注册所有闹钟
        // （正常情况下闹钟一直有效，这里主要是应对"数据和系统闹钟状态不一致"的边缘情况）。
        // 放在后台线程执行，不阻塞主线程启动。
        appScope.launch {
            rescheduleAllAlarmsUseCase()
        }
    }
}
