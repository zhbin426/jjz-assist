package com.jjz.assist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 组件数字节拍：到点刷新组件，然后排下一个「显示值会变化」的时刻。
 * 系统的 updatePeriodMillis 只保证最早 30 分钟且会被 Doze 拖延，
 * 最后一天显示小时/分钟时靠它必然滞后，所以改用自排闹钟链。
 */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.jjz.assist.action.TICK") {
            PassWidgetProvider.updateAll(context)
        }
        ReminderScheduler.scheduleNextTick(context)
    }
}
