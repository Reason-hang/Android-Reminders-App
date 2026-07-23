package com.reminder.local.data.db

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseSchemaContractTest {

    @Test
    fun versionThreeSchemaIsKeptForTheActiveVersionThreeToFourMigration() {
        val schema = File("schemas/com.reminder.local.data.db.AppDatabase/3.json")

        assertTrue(schema.exists())
        assertTrue(schema.readText().contains("\"version\": 3"))
    }
}
