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

            val views = RemoteViews(context.packageName, R.layout.widget_pass)

            val today = LocalDate.now()
            val until = PassStore.validUntil(context)
            val expired = until == null || today.isAfter(until)
            val pct = PassStore.percentFloat(context, today)
            val filled = !expired && pct > 0f

            val fillColor = PassStore.fillColor(context)
            val fillAlpha = PassStore.fillAlpha(context)
            // 上半部分（卡片底）透明度 = 下半部分（液面）的四分之一
            val cardAlpha = (fillAlpha / 4).coerceIn(0, 255)
            val radius = minOf(wPx, hPx) * 0.22f

            views.setImageViewBitmap(
                R.id.art,
                ShapeArt.art(
                    context, wPx, hPx, radius, fillColor, fillAlpha, cardAlpha, if (filled) pct else 0f
                )
            )

            views.setTextViewText(R.id.title, "进京证")
            views.setInt(R.id.title, "setTextColor", Color.argb(215, 255, 255, 255))

            val big = when {
                until == null -> "未设置"
                expired -> "已过期"
                else -> String.format("%.1f%%", pct)
            }
            views.setTextViewText(R.id.pct, big)

            // 排版：四行字都是固定高度盒子，全部顶部对齐、由同一套公式定位。
            // 盒子高度真实生效（autoSize 会把字号缩进盒子里），所以盒子和盒子之间
            // 只要公式保证不交叉，就一定不会重叠。
            val tPad = (hDp * 0.035f).coerceIn(3f, 6f)          // 标题上留白
            val tH = (hDp * 0.105f).coerceIn(11f, 16f)          // 标题盒
            val pH = (hDp * 0.24f).coerceIn(18f, 34f)           // 百分比盒
            val dH = (hDp * 0.125f).coerceIn(11f, 17f)          // 天数盒
            val eH = (hDp * 0.115f).coerceIn(10f, 15f)          // 到期日盒
            val qH = eH                                          // 年度余盒
            val qPad = (hDp * 0.055f).coerceIn(5f, 9f)          // 底部留白
            val gap = (hDp * 0.018f).coerceAtLeast(1.5f)        // 盒间最小缝

            val quotaTop = hDp - qPad - qH
            val dateTop = quotaTop - gap - eH
            val bottomLimit = dateTop - gap
            val topLimit = tPad + tH + gap

            val surfaceDp = if (expired) hDp * 0.42f else hDp * (1f - pct / 100f)

            // 百分比：浮在水平面上方；上不越过标题，下不压到天数+底部两行
            val pctMax = (bottomLimit - pH - gap - dH).coerceAtLeast(topLimit)
            val pctTop = (surfaceDp - pH * 0.12f - pH).coerceIn(topLimit, pctMax)

            // 天数想贴着水面，但最多只能走到到期日上方；保证 ≥ 百分比盒下沿
            val daysCap = dateTop - dH
            val daysMin = (pctTop + pH + gap).coerceAtLeast(surfaceDp + hDp * 0.025f)
            val daysTop = daysMin.coerceAtMost(daysCap)

            fun dp(v: Float) = (v * density).toInt()
            views.setViewPadding(R.id.title, 0, dp(tPad), 0, 0)
            views.setViewLayoutHeight(R.id.pct, dp(pH).toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewPadding(R.id.pct, 0, dp(pctTop), 0, 0)
            views.setViewLayoutHeight(R.id.days, dp(dH).toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewPadding(R.id.days, 0, dp(daysTop), 0, 0)
            views.setViewLayoutHeight(R.id.date, dp(eH).toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewPadding(R.id.date, 0, dp(dateTop), 0, 0)
            views.setViewLayoutHeight(R.id.quota, dp(qH).toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewPadding(R.id.quota, 0, dp(quotaTop), 0, 0)

            if (filled) {
                views.setTextViewText(
                    R.id.days,
                    String.format("剩 %.1f 天", PassStore.remainingDaysFloat(context))
                )
                views.setViewVisibility(R.id.days, View.VISIBLE)
                views.setInt(R.id.days, "setTextColor", Color.argb(238, 255, 255, 255))
            } else {
                views.setViewVisibility(R.id.days, View.GONE)
            }

            val dateLine = when {
                until == null -> "点开设置有效期"
                else -> "至 ${until.monthValue}/${until.dayOfMonth}(${WEEK[until.dayOfWeek.value - 1]})"
            }
            views.setTextViewText(R.id.date, dateLine)
            views.setInt(R.id.date, "setTextColor", Color.argb(215, 255, 255, 255))

            views.setTextViewText(R.id.quota, "年度余 ${PassStore.remainingQuota(context)} 次")
            views.setInt(R.id.quota, "setTextColor", Color.argb(180, 255, 255, 255))

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
