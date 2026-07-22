package com.reminder.local.domain.usecase

import android.util.Log
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.model.ReminderStatus
import javax.inject.Inject

/**
 * 完成 / 撤销完成。重复提醒需要区分"仅本次"和"停止所有重复"。
 */
class CompleteReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {

    /** 勾选完成。 */
    suspend fun markDone(
        reminder: Reminder,
        scope: RepeatActionScope,
        occurrenceTime: Long? = null
    ): Boolean {
        val current = repository.getById(reminder.id) ?: return false
        // 通知/全屏页可能属于编辑前的旧 occurrence。它只能关闭自身的打断，绝不能完成或取消已改期的提醒。
        if (occurrenceTime != null && occurrenceTime != current.effectiveTime) return true
        if (!current.isRepeating || scope == RepeatActionScope.ALL) {
            val updated =
                current.copy(
                    status = ReminderStatus.DONE,
                    completedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            return runCatching { repository.update(updated) }
                .fold(
                    onSuccess = {
                        if (current.status == ReminderStatus.PENDING) {
                            runCatching { alarmScheduler.cancel(current) }
                                .onFailure { logError("完成后取消系统闹钟失败 reminderId=${current.id}", it) }
                        }
                        true
                    },
                    onFailure = {
                        logError("完成提醒写入数据库失败 reminderId=${current.id}", it)
                        false
                    }
                )
        }

        val from = current.effectiveTime
        val next = RepeatCalculator.computeNext(current.triggerTime, from, current.repeatType)
        val exceededEnd = current.repeatEndDate != null && next != null && next > current.repeatEndDate

        if (next == null || exceededEnd) {
            val finished =
                current.copy(
                    status = ReminderStatus.DONE,
                    completedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            return runCatching { repository.updateIfOccurrenceCurrent(finished, from) }
                .fold(
                    onSuccess = { updated ->
                        if (updated) runCatching { alarmScheduler.cancel(current) }
                        updated
                    },
                    onFailure = {
                        logError("结束重复提醒写入数据库失败 reminderId=${current.id}", it)
                        false
                    }
                )
        }

        val updated = current.copy(nextTriggerTime = next, updatedAt = System.currentTimeMillis())
        val persisted = runCatching { repository.updateIfOccurrenceCurrent(updated, from) }
            .onFailure { logError("markDone(ONCE) 条件更新失败 reminderId=${current.id}", it) }
            .getOrDefault(false)
        if (!persisted) return false
        return try {
            alarmScheduler.replaceExact(current, updated)
            true
        } catch (scheduleError: Exception) {
            rollbackOccurrence(current, next)
            logError("markDone(ONCE) 重新调度失败 reminderId=${current.id}", scheduleError)
            false
        }
    }

    /** 撤销完成（从 DONE 变回 PENDING/EXPIRED）。 */
    suspend fun markPending(reminder: Reminder): Boolean {
        val now = System.currentTimeMillis()
        val effective = reminder.nextTriggerTime ?: reminder.triggerTime
        if (effective > now) {
            val updated = reminder.copy(
                status = ReminderStatus.PENDING,
                completedAt = null,
                updatedAt = now
            )
            return try {
                alarmScheduler.scheduleExact(updated)
                try {
                    repository.update(updated)
                    true
                } catch (dbError: Exception) {
                    runCatching { alarmScheduler.cancel(updated) }
                    logError("markPending 数据库更新失败 reminderId=${reminder.id}", dbError)
                    false
                }
            } catch (scheduleError: Exception) {
                logError("markPending 重新调度失败 reminderId=${reminder.id}", scheduleError)
                false
            }
        } else {
            // 时间已经过去了，不能对着过去的时间重新触发通知，只把状态还原成"已过期"。
            return runCatching { repository.update(
                reminder.copy(status = ReminderStatus.EXPIRED, completedAt = null, updatedAt = now)
            ) }.isSuccess
        }
    }

    private suspend fun rollbackOccurrence(previous: Reminder, failedOccurrence: Long) {
        val rolledBack = runCatching {
            repository.updateIfOccurrenceCurrent(previous, failedOccurrence)
        }.getOrDefault(false)
        if (!rolledBack) {
            logError(
                "重新调度失败后数据库回滚未生效 reminderId=${previous.id}",
                IllegalStateException("occurrence 已被其他操作修改")
            )
        }
    }

    private companion object {
        const val TAG = "CompleteReminderUseCase"
    }

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }
}
