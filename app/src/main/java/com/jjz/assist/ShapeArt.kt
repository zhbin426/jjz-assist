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
        liquidColor: Int,
        fillAlpha: Int,
        veilAlpha: Int,
        pct: Float
    ): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()
        // 提饱和：液面色先拉满一点再画，抵消玻璃层和半透明带来的发灰
        val fill = rich(liquidColor)
        val rect = RectF(0f, 0f, wf, hf)
        val round = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }

        canvas.save()
        canvas.clipPath(round)

        // 水面以上：中性半透明黑纱（不配颜色），壁纸换了对应区域跟着变。
        // 纱的浓度 = 液面不透明度 / 4，兼顾"跟壁纸走"和白字可读性。
        paint.color = Color.argb(veilAlpha.coerceIn(0, 255), 0, 0, 0)
        canvas.drawRect(0f, 0f, wf, hf, paint)

        // 液面：波浪水面。后浪半透明、相位错开，前浪实色渐变，静态不动画
        if (pct > 0f) {
            val liquidH = (hf * pct / 100f).coerceIn(1f, hf)
            val topY = hf - liquidH
            val amp = (hf * 0.022f).coerceIn(2f, 10f)

            paint.color = blend(fill, Color.WHITE, 0.18f, (fillAlpha * 0.45f).toInt())
            canvas.drawPath(wavePath(wf, hf, topY - amp * 0.9f, amp * 0.8f, 0.5f), paint)

            paint.shader = LinearGradient(
                0f, topY - amp, 0f, hf,
                blend(fill, Color.WHITE, 0.08f, fillAlpha),
                blend(fill, Color.BLACK, 0.18f, fillAlpha),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(wavePath(wf, hf, topY, amp, 0f), paint)
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
        // 用 getIdentifier 取贴图：仓库里可能没有这张二进制图，缺了就安静跳过玻璃层，
        // 不影响编译，也不影响底色和液面。
        val g = glass ?: runCatching {
            val resId = context.resources.getIdentifier("glass_rim", "drawable", context.packageName)
            if (resId == 0) null else BitmapFactory.decodeResource(context.resources, resId)
        }.getOrNull()?.also { glass = it }
        if (g != null) {
            // alpha 从 200 降到 150：玻璃边太实会把下面的液面饱和度冲淡
            paint.alpha = 150
            canvas.drawBitmap(g, null, android.graphics.Rect(0, 0, w, h), paint)
            paint.alpha = 255
        }

        return bmp
    }

    /** 波浪液面路径：1.25 个周期的正弦，采样 48 段足够平滑 */
    private fun wavePath(wf: Float, hf: Float, topY: Float, amp: Float, phase: Float): Path {
        val path = Path()
        path.moveTo(0f, hf + 2f)
        val steps = 48
        val k = (Math.PI * 2.0 * 1.25 / wf).toFloat()
        var i = 0
        while (i <= steps) {
            val x = wf * i / steps
            val y = topY + (Math.sin(x * k + phase * Math.PI * 2).toFloat() * amp)
            path.lineTo(x, y)
            i++
        }
        path.lineTo(wf, hf + 2f)
        path.close()
        return path
    }

    /** 饱和度 x1.55、亮度 +10%，上限 1.0。白/灰不受影响。 */
    private fun rich(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = (hsv[1] * 1.55f).coerceAtMost(1f)
        hsv[2] = (hsv[2] * 1.10f).coerceAtMost(1f)
        return Color.HSVToColor(hsv)
    }

    fun blend(color: Int, toward: Int, ratio: Float, alpha: Int): Int {
        val r = (Color.red(color) + (Color.red(toward) - Color.red(color)) * ratio).toInt()
        val g = (Color.green(color) + (Color.green(toward) - Color.green(color)) * ratio).toInt()
        val b = (Color.blue(color) + (Color.blue(toward) - Color.blue(color)) * ratio).toInt()
        return Color.argb(alpha.coerceIn(0, 255), r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
