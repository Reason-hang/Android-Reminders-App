package com.reminder.local.domain.usecase

import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatType
import javax.inject.Inject

/**
 * 开机广播 / App 冷启动时调用：
 * 1. 把"已过期但仍是 PENDING 的非重复提醒"标记为 EXPIRED；
 * 2. 给所有仍是 PENDING 的提醒重新注册 AlarmManager 闹钟（系统重启后闹钟全部失效，必须重新注册）；
 * 3. 重复提醒如果 nextTriggerTime 已经过去（比如手机关机了很久），要"追赶"到未来的下一个时间点，
 *    而不是拿一个已经过去的时间去注册闹钟。
 */
class RescheduleAllAlarmsUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke() {
        val now = System.currentTimeMillis()
        repository.markNonRepeatingExpired(now)

        val pendings = repository.getAllPending()
        for (reminder in pendings) {
            if (!alarmScheduler.canScheduleExactAlarms()) continue

            if (reminder.repeatType == RepeatType.NONE) {
                val effective = reminder.nextTriggerTime ?: reminder.triggerTime
                if (effective > now) {
                    alarmScheduler.scheduleExact(reminder)
                }
                // effective <= now 的情况已经被上面的 markNonRepeatingExpired 处理掉了。
                continue
            }

            // 重复提醒：追赶到未来的下一个触发点。
            var next = reminder.nextTriggerTime ?: reminder.triggerTime
            var shouldStop = false
            while (next <= now) {
                val candidate = RepeatCalculator.computeNext(reminder.triggerTime, next, reminder.repeatType)
                val exceededEnd = reminder.repeatEndDate != null &&
                    candidate != null && candidate > reminder.repeatEndDate
                if (candidate == null || exceededEnd) {
                    shouldStop = true
                    break
                }
                next = candidate
            }

            if (shouldStop) {
                repository.update(reminder.copy(status = ReminderStatus.DONE, updatedAt = now))
            } else {
                val updated = reminder.copy(nextTriggerTime = next, updatedAt = now)
                runCatching { alarmScheduler.scheduleExact(updated) }
                    .onSuccess { repository.update(updated) }
            }
        }
    }
}
