package com.reminder.local.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.reminder.local.data.db.entity.ReminderEntity
import com.reminder.local.domain.model.ReminderStatus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val databaseName = "migration-${UUID.randomUUID()}.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrationFromVersionThreeRemovesPriorityAndRepairsDuplicateAlarmIds() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(reminderInsertSql(1, "第一条", 9))
            execSQL(reminderInsertSql(2, "第二条", 9))
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            AppDatabase.MIGRATION_3_4
        ).use { database ->
            database.query("PRAGMA table_info(reminders)").use { columns ->
                val names = buildSet {
                    val nameIndex = columns.getColumnIndexOrThrow("name")
                    while (columns.moveToNext()) add(columns.getString(nameIndex))
                }
                assertFalse(names.contains("priority"))
            }

            database.query("SELECT alarmId FROM reminders ORDER BY id").use { alarms ->
                val values = buildList {
                    val alarmIndex = alarms.getColumnIndexOrThrow("alarmId")
                    while (alarms.moveToNext()) add(alarms.getInt(alarmIndex))
                }
                assertEquals(2, values.toSet().size)
                assertTrue(values.all { it < 0 })
            }
        }
    }

    @Test
    fun migrationFromVersionThreeRepairsZeroNegativeAndDuplicateAlarmIdsWithoutCollision() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(reminderInsertSql(1, "第一条", 9))
            execSQL(reminderInsertSql(2_147_483_647, "第二条", 9))
            execSQL(reminderInsertSql(3, "第三条", -1))
            execSQL(reminderInsertSql(4, "第四条", 0))
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            AppDatabase.MIGRATION_3_4
        ).use { database ->
            database.query("SELECT id, alarmId FROM reminders ORDER BY id").use { alarms ->
                val values = buildMap {
                    val idIndex = alarms.getColumnIndexOrThrow("id")
                    val alarmIndex = alarms.getColumnIndexOrThrow("alarmId")
                    while (alarms.moveToNext()) {
                        put(alarms.getLong(idIndex), alarms.getInt(alarmIndex))
                    }
                }

                assertEquals(
                    mapOf(1L to -1, 3L to -2, 4L to -3, 2_147_483_647L to -4),
                    values
                )
            }
        }
    }

    @Test
    fun migrationFromVersionFourKeepsExistingReminderVisibleAndAddsRecycleFields() {
        helper.createDatabase(databaseName, 4).apply {
            execSQL(reminderV4InsertSql(1, "历史提醒", 9))
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            AppDatabase.MIGRATION_4_5
        ).use { database ->
            database.query(
                "SELECT status, manualSortOrder, deletedAt, statusBeforeDelete FROM reminders WHERE id = 1"
            ).use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("PENDING", row.getString(0))
                assertEquals(0L, row.getLong(1))
                assertTrue(row.isNull(2))
                assertTrue(row.isNull(3))
            }
        }
    }

    @Test
    fun migrationFromVersionFiveKeepsNullPreviousStatusAndRepairsInvalidEnumData() {
        helper.createDatabase(databaseName, 5).apply {
            execSQL(reminderV5InsertSql(1, "正常提醒", 9, "PENDING", null))
            execSQL(reminderV5InsertSql(2, "已删除提醒", 10, "DELETED", "BROKEN_STATUS"))
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            AppDatabase.MIGRATION_5_6
        ).use { database ->
            database.query("SELECT status, statusBeforeDelete FROM reminders WHERE id = 1").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("PENDING", row.getString(0))
                assertTrue(row.isNull(1))
            }
            database.query("SELECT status, statusBeforeDelete FROM reminders WHERE id = 2").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("DELETED", row.getString(0))
                assertEquals("PENDING", row.getString(1))
            }
        }
    }

    @Test
    fun getAllPendingReadsNullableStatusBeforeDeleteWithoutCrashing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.reminderDao().insert(
                ReminderEntity(
                    title = "待办",
                    triggerTime = 1_000L,
                    status = ReminderStatus.PENDING,
                    alarmId = 42,
                    createdAt = 1_000L,
                    updatedAt = 1_000L,
                    statusBeforeDelete = null
                )
            )

            val reminders = db.reminderDao().getAllPending()

            assertEquals(1, reminders.size)
            assertNull(reminders.single().statusBeforeDelete)
        } finally {
            db.close()
        }
    }

    private fun reminderInsertSql(id: Long, title: String, alarmId: Int): String =
        """
        INSERT INTO reminders (
            id, title, note, triggerTime, nextTriggerTime, categoryId, priority, status,
            repeatType, repeatEndDate, advanceReminderType, customAdvanceValue,
            customAdvanceUnit, notifyVibrate, notifySound, alarmId, createdAt, updatedAt, completedAt
        ) VALUES (
            $id, '$title', NULL, 1000, 1000, NULL, 'HIGH', 'PENDING',
            'NONE', NULL, 'NONE', 1, 'HOURS', 1, 1, $alarmId, 1000, 1000, NULL
        )
        """.trimIndent()

    private fun reminderV4InsertSql(id: Long, title: String, alarmId: Int): String =
        """
        INSERT INTO reminders (
            id, title, note, triggerTime, nextTriggerTime, categoryId, status,
            repeatType, repeatEndDate, advanceReminderType, customAdvanceValue,
            customAdvanceUnit, notifyVibrate, notifySound, alarmId, createdAt, updatedAt, completedAt
        ) VALUES (
            $id, '$title', NULL, 1000, 1000, NULL, 'PENDING',
            'NONE', NULL, 'NONE', 1, 'HOURS', 1, 1, $alarmId, 1000, 1000, NULL
        )
        """.trimIndent()

    private fun reminderV5InsertSql(
        id: Long,
        title: String,
        alarmId: Int,
        status: String,
        statusBeforeDelete: String?
    ): String {
        val previousStatusSql = statusBeforeDelete?.let { "'$it'" } ?: "NULL"
        return """
            INSERT INTO reminders (
                id, title, note, triggerTime, nextTriggerTime, categoryId, status,
                repeatType, repeatEndDate, advanceReminderType, customAdvanceValue,
                customAdvanceUnit, notifyVibrate, notifySound, alarmId, createdAt, updatedAt,
                completedAt, manualSortOrder, deletedAt, statusBeforeDelete
            ) VALUES (
                $id, '$title', NULL, 1000, 1000, NULL, '$status',
                'NONE', NULL, 'NONE', 1, 'HOURS', 1, 1, $alarmId, 1000, 1000,
                NULL, 0, NULL, $previousStatusSql
            )
        """.trimIndent()
    }
}
