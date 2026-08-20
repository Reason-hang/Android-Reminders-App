package com.reminder.local.domain.mapper

import com.reminder.local.data.db.entity.ReminderEntity
import com.reminder.local.domain.model.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    @Test
    fun nullableRecycleStatusRemainsNullableWithoutRoomEnumConverter() {
        val entity = ReminderEntity(
            title = "回收站状态边界",
            triggerTime = 100L,
            createdAt = 100L,
            updatedAt = 100L,
            statusBeforeDelete = null
        )

        assertNull(entity.toDomain().statusBeforeDelete)
    }

    @Test
    fun validRecycleStatusMapsToDomainAndBackToText() {
        val entity = ReminderEntity(
            title = "回收站状态映射",
            triggerTime = 100L,
            createdAt = 100L,
            updatedAt = 100L,
            statusBeforeDelete = ReminderStatus.DONE.name
        )

        val domain = entity.toDomain()

        assertEquals(ReminderStatus.DONE, domain.statusBeforeDelete)
        assertEquals(ReminderStatus.DONE.name, domain.toEntity().statusBeforeDelete)
    }

    @Test
    fun unexpectedRecycleStatusDoesNotCrashReminderRead() {
        val entity = ReminderEntity(
            title = "异常回收站状态",
            triggerTime = 100L,
            createdAt = 100L,
            updatedAt = 100L,
            statusBeforeDelete = "REMOVED_BY_OLD_VERSION"
        )

        assertNull(entity.toDomain().statusBeforeDelete)
    }
}
