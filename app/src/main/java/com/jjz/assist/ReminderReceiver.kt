package com.jjz.assist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.jjz.assist.action.REMIND") {
            notify(context)
        }
        PassWidgetProvider.updateAll(context)
        ReminderScheduler.reschedule(context)
    }

    private fun notify(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL, "进京证到期提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "进京证即将到期时提醒续办" }
            nm.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val until = PassStore.validUntil(context)?.toString() ?: "未知"
        val left = PassStore.remainingDays(context)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        builder.setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("该办进京证了")
            .setContentText("当前进京证 $until 到期，还剩 $left 天，记得续办。")
            .setAutoCancel(true)
            .setContentIntent(open)

        nm.notify(NOTIFY_ID, builder.build())
    }

    companion object {
        private const val CHANNEL = "jjz_reminder"
        private const val NOTIFY_ID = 1001
    }
}
