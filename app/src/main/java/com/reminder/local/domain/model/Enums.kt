package com.reminder.local.domain.model

/** 旧版本数据库兼容字段；界面和业务逻辑不再使用该字段。 */
enum class Priority {
    HIGH,
    MEDIUM,
    LOW
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
    HOURLY("每小时"),
    EVERY_FIVE_HOURS("每隔5小时"),
    DAILY("每天"),
    WORKDAYS("工作日"),
    WEEKLY("每周"),
    WEEKLY_SUNDAY("每周日"),
    WEEKEND("周末"),
    BIWEEKLY("每两周"),
    MONTHLY("每月"),
    QUARTERLY("每3个月"),
    SEMIANNUALLY("每6个月"),
    YEARLY("每年")
}

enum class AdvanceReminderType(val label: String) {
    NONE("无"),
    FIVE_MINUTES("5分钟前"),
    TEN_MINUTES("10分钟前"),
    FIFTEEN_MINUTES("15分钟前"),
    THIRTY_MINUTES("30分钟前"),
    ONE_HOUR("一小时前"),
    TWO_HOURS("二小时前"),
    THREE_HOURS("三小时前"),
    ONE_DAY("一天前"),
    TWO_DAYS("两天前"),
    ONE_WEEK("一周前"),
    TWO_WEEKS("两周前"),
    ONE_MONTH("一个月前"),
    CUSTOM("自定义")
}

enum class AdvanceReminderUnit(val label: String) {
    MINUTES("分钟"),
    HOURS("小时"),
    DAYS("天"),
    WEEKS("周"),
    MONTHS("个月")
}

/**
 * 对重复提醒执行"完成"或"删除"操作时，需要区分作用范围。
 */
enum class RepeatActionScope {
    ONCE,   // 仅本次：跳到下一次触发，不影响后续重复
    ALL     // 全部：彻底停止该提醒的所有后续触发
}
