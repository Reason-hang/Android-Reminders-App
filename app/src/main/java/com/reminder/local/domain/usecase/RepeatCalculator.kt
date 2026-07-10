package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.RepeatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 计算重复提醒的下一次触发时间。
 *
 * 月份边界策略（对应需求文档"查漏补缺 ②"）：每次都以 baseTriggerTime 里的原始"日"作为目标，
 * 而不是以上一次已经被裁剪过的"日"为基准，这样 1月31日 -> 2月28日(裁剪) -> 3月31日(恢复) ，
 * 不会一直卡在 28/30 日，也不会产生日期漂移。
 */
object RepeatCalculator {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val beijingZone: ZoneId = ZoneId.of("Asia/Shanghai")

    /**
     * @param baseTriggerTime 用户最初设置的触发时间，永远不变，用来提供"目标日/时/分"模板。
     * @param fromTime        刚刚触发（或者需要往后推算）的时间点。
     * @param type            重复类型，NONE 时返回 null。
     */
    fun computeNext(baseTriggerTime: Long, fromTime: Long, type: RepeatType): Long? {
        if (type == RepeatType.NONE) return null
        val from = Instant.ofEpochMilli(fromTime).atZone(zone)
        return when (type) {
            RepeatType.HOURLY -> from.plusHours(1).toInstant().toEpochMilli()
            RepeatType.DAILY -> from.plusDays(1).toInstant().toEpochMilli()
            RepeatType.WORKDAYS -> computeNextWorkday(fromTime)
            RepeatType.WEEKLY -> from.plusWeeks(1).toInstant().toEpochMilli()
            RepeatType.WEEKLY_SUNDAY -> from.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                .toInstant().toEpochMilli()
            RepeatType.WEEKEND -> {
                val nextWeekendDay = if (from.dayOfWeek == DayOfWeek.SATURDAY) {
                    DayOfWeek.SUNDAY
                } else {
                    DayOfWeek.SATURDAY
                }
                from.with(TemporalAdjusters.next(nextWeekendDay)).toInstant().toEpochMilli()
            }
            RepeatType.BIWEEKLY -> from.plusWeeks(2).toInstant().toEpochMilli()
            RepeatType.MONTHLY -> computeNextMonth(baseTriggerTime, fromTime, 1)
            RepeatType.QUARTERLY -> computeNextMonth(baseTriggerTime, fromTime, 3)
            RepeatType.SEMIANNUALLY -> computeNextMonth(baseTriggerTime, fromTime, 6)
            RepeatType.YEARLY -> computeNextMonth(baseTriggerTime, fromTime, 12)
            RepeatType.NONE -> null
        }
    }

    private fun computeNextMonth(baseTriggerTime: Long, fromTime: Long, monthStep: Long): Long {
        val baseDay = Instant.ofEpochMilli(baseTriggerTime).atZone(zone).dayOfMonth
        val from = Instant.ofEpochMilli(fromTime).atZone(zone)
        val nextMonth = from.plusMonths(monthStep)
        val lastDayOfNextMonth = nextMonth.toLocalDate().lengthOfMonth()
        val targetDay = minOf(baseDay, lastDayOfNextMonth)
        return nextMonth.withDayOfMonth(targetDay).toInstant().toEpochMilli()
    }

    private fun computeNextWorkday(fromTime: Long): Long {
        var next = Instant.ofEpochMilli(fromTime).atZone(beijingZone).plusDays(1)
        while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
            next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli()
    }
}
