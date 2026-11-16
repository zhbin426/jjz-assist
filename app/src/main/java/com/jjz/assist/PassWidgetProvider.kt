package com.jjz.assist

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate

/**
 * 组件全部用原生控件拼：ImageView 只负责卡片底色和液面（纯色/渐变，拉伸不影响），
 * 文字是真正的 TextView，清晰、不会被拉扁。
 */
class PassWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
    }

    companion object {

        private val WEEK = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PassWidgetProvider::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }

        fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
            manager.updateAppWidget(widgetId, buildViews(context, widgetId))
        }

        fun buildViews(context: Context, widgetId: Int): RemoteViews {
            val manager = AppWidgetManager.getInstance(context)
            val options = manager.getAppWidgetOptions(widgetId)
            val wDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 70
            val hDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 } ?: 130
            val density = context.resources.displayMetrics.density
            val wPx = (wDp * density).toInt().coerceIn(100, 1200)
            val hPx = (hDp * density).toInt().coerceIn(100, 2000)

            // v1.8：位图只画背景（卡片 / 半透明水上区 / 波浪液面），不含文字，
            // 拉伸不伤字；文字交给原生 TextView 静态布局，系统排版不压扁。
            val theme = PassStore.theme(context)
            val fillAlpha = PassStore.fillAlpha(context)
            val veilAlpha = (fillAlpha / 8).coerceIn(0, 255)
            val radius = minOf(wPx, hPx) * 0.22f
            val today = java.time.LocalDate.now()
            val until = PassStore.validUntil(context)
            val expired = until == null || today.isAfter(until)
            val pct = PassStore.percentFloat(context, today)
            val filled = !expired && pct > 0f

            val views = RemoteViews(context.packageName, R.layout.widget_pass)
            views.setImageViewBitmap(
                R.id.art,
                ShapeArt.art(context, wPx, hPx, radius, theme.liquid, fillAlpha, veilAlpha, if (filled) pct else 0f)
            )

            views.setTextViewText(R.id.title, "进京证")
            views.setInt(R.id.title, "setTextColor", Color.WHITE)

            if (until == null || expired) {
                views.setTextViewText(R.id.daysInt, if (until == null) "未设置" else "已过期")
                views.setTextViewText(R.id.daysDec, "")
            } else {
                val str = String.format("%.1f", PassStore.remainingDaysFloat(context))
                val dot = str.indexOf('.')
                views.setTextViewText(R.id.daysInt, if (dot < 0) str else str.substring(0, dot))
                views.setTextViewText(R.id.daysDec, if (dot < 0) "" else str.substring(dot))
            }
            views.setInt(R.id.daysInt, "setTextColor", Color.WHITE)
            views.setInt(R.id.daysDec, "setTextColor", Color.WHITE)

            val dateLine = when {
                until == null -> "点开设置有效期"
                else -> "至 ${until.monthValue}/${until.dayOfMonth}(${WEEK[until.dayOfWeek.value - 1]})"
            }
            views.setTextViewText(R.id.date, dateLine)
            views.setInt(R.id.date, "setTextColor", Color.WHITE)
            views.setTextViewText(R.id.quota, "余 ${PassStore.remainingQuota(context)}")
            views.setInt(R.id.quota, "setTextColor", Color.WHITE)

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.root, open)

            return views
        }
    }
}
