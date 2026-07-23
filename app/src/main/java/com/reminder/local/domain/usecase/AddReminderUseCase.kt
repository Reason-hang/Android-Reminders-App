package com.reminder.local.domain.usecase

import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.alarm.AlarmSchedulerImpl
import com.reminder.local.domain.model.Reminder
import android.util.Log
import javax.inject.Inject

sealed interface SaveResult {
    data class Success(val id: Long) : SaveResult
    data class TimeAlreadyPassed(val message: String = "时间已过，请重新选择") : SaveResult
    data class Failure(val message: String = "保存失败，请重试") : SaveResult
}

/**
 * 新增提醒：数据库写入 + AlarmManager 注册是一个"原子操作"——
 * 如果注册闹钟失败（比如权限被回收），要把刚插入的数据库记录删掉，整体回滚。
 */
class AddReminderUseCase @Inject constructor(
    private val repository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(input: Reminder): SaveResult {
        ReminderContentValidator.validate(input.title, input.note)?.let {
            return SaveResult.Failure(it)
        }
        val now = System.currentTimeMillis()
        if (input.triggerTime <= now) {
            return SaveResult.TimeAlreadyPassed()
        }
        if (input.repeatEndDate != null && input.repeatEndDate <= input.triggerTime) {
            return SaveResult.Failure("重复截止日期必须晚于第一次触发时间")
        }
        ReminderScheduleValidator.validate(input)?.let { return SaveResult.Failure(it) }

        return try {
            val prepared = input.copy(
                alarmId = repository.nextAvailableAlarmId(AlarmSchedulerImpl::generateAlarmId),
                nextTriggerTime = input.triggerTime,
                createdAt = now,
                updatedAt = now
            )
            val id = repository.insert(prepared)
            val saved = prepared.copy(id = id)
            try {
                alarmScheduler.scheduleExact(saved)
            } catch (alarmError: Exception) {
                runCatching { alarmScheduler.cancel(saved) }
                    .onFailure { Log.e(TAG, "新增失败后取消残留系统闹钟失败 reminderId=$id", it) }
                runCatching { repository.delete(saved) }
                    .onFailure { Log.e(TAG, "新增失败后回滚数据库失败 reminderId=$id", it) }
                return SaveResult.Failure(alarmError.toUserMessage())
            }
            SaveResult.Success(id)
        } catch (dbError: Exception) {
            SaveResult.Failure()
        }
    }

    private fun Exception.toUserMessage(): String =
        when {
            message?.contains("精确闹钟权限") == true ->
                "精确闹钟权限未开启，请到设置页打开后重试"
            else ->
                "闹钟注册失败，请检查通知、锁屏显示和后台弹出权限后重试"
        }

    private companion object {
        const val TAG = "AddReminderUseCase"
    }
}

internal suspend fun ReminderRepository.nextAvailableAlarmId(candidate: () -> Int): Int {
    repeat(MAX_ALARM_ID_GENERATION_ATTEMPTS) {
        val alarmId = candidate()
        if (!isAlarmIdInUse(alarmId)) return alarmId
    }
    throw IllegalStateException("无法生成未占用的提醒闹钟标识")
}

private const val MAX_ALARM_ID_GENERATION_ATTEMPTS = 100
