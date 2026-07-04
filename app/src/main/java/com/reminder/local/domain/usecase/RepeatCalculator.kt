package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.RepeatType
import java.time.Instant
import java.time.ZoneId

/**
 * 计算重复提醒的下一次触发时间。
 *
 * 月份边界策略（对应需求文档"查漏补缺 ②"）：每次都以 baseTriggerTime 里的原始"日"作为目标，
 * 而不是以上一次已经被裁剪过的"日"为基准，这样 1月31日 -> 2月28日(裁剪) -> 3月31日(恢复) ，
 * 不会一直卡在 28/30 日，也不会产生日期漂移。
 */
object RepeatCalculator {

    private val zone: ZoneId = ZoneId.systemDefault()

    /**
     * @param baseTriggerTime 用户最初设置的触发时间，永远不变，用来提供"目标日/时/分"模板。
     * @param fromTime        刚刚触发（或者需要往后推算）的时间点。
     * @param type            重复类型，NONE 时返回 null。
     */
    fun computeNext(baseTriggerTime: Long, fromTime: Long, type: RepeatType): Long? {
        if (type == RepeatType.NONE) return null
        val from = Instant.ofEpochMilli(fromTime).atZone(zone)
        return when (type) {
            RepeatType.DAILY -> from.plusDays(1).toInstant().toEpochMilli()
            RepeatType.WEEKLY -> from.plusWeeks(1).toInstant().toEpochMilli()
            RepeatType.MONTHLY -> {
                val baseDay = Instant.ofEpochMilli(baseTriggerTime).atZone(zone).dayOfMonth
                val nextMonth = from.plusMonths(1)
                val lastDayOfNextMonth = nextMonth.toLocalDate().lengthOfMonth()
                val targetDay = minOf(baseDay, lastDayOfNextMonth)
                nextMonth.withDayOfMonth(targetDay).toInstant().toEpochMilli()
            }
            RepeatType.NONE -> null
        }
    }
}
