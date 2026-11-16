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

        val theme = PassStore.theme(context)
        val fillAlpha = PassStore.fillAlpha(context)
        val veilAlpha = (fillAlpha / 4).coerceIn(0, 255)
        val radius = minOf(w, h) * 0.22f

        canvas.drawBitmap(
            ShapeArt.art(context, w, h, radius, theme.liquid, fillAlpha, veilAlpha, if (filled) pct else 0f),
            0f, 0f, null
        )

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            setShadowLayer(minOf(w, h) * 0.03f, 0f, minOf(w, h) * 0.01f, 0x55000000)
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

        // 与组件同一套盒子排布公式。公式本身在 dp 域计算（保证和 Provider 完全一致），
        // 最后统一换算成像素落笔，避免常量 clamp 在 px 域失真。
        val density = context.resources.displayMetrics.density
        val hDp = h / density
        val wDp = w / density

        val tPad = (hDp * 0.035f).coerceIn(3f, 6f)
        val tH = (hDp * 0.105f).coerceIn(11f, 16f)
        val pH = (hDp * 0.26f).coerceIn(20f, 40f)
        val dH = (hDp * 0.125f).coerceIn(11f, 17f)
        val eH = (hDp * 0.115f).coerceIn(10f, 15f)
        val qH = eH
        val qPad = (hDp * 0.055f).coerceIn(5f, 9f)
        val gap = (hDp * 0.018f).coerceAtLeast(1.5f)

        val quotaTop = hDp - qPad - qH
        val dateTop = quotaTop - gap - eH
        val topLimit = tPad + tH + gap

        val surfaceDp = if (expired) hDp * 0.42f else hDp * (1f - pct / 100f)

        val daysCap = dateTop - dH - gap
        val daysFloor = topLimit + pH + gap
        val daysTop = minOf(
            maxOf(surfaceDp + hDp * 0.025f, daysFloor),
            maxOf(daysCap, topLimit)
        ).coerceAtLeast(topLimit)

        val pctAnchor = if (filled) daysTop else dateTop
        val pctMax = (pctAnchor - gap - pH).coerceAtLeast(topLimit)
        val pctTop = (surfaceDp - pH * 1.12f).coerceIn(topLimit, pctMax)

        // 字号跟着盒子高度走（≈autoSize 收进盒子后的结果），宽度不够时 drawLine 内部再缩
        fun px(v: Float) = v * density
        fun sizeOf(boxDp: Float) = px(boxDp) * 0.80f

        drawLine(canvas, "进京证", w / 2f, px(tPad + tH / 2f), sizeOf(tH), wDp * 0.86f * density, text, false, 0.12f, Color.WHITE)
        drawLine(canvas, big, w / 2f, px(pctTop + pH / 2f), sizeOf(pH), wDp * 0.94f * density, text, true, 0f, Color.argb(255, 255, 255, 255))

        if (filled) {
            drawLine(
                canvas, String.format("剩 %.1f 天", PassStore.remainingDaysFloat(context)), w / 2f,
                px(daysTop + dH / 2f), sizeOf(dH), wDp * 0.92f * density, text, false, 0.02f, Color.WHITE
            )
        }

        drawLine(canvas, dateLine, w / 2f, px(dateTop + eH / 2f), sizeOf(eH), wDp * 0.90f * density, text, false, 0.02f, Color.WHITE)
        drawLine(
            canvas, "年度余 ${PassStore.remainingQuota(context)} 次", w / 2f, px(quotaTop + qH / 2f),
            sizeOf(qH), wDp * 0.90f * density, text, false, 0.02f, Color.WHITE
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
        color: Int
    ) {
        paint.typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        paint.letterSpacing = letterSpacing
        paint.color = color

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
