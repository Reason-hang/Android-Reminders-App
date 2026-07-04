package com.reminder.local.ui.navigation

object Routes {
    const val LIST = "list"
    const val CATEGORY = "category"
    const val SETTINGS = "settings"

    const val EDIT_ARG_ID = "reminderId"
    const val EDIT_PATTERN = "edit?$EDIT_ARG_ID={$EDIT_ARG_ID}"

    /** reminderId = -1L 代表"新增"，否则代表编辑该 id 对应的提醒。 */
    fun edit(reminderId: Long = -1L): String = "edit?$EDIT_ARG_ID=$reminderId"
}
