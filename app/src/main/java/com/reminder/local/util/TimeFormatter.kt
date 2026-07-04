package com.reminder.local.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 按需求文档 4.2 节的规则格式化时间展示：
 * 今天 HH:mm / 明天 HH:mm / M月D日 HH:mm（本年内）/ YYYY年M月D日（跨年）
 */
object TimeFormatter {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")
    private val monthDay = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    private val fullDate = DateTimeFormatter.ofPattern("yyyy年M月d日")

    data class Formatted(val text: String, val isOverdue: Boolean)

    fun format(triggerTimeMillis: Long, now: Long = System.currentTimeMillis()): Formatted {
        val target = Instant.ofEpochMilli(triggerTimeMillis).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val targetDate: LocalDate = target.toLocalDate()

        val isOverdue = triggerTimeMillis < now

        val text = when {
            isOverdue -> {
                val base = when (targetDate) {
                    today -> "今天 ${target.format(hourMinute)}"
                    today.minusDays(1) -> "昨天 ${target.format(hourMinute)}"
                    else -> if (targetDate.year == today.year) target.format(monthDay) else target.format(fullDate)
                }
                "已过期 · $base"
            }
            targetDate == today -> "今天 ${target.format(hourMinute)}"
            targetDate == today.plusDays(1) -> "明天 ${target.format(hourMinute)}"
            targetDate.year == today.year -> target.format(monthDay)
            else -> target.format(fullDate)
        }

        return Formatted(text, isOverdue)
    }
}
