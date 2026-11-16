package com.jjz.assist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.time.LocalDate

/**
 * 仅用于 App 内的静态效果预览（桌面组件本身已经换成原生控件，不再用整张位图）。
 * 画面比例、圆角、透明度、排版都跟组件保持一致，方便调颜色时看效果。
 */
object WidgetRenderer {

    private val WEEK = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun render(context: Context, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val today = LocalDate.now()
        val until = PassStore.validUntil(context)
        val expired = until == null || today.isAfter(until)
        val pct = PassStore.percentFloat(context, today)
        val filled = !expired && pct > 0f

        val fillColor = PassStore.fillColor(context)
        val fillAlpha = PassStore.fillAlpha(context)
        val cardAlpha = (fillAlpha / 4).coerceIn(0, 255)
        val radius = minOf(w, h) * 0.22f
        val base = minOf(w, h).toFloat()

        canvas.drawBitmap(
            ShapeArt.art(context, w, h, radius, fillColor, fillAlpha, cardAlpha, if (filled) pct else 0f),
            0f, 0f, null
        )

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            setShadowLayer(base * 0.03f, 0f, base * 0.01f, 0x55000000)
        }

        val big = when {
            until == null -> "未设置"
            expired -> "已过期"
            else -> String.format("%.1f%%", pct)
        }
        val dateLine = when {
            until == null -> "点开设置有效期"
            else -> "至 ${until.monthValue}/${until.dayOfMonth}(${WEEK[until.dayOfWeek.value - 1]})"
        }

        // 与组件同一套盒子排布公式（此处单位是像素）
        val tPad = (h * 0.035f).coerceIn(3f, 6f)
        val tH = (h * 0.105f).coerceIn(11f, 16f)
        val pH = (h * 0.24f).coerceIn(18f, 34f)
        val dH = (h * 0.125f).coerceIn(11f, 17f)
        val eH = (h * 0.115f).coerceIn(10f, 15f)
        val qH = eH
        val qPad = (h * 0.055f).coerceIn(5f, 9f)
        val gap = (h * 0.018f).coerceAtLeast(1.5f)

        val quotaTop = h - qPad - qH
        val dateTop = quotaTop - gap - eH
        val bottomLimit = dateTop - gap
        val topLimit = tPad + tH + gap

        val surfaceY = if (expired) h * 0.42f else h * (1f - pct / 100f)
        val pctMax = (bottomLimit - pH - gap - dH).coerceAtLeast(topLimit)
        val pctTop = (surfaceY - pH * 0.12f - pH).coerceIn(topLimit, pctMax)
        val daysCap = dateTop - dH
        val daysMin = (pctTop + pH + gap).coerceAtLeast(surfaceY + h * 0.025f)
        val daysTop = daysMin.coerceAtMost(daysCap)

        drawLine(canvas, "进京证", w / 2f, tPad + tH / 2f, base * 0.19f, w * 0.86f, text, false, 0.12f, 215)
        drawLine(canvas, big, w / 2f, pctTop + pH / 2f, base * 0.34f, w * 0.88f, text, true, 0.02f, 255)

        if (filled) {
            drawLine(
                canvas, String.format("剩 %.1f 天", PassStore.remainingDaysFloat(context)), w / 2f,
                daysTop + dH / 2f, base * 0.135f, w * 0.88f, text, false, 0.02f, 238
            )
        }

        drawLine(canvas, dateLine, w / 2f, dateTop + eH / 2f, base * 0.10f, w * 0.86f, text, false, 0.02f, 215)
        drawLine(
            canvas, "年度余 ${PassStore.remainingQuota(context)} 次", w / 2f, quotaTop + qH / 2f,
            base * 0.10f, w * 0.86f, text, false, 0.02f, 180
        )

        return bmp
    }

    private fun drawLine(
        canvas: Canvas,
        value: String,
        cx: Float,
        cy: Float,
        maxSize: Float,
        maxWidth: Float,
        paint: Paint,
        bold: Boolean,
        letterSpacing: Float,
        alpha: Int
    ) {
        paint.typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        paint.letterSpacing = letterSpacing
        paint.alpha = alpha

        var size = maxSize
        paint.textSize = size
        var guard = 0
        while (paint.measureText(value) > maxWidth && size > 8f && guard < 200) {
            size -= 1f
            paint.textSize = size
            guard++
        }
        var ls = letterSpacing
        while (paint.measureText(value) > maxWidth && ls > -0.04f) {
            ls -= 0.01f
            paint.letterSpacing = ls
        }

        val metrics = paint.fontMetrics
        canvas.drawText(value, cx, cy - (metrics.ascent + metrics.descent) / 2f, paint)
        paint.letterSpacing = 0f
    }
}
