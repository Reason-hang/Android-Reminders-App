package com.reminder.local.domain.usecase

import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.ReminderStatus
import javax.inject.Inject

sealed interface EditResult {
    data class Success(val reactivated: Boolean) : EditResult
    data class TimeAlreadyPassed(val message: String = "时间已过，请重新选择") : EditResult
    data class Failure(val message: String = "保存失败，请重试") : EditResult
}

/**
 * 编辑提醒：
 * - 时间没变：直接保存其它字段，不动闹钟。
 * - 时间变了：取消旧闹钟 -> 校验新时间必须是未来 -> 保存 -> 注册新闹钟（同一套校验规则，跟新增一致）。
 * - 如果原状态是 DONE/EXPIRED，且新时间在未来，则"重新激活"为 PENDING 并重新调度闹钟。
 */
class EditReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(updated: Reminder): EditResult {
        val old = repository.getById(updated.id) ?: return EditResult.Failure("提醒不存在")
        val now = System.currentTimeMillis()
        val timeChanged = updated.triggerTime != old.triggerTime

        var reactivated = false
        var toSave = updated

        if (timeChanged) {
            if (updated.triggerTime <= now) {
                return EditResult.TimeAlreadyPassed()
            }
            if (old.status == ReminderStatus.PENDING) {
                alarmScheduler.cancel(old)
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

        return try {
            val finalToSave = toSave.copy(alarmId = old.alarmId, updatedAt = now)
            repository.update(finalToSave)
            if (timeChanged && finalToSave.status == ReminderStatus.PENDING) {
                try {
                    alarmScheduler.scheduleExact(finalToSave)
                } catch (e: Exception) {
                    // 调度失败：把数据库状态还原成旧值，保持原子性。
                    repository.update(old)
                    return EditResult.Failure()
                }
            }
            EditResult.Success(reactivated)
        } catch (e: Exception) {
            EditResult.Failure()
        }
    }
}
