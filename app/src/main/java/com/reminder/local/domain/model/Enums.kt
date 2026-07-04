package com.reminder.local.domain.model

import androidx.compose.ui.graphics.Color

/**
 * 优先级：只影响列表排序和颜色展示，不影响通知方式。
 */
enum class Priority(val label: String, val color: Color, val sortWeight: Int) {
    HIGH("高", Color(0xFFD0021B), 0),
    MEDIUM("中", Color(0xFFF5A623), 1),
    LOW("低", Color(0xFF9B9B9B), 2)
}

/**
 * 提醒状态机：
 * PENDING（待触发）--用户勾选--> DONE
 * PENDING --时间已过未触发（App重启检测）--> EXPIRED
 * 重复提醒触发后不进入 DONE/EXPIRED，而是原地滚动 nextTriggerTime，
 * 只有用户主动"停止重复"或超过 repeatEndDate，才变为 DONE。
 */
enum class ReminderStatus {
    PENDING, DONE, EXPIRED
}

enum class RepeatType(val label: String) {
    NONE("不重复"),
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月")
}

/**
 * 对重复提醒执行"完成"或"删除"操作时，需要区分作用范围。
 */
enum class RepeatActionScope {
    ONCE,   // 仅本次：跳到下一次触发，不影响后续重复
    ALL     // 全部：彻底停止该提醒的所有后续触发
}
