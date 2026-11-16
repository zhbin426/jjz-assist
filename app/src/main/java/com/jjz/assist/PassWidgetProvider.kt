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

            val theme = PassStore.theme(context)
            val fillAlpha = PassStore.fillAlpha(context)
            // 水面以上黑纱浓度 = 液面不透明度 / 4（沿用"背景=前景 1/4"口径，但改成中性色跟壁纸走）
            val veilAlpha = (fillAlpha / 4).coerceIn(0, 255)
            val radius = minOf(wPx, hPx) * 0.22f

            views.setImageViewBitmap(
                R.id.art,
                ShapeArt.art(
                    context, wPx, hPx, radius, theme.liquid,
                    fillAlpha, veilAlpha, if (filled) pct else 0f
                )
            )

            views.setTextViewText(R.id.title, "进京证")

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
            val pH = (hDp * 0.26f).coerceIn(20f, 40f)           // 百分比盒（v2.3 放大）
            val dH = (hDp * 0.125f).coerceIn(11f, 17f)          // 天数盒
            val eH = (hDp * 0.115f).coerceIn(10f, 15f)          // 到期日盒
            val qH = eH                                         // 年度余盒
            val qPad = (hDp * 0.055f).coerceIn(5f, 9f)          // 底部留白
            val gap = (hDp * 0.018f).coerceAtLeast(1.5f)        // 盒间最小缝

            val quotaTop = hDp - qPad - qH
            val dateTop = quotaTop - gap - eH
            val topLimit = tPad + tH + gap

            val surfaceDp = if (expired) hDp * 0.42f else hDp * (1f - pct / 100f)

            // 天数：跟着水面往下走，但走到「到期日上沿再留一个缝」就钉死，不再继续下降。
            // daysFloor 保证它永远压不到百分比盒；两者冲突时优先钉住天数、把百分比往上顶。
            val daysCap = dateTop - dH - gap
            val daysFloor = topLimit + pH + gap
            val daysTop = minOf(
                maxOf(surfaceDp + hDp * 0.025f, daysFloor),
                maxOf(daysCap, topLimit)
            ).coerceAtLeast(topLimit)

            // 百分比：浮在水面上方，下沿始终让开天数盒（天数不显示时让开到期日）
            val pctAnchor = if (filled) daysTop else dateTop
            val pctMax = (pctAnchor - gap - pH).coerceAtLeast(topLimit)
            val pctTop = (surfaceDp - pH * 1.12f).coerceIn(topLimit, pctMax)

            fun dp(v: Float) = (v * density).toInt()
            views.setViewPadding(R.id.title, 0, dp(tPad), 0, 0)
            // 铁律：setViewLayoutHeight 收的是「dp 数值 + COMPLEX_UNIT_DIP」，
            // 绝对不能再先乘 density。v2.2 剩余天数/到期日/年度余次三行整体消失、
            // 百分比被压小，就是这里双重换算把盒子撑到 3 倍多高，paddingTop 一挤，
            // 内容区变负，文字被整行裁掉。
            views.setViewLayoutHeight(R.id.pct, pH, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewPadding(R.id.pct, 0, dp(pctTop), 0, 0)
            views.setViewLayoutHeight(R.id.days, dH, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewPadding(R.id.days, 0, dp(daysTop), 0, 0)
            views.setViewLayoutHeight(R.id.date, eH, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewPadding(R.id.date, 0, dp(dateTop), 0, 0)
            views.setViewLayoutHeight(R.id.quota, qH, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewPadding(R.id.quota, 0, dp(quotaTop), 0, 0)

            // 字体统一白色（用户口径 v1.3），透明度分行递减
            views.setInt(R.id.title, "setTextColor", Color.WHITE)
            views.setInt(R.id.pct, "setTextColor", Color.argb(255, 255, 255, 255))

            if (filled) {
                views.setTextViewText(
                    R.id.days,
                    String.format("剩 %.1f 天", PassStore.remainingDaysFloat(context))
                )
                views.setViewVisibility(R.id.days, View.VISIBLE)
                views.setInt(R.id.days, "setTextColor", Color.WHITE)
            } else {
                views.setViewVisibility(R.id.days, View.GONE)
            }

            val dateLine = when {
                until == null -> "点开设置有效期"
                else -> "至 ${until.monthValue}/${until.dayOfMonth}(${WEEK[until.dayOfWeek.value - 1]})"
            }
            views.setTextViewText(R.id.date, dateLine)
            views.setInt(R.id.date, "setTextColor", Color.WHITE)

            views.setTextViewText(R.id.quota, "年度余 ${PassStore.remainingQuota(context)} 次")
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
