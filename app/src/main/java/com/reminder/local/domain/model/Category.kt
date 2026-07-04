package com.reminder.local.domain.model

data class Category(
    val id: Long = 0L,
    val name: String,
    val colorHex: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false
)

/** 分类筛选栏里"未分类"不是一个真实 Category 实体，用这个常量表示 categoryId == null 的筛选态。 */
const val UNCATEGORIZED_FILTER_ID = -1L

/** 分类筛选栏里"全部"筛选态。 */
const val ALL_CATEGORY_FILTER_ID = -2L
