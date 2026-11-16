package com.jjz.assist

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId

/**
 * 两个闹钟：到期前 2 天早上 9:00 提醒；到期次日 0:10 把组件刷成 0%。
 * 靠系统唤醒，进程不需要常驻。
 */
object ReminderScheduler {

    private const val REQ_REMIND = 1001
    private const val REQ_REFRESH = 1002

    private const val ACTION_REMIND = "com.jjz.assist.action.REMIND"
    private const val ACTION_REFRESH = "com.jjz.assist.action.REFRESH"

    fun reschedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancel(context, am)

        val until = PassStore.validUntil(context) ?: return
        val remindAt = until.minusDays(2).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val refreshAt = until.plusDays(1).atTime(0, 10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        set(context, am, REQ_REMIND, ACTION_REMIND, remindAt)
        set(context, am, REQ_REFRESH, ACTION_REFRESH, refreshAt)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancel(context, am)
    }

    private fun cancel(context: Context, am: AlarmManager) {
        am.cancel(pending(context, REQ_REMIND, ACTION_REMIND))
        am.cancel(pending(context, REQ_REFRESH, ACTION_REFRESH))
    }

    private fun set(context: Context, am: AlarmManager, req: Int, action: String, at: Long) {
        if (at <= System.currentTimeMillis()) return
        val pi = pending(context, req, action)
        try {
            val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else if (exactAllowed) {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(context: Context, req: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            req,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
