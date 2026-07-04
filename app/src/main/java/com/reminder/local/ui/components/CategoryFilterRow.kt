package com.reminder.local.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reminder.local.domain.model.ALL_CATEGORY_FILTER_ID
import com.reminder.local.domain.model.Category
import com.reminder.local.domain.model.UNCATEGORIZED_FILTER_ID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterRow(
    categories: List<Category>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        item {
            FilterChip(
                selected = selectedId == ALL_CATEGORY_FILTER_ID,
                onClick = { onSelect(ALL_CATEGORY_FILTER_ID) },
                label = { Text("全部") },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item {
            FilterChip(
                selected = selectedId == UNCATEGORIZED_FILTER_ID,
                onClick = { onSelect(UNCATEGORIZED_FILTER_ID) },
                label = { Text("未分类") },
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
