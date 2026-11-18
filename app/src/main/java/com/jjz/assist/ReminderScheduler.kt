package com.jjz.assist

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 三个闹钟：到期前 2 天早上 9:00 提醒；到期次日 0:10 把组件刷成 0%；
 * 以及组件数字节拍 TICK——按「下一个显示值会变化的时刻」自排，
 * 避免 updatePeriodMillis（≥30 分钟且不精确）造成的显示滞后。
 */
object ReminderScheduler {

    private const val REQ_REMIND = 1001
    private const val REQ_REFRESH = 1002
    private const val REQ_TICK = 1003

    private const val ACTION_REMIND = "com.jjz.assist.action.REMIND"
    private const val ACTION_REFRESH = "com.jjz.assist.action.REFRESH"
    private const val ACTION_TICK = "com.jjz.assist.action.TICK"

    fun reschedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancel(context, am)

        val until = PassStore.validUntil(context) ?: return
        val remindAt = until.minusDays(2).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val refreshAt = until.plusDays(1).atTime(0, 10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        set(context, am, REQ_REMIND, ACTION_REMIND, remindAt)
        set(context, am, REQ_REFRESH, ACTION_REFRESH, refreshAt)
        scheduleNextTick(context)
    }

    /**
     * 排下一个「组件显示值会变化」的时刻（v1.9 三档显示对应）:
     *  - ≥24h:天数一位小数,每 0.1 天(2.4h)变一次
     *  - <24h:小时一位小数,每 0.1 小时(6min)变一次
     *  - <1h :分钟整数,每分钟变一次
     * 收到 TICK 后由 TickReceiver 刷新组件再重排,形成链条。
     */
    fun scheduleNextTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pending(context, REQ_TICK, TickReceiver::class.java, ACTION_TICK))

        val until = PassStore.validUntil(context) ?: return
        val end = until.plusDays(1).atStartOfDay(ZoneId.systemDefault())
        val secs = Duration.between(ZonedDateTime.now(), end).seconds
        if (secs <= 0) return

        // 下一个下降沿：secs 跨过 step 的整数倍时显示值变化。
        // 跨档位（24h/1h）的时刻也要贴上去，避免档位切换滞后。
        val waitSecs = when {
            secs > 86_400L -> minOf(secs % 8_640L, secs - 86_400L).takeIf { it > 0 } ?: 8_640L
            secs > 3_600L -> (secs % 360L).takeIf { it > 0 } ?: 360L
            else -> (secs % 60L).takeIf { it > 0 } ?: 60L
        }
        set(context, am, REQ_TICK, TickReceiver::class.java, ACTION_TICK,
            System.currentTimeMillis() + waitSecs * 1000L)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancel(context, am)
    }

    private fun cancel(context: Context, am: AlarmManager) {
        am.cancel(pending(context, REQ_REMIND, ReminderReceiver::class.java, ACTION_REMIND))
        am.cancel(pending(context, REQ_REFRESH, ReminderReceiver::class.java, ACTION_REFRESH))
        am.cancel(pending(context, REQ_TICK, TickReceiver::class.java, ACTION_TICK))
    }

    private fun set(context: Context, am: AlarmManager, req: Int, action: String, at: Long) =
        set(context, am, req, ReminderReceiver::class.java, action, at)

    private fun set(
        context: Context,
        am: AlarmManager,
        req: Int,
        cls: Class<*>,
        action: String,
        at: Long
    ) {
        if (at <= System.currentTimeMillis()) return
        val pi = pending(context, req, cls, action)
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
        pending(context, req, ReminderReceiver::class.java, action)

    private fun pending(context: Context, req: Int, cls: Class<*>, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            req,
            Intent(context, cls).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
