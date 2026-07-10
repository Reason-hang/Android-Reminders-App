package com.reminder.local.domain.usecase

import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddReminderUseCaseTest {

    @Test
    fun exactAlarmPermissionErrorIsShownToUser() = runBlocking {
        val repository = FakeReminderRepository()
        val useCase = AddReminderUseCase(
            repository = repository,
            alarmScheduler = ThrowingAlarmScheduler(IllegalStateException("精确闹钟权限未开启"))
        )

        val result = useCase(futureReminder())

        assertTrue(result is SaveResult.Failure)
        assertEquals("精确闹钟权限未开启，请到设置页打开后重试", (result as SaveResult.Failure).message)
        assertTrue(repository.deletedAfterInsert)
    }

    @Test
    fun genericAlarmScheduleErrorKeepsActionableMessage() = runBlocking {
        val useCase = AddReminderUseCase(
            repository = FakeReminderRepository(),
            alarmScheduler = ThrowingAlarmScheduler(IllegalArgumentException("bad pending intent options"))
        )

        val result = useCase(futureReminder())

        assertTrue(result is SaveResult.Failure)
        assertEquals("闹钟注册失败，请检查通知、锁屏显示和后台弹出权限后重试", (result as SaveResult.Failure).message)
    }

    private fun futureReminder(): Reminder =
        Reminder(
            title = "测试提醒",
            triggerTime = System.currentTimeMillis() + 60_000L
        )
}

private class ThrowingAlarmScheduler(
    private val error: Exception
) : AlarmScheduler {
    override fun canScheduleExactAlarms(): Boolean = true
    override fun scheduleExact(reminder: Reminder) {
        throw error
    }
    override fun cancel(reminder: Reminder) = Unit
    override fun scheduleSnooze(reminder: Reminder, delayMillis: Long) = Unit
}

private class FakeReminderRepository : ReminderRepository {
    private var saved: Reminder? = null
    var deletedAfterInsert: Boolean = false
        private set

    override fun observeAll(): Flow<List<Reminder>> = flowOf(emptyList())
    override suspend fun getById(id: Long): Reminder? = saved?.takeIf { it.id == id }
    override suspend fun insert(reminder: Reminder): Long {
        saved = reminder.copy(id = 1L)
        return 1L
    }
    override suspend fun update(reminder: Reminder) {
        saved = reminder
    }
    override suspend fun delete(reminder: Reminder) {
        deletedAfterInsert = saved?.id == reminder.id
        saved = null
    }
    override suspend fun getAllPending(): List<Reminder> = saved?.let { listOf(it) } ?: emptyList()
    override suspend fun markNonRepeatingExpired(now: Long) = Unit
    override fun observeCountByCategory(categoryId: Long): Flow<Int> = flowOf(0)
}
