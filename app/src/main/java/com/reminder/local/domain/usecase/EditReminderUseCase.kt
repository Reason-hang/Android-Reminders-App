package com.reminder.local.domain.usecase

import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.ReminderStatus
import android.util.Log
import javax.inject.Inject

sealed interface EditResult {
    data class Success(val reactivated: Boolean) : EditResult
    data class TimeAlreadyPassed(val message: String = "时间已过，请重新选择") : EditResult
    data class Failure(val message: String = "保存失败，请重试") : EditResult
}

/**
 * 编辑提醒：
 * - 调度字段没变：只保存其它字段，不动闹钟。
 * - 调度字段变化：先完成全部校验和 occurrence 条件更新，再替换系统闹钟；失败时补偿回滚。
 * - 如果原状态是 DONE/EXPIRED，且新时间在未来，则"重新激活"为 PENDING 并重新调度闹钟。
 */
class EditReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(updated: Reminder): EditResult {
        val old = repository.getById(updated.id) ?: return EditResult.Failure("提醒不存在")
        ReminderContentValidator.validate(updated.title, updated.note)?.let {
            return EditResult.Failure(it)
        }
        val now = System.currentTimeMillis()
        val timeChanged = updated.triggerTime != old.triggerTime
        val scheduleChanged = timeChanged ||
            updated.advanceReminderType != old.advanceReminderType ||
            updated.customAdvanceValue != old.customAdvanceValue ||
            updated.customAdvanceUnit != old.customAdvanceUnit ||
            updated.repeatType != old.repeatType ||
            updated.repeatEndDate != old.repeatEndDate

        var reactivated = false
        var toSave = updated

        if (timeChanged) {
            if (updated.triggerTime <= now) {
                return EditResult.TimeAlreadyPassed()
            }
            reactivated = old.status != ReminderStatus.PENDING
            toSave = updated.copy(
                status = ReminderStatus.PENDING,
                nextTriggerTime = updated.triggerTime,
                completedAt = null
            )
        } else {
            // 时间没变：保持原有的状态/下一次触发时间/alarmId，不触碰闹钟。
            toSave = updated.copy(
                status = old.status,
                nextTriggerTime = old.nextTriggerTime,
                alarmId = old.alarmId,
                completedAt = old.completedAt
            )
        }

        if (toSave.repeatEndDate != null && toSave.repeatEndDate <= toSave.triggerTime) {
            return EditResult.Failure("重复截止日期必须晚于第一次触发时间")
        }
        ReminderScheduleValidator.validate(toSave)?.let { return EditResult.Failure(it) }

        val endReached = toSave.repeatType != com.reminder.local.domain.model.RepeatType.NONE &&
            toSave.repeatEndDate != null && toSave.effectiveTime > toSave.repeatEndDate
        val finalToSave = toSave.copy(
            alarmId = old.alarmId,
            status = if (endReached) ReminderStatus.DONE else toSave.status,
            completedAt = if (endReached) now else toSave.completedAt,
            createdAt = old.createdAt,
            updatedAt = now
        )

        if (!scheduleChanged) {
            return if (persistIfCurrent(old, finalToSave)) {
                EditResult.Success(reactivated)
            } else {
                EditResult.Failure("提醒状态刚刚发生变化，请重新打开后再编辑")
            }
        }

        if (finalToSave.status != ReminderStatus.PENDING) {
            if (!persistIfCurrent(old, finalToSave)) {
                return EditResult.Failure("提醒状态刚刚发生变化，请重新打开后再编辑")
            }
            if (old.status == ReminderStatus.PENDING) {
                runCatching { alarmScheduler.cancel(old) }
                    .onFailure { logError("截止重复后取消旧闹钟失败 reminderId=${old.id}", it) }
            }
            return EditResult.Success(reactivated)
        }

        if (!persistIfCurrent(old, finalToSave)) {
            return EditResult.Failure("提醒状态刚刚发生变化，请重新打开后再编辑")
        }
        return try {
            alarmScheduler.replaceExact(old, finalToSave)
            EditResult.Success(reactivated)
        } catch (scheduleError: Exception) {
            rollbackOccurrence(old, finalToSave)
            logError("编辑后替换系统闹钟失败 reminderId=${old.id}", scheduleError)
            EditResult.Failure()
        }
    }

    private suspend fun persistIfCurrent(old: Reminder, updated: Reminder): Boolean =
        if (old.status == ReminderStatus.PENDING) {
            runCatching { repository.updateIfOccurrenceCurrent(updated, old.effectiveTime) }
                .onFailure { logError("提醒条件更新失败 reminderId=${old.id}", it) }
                .getOrDefault(false)
        } else {
            runCatching { repository.update(updated) }
                .onFailure { logError("提醒更新失败 reminderId=${old.id}", it) }
                .isSuccess
        }

    private suspend fun rollbackOccurrence(old: Reminder, failed: Reminder) {
        val rolledBack = runCatching {
            repository.updateIfOccurrenceCurrent(old, failed.effectiveTime)
        }.getOrDefault(false)
        if (!rolledBack) {
            logError(
                "编辑调度失败后数据库回滚未生效 reminderId=${old.id}",
                IllegalStateException("occurrence 已被其他操作修改")
            )
        }
    }

    private companion object {
        const val TAG = "EditReminderUseCase"
    }

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }
}
