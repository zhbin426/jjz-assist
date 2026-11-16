package com.jjz.assist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * 取色板：横向 0~360 色相逐像素生成，纵向明暗（透明→黑）。
 * 取到的颜色就是手指按下去那块的实际像素颜色，不会出现"点蓝色出绿色"。
 */
class PaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onColorChanging: ((Int) -> Unit)? = null
    var onColorPicked: ((Int) -> Unit)? = null

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private var hueBitmap: Bitmap? = null
    private var hue = 210f
    private var value = 1f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // 每个像素列对应一个色相，横向平滑放大
        val strip = Bitmap.createBitmap(360, 1, Bitmap.Config.ARGB_8888)
        for (i in 0 until 360) {
            strip.setPixel(i, 0, Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f)))
        }
        hueBitmap = strip

        dimPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        hueBitmap?.let {
            canvas.drawBitmap(it, null, Rect(0, 0, width, height), bitmapPaint)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        val x = (hue / 360f) * width
        val y = (1f - value) * height
        canvas.drawCircle(x.coerceIn(13f, width - 13f), y.coerceIn(13f, height - 13f), 12f, markPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                hue = (event.x / width.toFloat()).coerceIn(0f, 0.999f) * 360f
                value = (1f - event.y / height.toFloat()).coerceIn(0.28f, 1f)
                invalidate()
                onColorChanging?.invoke(currentColor())
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                onColorPicked?.invoke(currentColor())
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun currentColor(): Int = Color.HSVToColor(floatArrayOf(hue, 1f, value))

    fun setColor(color: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue = hsv[0].coerceIn(0f, 359f)
        value = hsv[2].coerceIn(0.28f, 1f)
        invalidate()
    }
}
