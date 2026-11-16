package com.jjz.assist

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

/**
 * 从相册/文件管理器"分享"过来时由系统拉起。
 * 流程：读图 → OCR → 抓到期日 → 人工确认 → 存本地 → 刷组件。
 */
class ShareReceiverActivity : Activity() {

    private var preview: ImageView? = null
    private var statusText: TextView? = null
    private var endButton: Button? = null
    private var countCheck: CheckBox? = null

    private var parsed: OcrParser.Parsed? = null
    private var chosenEnd: LocalDate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        start()
    }

    private fun currentImageUri(): Uri? {
        val intent = intent ?: return null
        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        if (stream != null) return stream
        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) return clip.getItemAt(0).uri
        return intent.data
    }

    private fun start() {
        val uri = currentImageUri()
        if (uri == null) {
            statusText?.text = "没拿到图片，请从相册重新分享一次"
            return
        }
        preview?.setImageURI(uri)
        statusText?.text = "正在识别…"

        OcrEngine.recognize(this, uri) { result, raw, error ->
            if (result == null) {
                val shown = raw.replace("\n", " ").trim().take(300)
                statusText?.text = when {
                    error.isNotEmpty() -> error
                    raw.isBlank() -> "图片没读出文字：文字太小或太模糊。\n可以直接手动选到期日"
                    else -> "读出文字了，但没找到日期：\n$shown\n请手动选到期日"
                }
                pickDate(LocalDate.now().plusDays(6))
            } else {
                parsed = result
                chosenEnd = result.end
                statusText?.text =
                    "识别成功：${result.start.monthValue}月${result.start.dayOfMonth}日 至 " +
                        "${result.end.monthValue}月${result.end.dayOfMonth}日，共 ${result.totalDays} 天\n如不对，点下面的到期日改一下"
                renderEndButton()
            }
        }
    }

    private fun renderEndButton() {
        endButton?.text = chosenEnd?.let { "到期日：$it（点我修改）" } ?: "到期日：未选择（点我选择）"
    }

    private fun pickDate(initial: LocalDate) {
        DatePickerDialog(this, { _, y, m, d ->
            chosenEnd = LocalDate.of(y, m + 1, d)
            renderEndButton()
            PassStore.validUntil(this)?.let { old ->
                statusText?.text = "到期日已设为 $chosenEnd"
            }
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    private fun save() {
        val end = chosenEnd
        if (end == null) {
            Toast.makeText(this, "先选一个到期日", Toast.LENGTH_SHORT).show()
            return
        }
        val start = parsed?.takeIf { it.end == end }?.start ?: end.minusDays(6)
        PassStore.savePass(this, start, end, countCheck?.isChecked == true)
        PassWidgetProvider.updateAll(this)
        ReminderScheduler.reschedule(this)
        Toast.makeText(this, "已保存，桌面组件已更新", Toast.LENGTH_SHORT).show()
        finish()
    }

    // ---------- UI ----------

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(0xFF101312.toInt())
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "识别进京证"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })

        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF1E2A24.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)
            ).apply { topMargin = dp(16) }
        }
        root.addView(preview)

        statusText = TextView(this).apply {
            setTextColor(0xFFC9D3CC.toInt())
            textSize = 14f
            setPadding(0, dp(14), 0, dp(4))
        }
        root.addView(statusText)

        endButton = Button(this).apply {
            text = "到期日：未选择"
            setTextColor(Color.WHITE)
            textSize = 15f
            background = rounded(0xFF232F29.toInt(), 12)
            setOnClickListener { pickDate(chosenEnd ?: LocalDate.now().plusDays(6)) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { topMargin = dp(10) }
        }
        root.addView(endButton)

        countCheck = CheckBox(this).apply {
            text = "计入年度办理次数（剩 ${PassStore.remainingQuota(this@ShareReceiverActivity)} 次）"
            setTextColor(0xFF9AA79F.toInt())
            isChecked = true
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(countCheck)

        root.addView(Button(this).apply {
            text = "保存并刷新桌面组件"
            setTextColor(Color.WHITE)
            textSize = 15f
            background = rounded(0xFFD63A2F.toInt(), 12)
            setOnClickListener { save() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
            ).apply { topMargin = dp(16) }
        })

        root.addView(Button(this).apply {
            text = "取消"
            setTextColor(0xFF9AA79F.toInt())
            textSize = 15f
            background = rounded(0xFF1A211D.toInt(), 12)
            setOnClickListener { finish() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { topMargin = dp(10) }
        })

        return scroll
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
