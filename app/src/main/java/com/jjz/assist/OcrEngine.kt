package com.jjz.assist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * 识别走三路：原图、放大图、灰度增强图。
 * 同一张图跑三遍取并集，小字和截图里压扁的字更容易被读出来。
 */
object OcrEngine {

    private val client: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    fun recognize(
        context: Context,
        uri: Uri,
        onDone: (parsed: OcrParser.Parsed?, rawText: String, error: String) -> Unit
    ) {
        val base = ImageLoader.decode(context, uri, 2200)
        if (base == null) {
            onDone(null, "", "图片打不开，请从相册重新选一张，或确认相册权限")
            return
        }

        val variants = mutableListOf(base)
        runCatching { upscale(base, 1.8f) }.getOrNull()?.let { if (it != base) variants.add(it) }
        runCatching { enhance(base) }.getOrNull()?.let { variants.add(it) }

        runVariants(variants, 0, StringBuilder(), onDone)
    }

    private fun runVariants(
        variants: List<Bitmap>,
        index: Int,
        collected: StringBuilder,
        onDone: (OcrParser.Parsed?, String, String) -> Unit
    ) {
        if (index >= variants.size) {
            val raw = collected.toString()
            onDone(OcrParser.parse(raw), raw, "")
            return
        }
        client.process(InputImage.fromBitmap(variants[index], 0))
            .addOnSuccessListener { result ->
                collected.append(result.text).append('\n')
                runVariants(variants, index + 1, collected, onDone)
            }
            .addOnFailureListener {
                runVariants(variants, index + 1, collected, onDone)
            }
    }

    /** 放大：截图里的日期字很小，放大后识别率高很多 */
    private fun upscale(src: Bitmap, factor: Float): Bitmap {
        val w = (src.width * factor).toInt().coerceAtMost(2800)
        val h = (src.height * factor).toInt().coerceAtMost(5600)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    /** 灰度 + 提对比度，压掉彩色底纹（粉色票面、蓝色横幅）对文字的干扰 */
    private fun enhance(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            val contrast = 1.6f
            val translate = (-.5f * contrast + .5f) * 255f
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
