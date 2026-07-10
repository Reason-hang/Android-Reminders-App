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
    version = 3,
    exportSchema = false
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
