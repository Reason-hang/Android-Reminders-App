package com.reminder.local.data.db

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseSchemaContractTest {

    @Test
    fun versionThreeSchemaIsKeptForTheActiveVersionThreeToFourMigration() {
        val schema = File("schemas/com.reminder.local.data.db.AppDatabase/3.json")

        assertTrue(schema.exists())
        assertTrue(schema.readText().contains("\"version\": 3"))
    }

    @Test
    fun migrationRepairsEveryInvalidAlarmIdWithASequentialNegativeInt() {
        val source = File("src/main/java/com/reminder/local/data/db/AppDatabase.kt").readText()

        assertTrue(source.contains("WHERE alarmId <= 0 OR alarmId IN"))
        assertTrue(source.contains("ROW_NUMBER() OVER (ORDER BY id)"))
        assertFalse(source.contains("THEN -id"))
        assertFalse(source.contains("id % 2147483646"))
    }
}
