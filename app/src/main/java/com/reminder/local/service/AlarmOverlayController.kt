package com.reminder.local.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AlarmOverlayShowResult {
    SHOWN,
    PERMISSION_MISSING,
    ADD_VIEW_FAILED
}

/**
 * 解锁态强提醒视图。它由前台服务持有，不依赖后台启动 Activity，因而不受
 * FullScreenIntent 在解锁状态被 SystemUI 降级为横幅的限制。
 */
class AlarmOverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null

    fun show(
        title: String,
        note: String?,
        kind: AlarmAlertKind,
        occurrenceTime: Long,
        onClose: () -> Unit,
        onSnooze: () -> Unit,
        onDone: () -> Unit
    ): AlarmOverlayShowResult {
        if (!android.provider.Settings.canDrawOverlays(appContext)) {
            return AlarmOverlayShowResult.PERMISSION_MISSING
        }
        dismiss()
        val view = buildView(title, note, kind, occurrenceTime, onClose, onSnooze, onDone)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.FILL
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        return runCatching {
            windowManager.addView(view, params)
            overlayView = view
            AlarmOverlayShowResult.SHOWN
        }.onFailure {
            Log.e(TAG, "添加解锁强提醒悬浮页失败", it)
        }.getOrDefault(AlarmOverlayShowResult.ADD_VIEW_FAILED)
    }

    fun dismiss(): Boolean {
        val view = overlayView ?: return false
        overlayView = null
        return runCatching {
            windowManager.removeViewImmediate(view)
            true
        }.onFailure {
            Log.e(TAG, "移除解锁强提醒悬浮页失败", it)
        }.getOrDefault(false)
    }

    private fun buildView(
        title: String,
        note: String?,
        kind: AlarmAlertKind,
        occurrenceTime: Long,
        onClose: () -> Unit,
        onSnooze: () -> Unit,
        onDone: () -> Unit
    ): View {
        val content = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(48), dp(28), dp(32))
            setBackgroundColor(Color.WHITE)
        }
        content.addView(textView(kindLabel(kind), 18f, Color.rgb(46, 90, 172), Typeface.BOLD))
        content.addView(space(24))
        content.addView(
            textView(
                title.trim().ifBlank { "提醒事项" },
                32f,
                Color.rgb(25, 25, 28),
                Typeface.BOLD
            )
        )
        content.addView(space(16))
        content.addView(
            textView(
                note?.trim().orEmpty().ifBlank { "提醒时间到了" },
                19f,
                Color.rgb(75, 75, 82),
                Typeface.NORMAL
            )
        )
        content.addView(space(20))
        content.addView(
            textView(
                "提醒时间  ${formatTime(occurrenceTime)}",
                18f,
                Color.rgb(75, 75, 82),
                Typeface.NORMAL
            )
        )
        content.addView(
            Space(appContext),
            LinearLayout.LayoutParams(1, 0, 1f)
        )
        content.addView(actionButton("关闭", onClose, emphasized = false))
        content.addView(space(10))
        content.addView(actionButton("稍后提醒 10 分钟", onSnooze, emphasized = true))
        content.addView(space(10))
        content.addView(actionButton("标为完成", onDone, emphasized = false))

        return content
    }

    private fun textView(text: String, sizeSp: Float, color: Int, style: Int): TextView =
        TextView(appContext).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, style)
        }

    private fun actionButton(label: String, action: () -> Unit, emphasized: Boolean): Button =
        Button(appContext).apply {
            text = label
            textSize = 17f
            isAllCaps = false
            setTextColor(if (emphasized) Color.WHITE else Color.rgb(46, 90, 172))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(
                    if (emphasized) Color.rgb(46, 90, 172) else Color.rgb(237, 241, 249)
                )
            }
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        }

    private fun space(heightDp: Int): Space = Space(appContext).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    private fun kindLabel(kind: AlarmAlertKind): String = when (kind) {
        AlarmAlertKind.DUE -> "到点提醒"
        AlarmAlertKind.ADVANCE -> "提前提醒"
        AlarmAlertKind.SNOOZE -> "稍后提醒"
    }

    private fun formatTime(occurrenceTime: Long): String = runCatching {
        Instant.ofEpochMilli(occurrenceTime)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))
    }.getOrDefault("当前")

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "AlarmOverlayController"
    }
}
