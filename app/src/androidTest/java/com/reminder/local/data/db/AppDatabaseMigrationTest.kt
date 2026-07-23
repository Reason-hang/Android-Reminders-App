package com.reminder.local.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
