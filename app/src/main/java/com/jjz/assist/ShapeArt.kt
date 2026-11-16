package com.jjz.assist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * 卡片外观：底下一层同色玻璃 + 液面，最上面盖一张真实渲染的光学玻璃边缘贴图
 * （res/drawable-nodpi/glass_rim.png，只保留玻璃的高光倒角与折射，中间是透的）。
 * 这样既有真玻璃的光学质感，又能保留会变的液面。
 */
object ShapeArt {

    private var glass: Bitmap? = null

    fun art(
        context: Context,
        w: Int,
        h: Int,
        radius: Float,
        fillColor: Int,
        fillAlpha: Int,
        cardAlpha: Int,
        pct: Float
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()
        // 提饱和：用户选的颜色先拉满一点再画，抵消玻璃层和半透明带来的发灰
        val fill = rich(fillColor)
        val rect = RectF(0f, 0f, wf, hf)
        val round = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }

        canvas.save()
        canvas.clipPath(round)

        // 玻璃本体：同色、很淡
        paint.color = Color.argb(
            cardAlpha.coerceIn(0, 255),
            Color.red(fill),
            Color.green(fill),
            Color.blue(fill)
        )
        canvas.drawRect(0f, 0f, wf, hf, paint)

        // 液面
        if (pct > 0f) {
            val liquidH = (hf * pct / 100f).coerceIn(1f, hf)
            val topY = hf - liquidH
            paint.shader = LinearGradient(
                0f, topY, 0f, hf,
                blend(fill, Color.WHITE, 0.10f, fillAlpha),
                blend(fill, Color.BLACK, 0.30f, fillAlpha),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, topY, wf, hf, paint)
            paint.shader = null

            // 水面：一条干净的亮线 + 线下一点柔光
            val lineH = (hf * 0.010f).coerceAtLeast(2f)
            paint.color = blend(fill, Color.WHITE, 0.60f, (fillAlpha * 0.85f).toInt())
            canvas.drawRect(0f, topY, wf, topY + lineH, paint)
            paint.shader = LinearGradient(
                0f, topY + lineH, 0f, topY + hf * 0.10f,
                Color.argb(26, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, topY + lineH, wf, topY + hf * 0.10f, paint)
            paint.shader = null
        }

        // 顶光：极淡，让上半部分亮一口气
        paint.shader = LinearGradient(
            0f, 0f, 0f, hf * 0.45f,
            Color.argb(10, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, wf, hf, paint)
        paint.shader = null

        canvas.restore()

        // 真玻璃边缘贴图，直接拉伸到卡片大小
        val g = glass ?: runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.glass_rim)
        }.getOrNull()?.also { glass = it }
        if (g != null) {
            paint.alpha = 200
            canvas.drawBitmap(g, null, android.graphics.Rect(0, 0, w, h), paint)
            paint.alpha = 255
        }

        return bmp
    }

    /** 饱和度 x1.35、亮度 +6%，上限 1.0。白/灰不受影响。 */
    private fun rich(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = (hsv[1] * 1.35f).coerceAtMost(1f)
        hsv[2] = (hsv[2] * 1.06f).coerceAtMost(1f)
        return Color.HSVToColor(hsv)
    }

    fun blend(color: Int, toward: Int, ratio: Float, alpha: Int): Int {
        val r = (Color.red(color) + (Color.red(toward) - Color.red(color)) * ratio).toInt()
        val g = (Color.green(color) + (Color.green(toward) - Color.green(color)) * ratio).toInt()
        val b = (Color.blue(color) + (Color.blue(toward) - Color.blue(color)) * ratio).toInt()
        return Color.argb(alpha.coerceIn(0, 255), r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
