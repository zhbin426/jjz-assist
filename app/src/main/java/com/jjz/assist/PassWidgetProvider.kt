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

            // 组件与 App 内预览走同一条渲染管线：整张画面（卡片 / 波浪 / 全部文字）
            // 按组件实际像素 1:1 画进位图，RemoteViews 只挂一张 ImageView。
            // 位图尺寸=组件尺寸，fitXY 不产生拉伸；文字是 Canvas 按 px 画的，清晰不糊。
            // 彻底绕开 MIUI 启动器对 RemoteViews 文本/布局 API 的兼容性差异
            // （v2.2 三行消失、v1.4/v1.5 真机四行消失都出在这一层）。
            val views = RemoteViews(context.packageName, R.layout.widget_pass)
            views.setImageViewBitmap(R.id.art, WidgetRenderer.render(context, wPx, hPx))

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
