package com.reminder.local.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val colorHex: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false
)
