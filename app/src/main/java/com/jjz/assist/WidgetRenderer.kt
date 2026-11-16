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
        val veilAlpha = (fillAlpha / 8).coerceIn(0, 255)   // v1.7：水上区更透，跟壁纸走得更彻底
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

        val quotaTop = hDp - 8f - qH          // v1.8：静态贴底，与 XML marginBottom 8dp 对齐
        val dateTop = quotaTop - 2f - eH       // 与 XML marginTop 2dp 对齐
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

        // v1.8：与组件静态布局一致——天数数字固定在卡片正中，不跟水面浮动
        if (until == null || expired) {
            drawLine(canvas, big, w / 2f, h / 2f, sizeOf(pH) * 0.7f, wDp * 0.94f * density, text, true, 0f, Color.WHITE)
        } else {
            drawDaysNumber(
                canvas, PassStore.remainingDaysFloat(context), w / 2f, h / 2f,
                sizeOf(pH), sizeOf(pH) * 0.62f, text
            )
        }

        drawLine(canvas, dateLine, w / 2f, px(dateTop + eH / 2f), sizeOf(eH), wDp * 0.90f * density, text, false, 0.02f, Color.WHITE)
        drawLine(
            canvas, "余 ${PassStore.remainingQuota(context)}", w / 2f, px(quotaTop + qH / 2f),
            sizeOf(qH), wDp * 0.90f * density, text, false, 0.02f, Color.WHITE
        )

        return bmp
    }

    /** 剩余天数纯数字：整数大号 + 小数小号，共用基线，整体水平居中 */
    private fun drawDaysNumber(
        canvas: Canvas,
        value: Float,
        cx: Float,
        cy: Float,
        bigSize: Float,
        smallSize: Float,
        paint: Paint
    ) {
        val str = String.format("%.1f", value)
        val dot = str.indexOf('.')
        val intPart = if (dot < 0) str else str.substring(0, dot)
        val decPart = if (dot < 0) "" else str.substring(dot)

        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.letterSpacing = 0f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.LEFT

        paint.textSize = bigSize
        val mb = paint.fontMetrics
        paint.textSize = smallSize

        // 整体宽度不够时先收整数号
        var big = bigSize
        paint.textSize = big
        while (paint.measureText(intPart) + paint.measureText(decPart) > canvas.width * 0.92f && big > 12f) {
            big -= 1f
            paint.textSize = big
        }
        val widthInt = paint.measureText(intPart)
        val metricsBig = paint.fontMetrics
        paint.textSize = smallSize
        val widthDec = paint.measureText(decPart)

        val startX = cx - (widthInt + widthDec) / 2f
        val baseline = cy - (metricsBig.ascent + metricsBig.descent) / 2f

        paint.textSize = big
        canvas.drawText(intPart, startX, baseline, paint)
        paint.textSize = smallSize
        canvas.drawText(decPart, startX + widthInt, baseline, paint)

        paint.textAlign = Paint.Align.CENTER
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
