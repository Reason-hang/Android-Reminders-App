package com.reminder.local.domain.usecase

import android.util.Log
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.ReminderStatus
import javax.inject.Inject

/** 回收站恢复：只恢复数据与应有的系统闹钟，不改变原始提醒时间。 */
class RestoreReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(reminderId: Long): Boolean {
        val deleted = repository.getByIdIncludingDeleted(reminderId)
            ?.takeIf { it.status == ReminderStatus.DELETED } ?: return false
        var restored = deleted.copy(
            status = deleted.statusBeforeDelete ?: ReminderStatus.PENDING,
            deletedAt = null,
            statusBeforeDelete = null,
            updatedAt = System.currentTimeMillis()
        )
        if (restored.status != ReminderStatus.PENDING) {
            return runCatching { repository.update(restored) }.isSuccess
        }
        val now = System.currentTimeMillis()
        if (restored.effectiveTime <= now) {
            if (!restored.isRepeating) {
                return runCatching { repository.update(restored.copy(status = ReminderStatus.EXPIRED)) }.isSuccess
            }
            var next = restored.effectiveTime
            while (next <= now) {
                val candidate = RepeatCalculator.computeNext(restored.triggerTime, next, restored.repeatType)
                if (candidate == null || restored.repeatEndDate?.let { candidate > it } == true) {
                    return runCatching {
                        repository.update(restored.copy(status = ReminderStatus.EXPIRED))
                    }.isSuccess
                }
                next = candidate
            }
            restored = restored.copy(nextTriggerTime = next)
        }
        return try {
            alarmScheduler.scheduleExact(restored)
            try {
                repository.update(restored)
                true
            } catch (error: Exception) {
                runCatching { alarmScheduler.cancel(restored) }
                false
            }
        } catch (error: Exception) {
            Log.e(TAG, "恢复提醒后注册闹钟失败 reminderId=${restored.id}", error)
            false
        }
    }

    private companion object {
        const val TAG = "RestoreReminderUseCase"
    }
}

/** 永久删除只接受已经在回收站的记录，避免绕过恢复机会。 */
class PermanentlyDeleteReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(reminderId: Long): Boolean {
        val deleted = repository.getByIdIncludingDeleted(reminderId)
            ?.takeIf { it.status == ReminderStatus.DELETED } ?: return false
        val permanentlyDeleted = runCatching { repository.delete(deleted) }.isSuccess
        if (permanentlyDeleted) runCatching { alarmScheduler.cancel(deleted) }
        return permanentlyDeleted
    }
}

/** 手动整理只改未完成事项的展示序号，不改提醒时间、状态或闹钟。 */
class ReorderRemindersUseCase @Inject constructor(
    private val repository: ReminderRepository
) {
    suspend operator fun invoke(orderedIds: List<Long>): Boolean {
        if (orderedIds.isEmpty() || orderedIds.size != orderedIds.distinct().size) return false
        return repository.replaceManualSortOrder(orderedIds)
    }
}
