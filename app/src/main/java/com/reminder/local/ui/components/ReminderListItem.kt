package com.reminder.local.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reminder.local.domain.model.Category
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.util.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListItem(
    reminder: Reminder,
    category: Category?,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onSwipeComplete: () -> Unit,
    onRequestDelete: () -> Unit,
    isManagementMode: Boolean = false,
    isSelected: Boolean = false,
    onManageSelect: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    if (isManagementMode) {
        ReminderCard(
            reminder = reminder,
            category = category,
            onClick = onManageSelect,
            onToggleComplete = onManageSelect,
            isManagementMode = true,
            isSelected = isSelected,
            dragHandleModifier = dragHandleModifier,
            modifier = modifier
        )
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipeComplete()
                    false // 不让卡片真的消失在原地，列表会因为状态变化自然重排
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onRequestDelete()
                    false // 删除需要二次确认，这里先弹窗，不直接移除
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { SwipeBackground(dismissState.targetValue) }
    ) {
        ReminderCard(
            reminder = reminder,
            category = category,
            onClick = onClick,
            onToggleComplete = onToggleComplete,
            modifier = Modifier
        )
    }
}

@Composable
private fun SwipeBackground(target: androidx.compose.material3.SwipeToDismissBoxValue) {
    val (color, icon, alignment) = when (target) {
        androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd ->
            Triple(Color(0xFF43A047), Icons.Filled.CheckCircle, Alignment.CenterStart)
        androidx.compose.material3.SwipeToDismissBoxValue.EndToStart ->
            Triple(Color(0xFFD32F2F), Icons.Filled.Delete, Alignment.CenterEnd)
        else -> Triple(Color.Transparent, null, Alignment.Center)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        icon?.let {
            Icon(imageVector = it, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    category: Category?,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    isManagementMode: Boolean = false,
    isSelected: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val formatted = TimeFormatter.format(reminder.effectiveTime)
    val isDone = reminder.status == ReminderStatus.DONE
    val metadata = buildList {
        add(TimeFormatter.dateLabel(reminder.effectiveTime))
        add(TimeFormatter.timeLabel(reminder.effectiveTime))
        add(reminder.repeatType.label)
        if (reminder.advanceReminderType != com.reminder.local.domain.model.AdvanceReminderType.NONE) {
            add(
                if (reminder.advanceReminderType == com.reminder.local.domain.model.AdvanceReminderType.CUSTOM) {
                    "提前 ${reminder.customAdvanceValue}${reminder.customAdvanceUnit.label}"
                } else {
                    "提前 ${reminder.advanceReminderType.label.removeSuffix("前")}"
                }
            )
        }
        category?.let { add(it.name) }
    }.joinToString(" · ")

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isManagementMode && isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = if (isManagementMode) isSelected else isDone,
                onCheckedChange = { onToggleComplete() }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (formatted.isOverdue) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isManagementMode) {
                Text(
                    text = "⠿",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}
