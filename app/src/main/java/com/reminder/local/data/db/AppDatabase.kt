package com.reminder.local.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reminder.local.data.db.dao.CategoryDao
import com.reminder.local.data.db.dao.ReminderDao
import com.reminder.local.data.db.entity.CategoryEntity
import com.reminder.local.data.db.entity.ReminderEntity

@Database(
    entities = [ReminderEntity::class, CategoryEntity::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val DB_NAME = "reminder.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN advanceReminderType TEXT NOT NULL DEFAULT 'NONE'"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN customAdvanceValue INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE reminders ADD COLUMN customAdvanceUnit TEXT NOT NULL DEFAULT 'HOURS'")
            }
        }

        /**
         * v4 彻底移除已经下线的 priority 字段，并把 alarmId 唯一性提升为数据库约束。
         * 历史重复/零值只修复异常行；负数 requestCode/notificationId 对 Android API 合法，
         * 且与正常生成的正数 alarmId 不冲突。所有非正或重复历史值按主键顺序分配连续
         * 负数，避免取模、主键取反或已有负值造成碰撞；该映射以 alarmId 的 Int 可表示范围为边界。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        note TEXT,
                        triggerTime INTEGER NOT NULL,
                        nextTriggerTime INTEGER,
                        categoryId INTEGER,
                        status TEXT NOT NULL,
                        repeatType TEXT NOT NULL,
                        repeatEndDate INTEGER,
                        advanceReminderType TEXT NOT NULL,
                        customAdvanceValue INTEGER NOT NULL,
                        customAdvanceUnit TEXT NOT NULL,
                        notifyVibrate INTEGER NOT NULL,
                        notifySound INTEGER NOT NULL,
                        alarmId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    WITH repaired_alarm_ids AS (
                        SELECT id, -ROW_NUMBER() OVER (ORDER BY id) AS repairedAlarmId
                        FROM reminders
                        WHERE alarmId <= 0 OR alarmId IN (
                            SELECT alarmId FROM reminders GROUP BY alarmId HAVING COUNT(*) > 1
                        )
                    )
                    INSERT INTO reminders_new (
                        id, title, note, triggerTime, nextTriggerTime, categoryId, status,
                        repeatType, repeatEndDate, advanceReminderType, customAdvanceValue,
                        customAdvanceUnit, notifyVibrate, notifySound, alarmId, createdAt,
                        updatedAt, completedAt
                    )
                    SELECT
                        reminders.id, title, note, triggerTime, nextTriggerTime, categoryId, status,
                        repeatType, repeatEndDate, advanceReminderType, customAdvanceValue,
                        customAdvanceUnit, notifyVibrate, notifySound,
                        COALESCE(repaired_alarm_ids.repairedAlarmId, alarmId),
                        createdAt, updatedAt, completedAt
                    FROM reminders
                    LEFT JOIN repaired_alarm_ids ON reminders.id = repaired_alarm_ids.id
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE reminders")
                db.execSQL("ALTER TABLE reminders_new RENAME TO reminders")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_reminders_alarmId ON reminders(alarmId)"
                )
            }
        }

        /**
         * 首次建库时插入内置分类（工作/生活/学习/健康）。
         * 用 execSQL 而不是走 DAO，避免在 RoomDatabase.Callback 里再拉一次 Hilt 依赖。
         */
        val defaultCategoriesCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val defaults = listOf(
                    Triple("工作", "#4A90E2", 0),
                    Triple("生活", "#7ED321", 1),
                    Triple("学习", "#F5A623", 2),
                    Triple("健康", "#D0021B", 3)
                )
                defaults.forEach { (name, color, order) ->
                    db.execSQL(
                        "INSERT INTO categories (name, colorHex, icon, sortOrder, isDefault) " +
                            "VALUES ('$name', '$color', NULL, $order, 1)"
                    )
                }
            }
        }
    }
}
