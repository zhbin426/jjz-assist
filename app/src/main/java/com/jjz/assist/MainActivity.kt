package com.jjz.assist

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

class MainActivity : Activity() {

    private lateinit var percentText: TextView
    private lateinit var untilText: TextView
    private lateinit var remainText: TextView
    private lateinit var quotaText: TextView
    private lateinit var forecastText: TextView
    private lateinit var stateText: TextView
    private lateinit var permText: TextView
    private var previewView: android.widget.ImageView? = null

    private val pickRequest = 200
    private val photoPermRequest = 300

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        askNotificationPermission()
        requestPhotoPermission()
        ReminderScheduler.reschedule(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickRequest && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val send = Intent(this, ShareReceiverActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(send)
        }
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
            text = "进京证助手"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "到期自动提醒 · 桌面组件显示剩余有效期"
            setTextColor(0xFF9AA79F.toInt())
            textSize = 13f
            setPadding(0, dp(6), 0, 0)
        })

        permText = TextView(this).apply {
            textSize = 12f
            setPadding(0, dp(8), 0, 0)
            setOnClickListener {
                if (hasPhotoPermission()) {
                    Toast.makeText(this@MainActivity, "相册权限已授权", Toast.LENGTH_SHORT).show()
                } else {
                    requestPhotoPermission()
                }
            }
        }
        root.addView(permText)

        // 数据卡片
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF1E2A24.toInt(), 18)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        root.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        percentText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 40f
            setTypeface(typeface, Typeface.BOLD)
        }
        card.addView(percentText)

        stateText = TextView(this).apply {
            setTextColor(0xFF61D095.toInt())
            textSize = 13f
            setPadding(0, dp(2), 0, dp(10))
        }
        card.addView(stateText)

        untilText = row(card, "有效期至", "—")
        remainText = row(card, "剩余天数", "—")
        quotaText = row(card, "年度剩余次数", "—")
        forecastText = row(card, "额度可覆盖至", "—")

        // 按钮区
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        actions.addView(primaryButton("从相册识别进京证", 0xFFD63A2F.toInt()) {
            if (!hasPhotoPermission()) {
                requestPhotoPermission()
                return@primaryButton
            }
            val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(Intent.createChooser(pick, "选择进京证图片"), pickRequest)
        })

        actions.addView(secondaryButton("直接读最近一张截图识别") {
            if (!hasPhotoPermission()) {
                requestPhotoPermission()
                Toast.makeText(this, "需要相册权限才能直接读截图", Toast.LENGTH_SHORT).show()
                return@secondaryButton
            }
            val uri = latestScreenshot()
            if (uri == null) {
                Toast.makeText(this, "相册里没找到截图，改用上面的按钮选图", Toast.LENGTH_SHORT).show()
                return@secondaryButton
            }
            openShare(uri)
        })

        actions.addView(secondaryButton("手动修改到期日") {
            val initial = PassStore.validUntil(this) ?: LocalDate.now().plusDays(6)
            DatePickerDialog(this, { _, y, m, d ->
                PassStore.updateEnd(this, LocalDate.of(y, m + 1, d))
                afterDataChanged()
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        })

        actions.addView(secondaryButton("手动修改生效日") {
            val initial = PassStore.validStart(this) ?: LocalDate.now()
            DatePickerDialog(this, { _, y, m, d ->
                PassStore.updateStart(this, LocalDate.of(y, m + 1, d))
                afterDataChanged()
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        })

        actions.addView(secondaryButton("记一次办理（次数 +1）") {
            AlertDialog.Builder(this)
                .setTitle("记一次办理")
                .setMessage("确认这算作本年度第 ${PassStore.usedThisYear(this) + 1} 次办理？年度额度 ${PassStore.QUOTA} 次。")
                .setPositiveButton("确认") { _, _ ->
                    PassStore.markUsed(this)
                    afterDataChanged()
                }
                .setNegativeButton("取消", null)
                .show()
        })

        actions.addView(secondaryButton("录入今年剩余次数") {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(PassStore.remainingQuota(this@MainActivity).toString())
                setSelection(text.length)
                setPadding(dp(20), dp(12), dp(20), dp(12))
            }
            AlertDialog.Builder(this)
                .setTitle("本年度还剩几次额度？")
                .setMessage("六环内进京证每年 ${PassStore.QUOTA} 次，按 7 天/次计算")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val n = input.text.toString().toIntOrNull()
                    if (n == null || n !in 0..PassStore.QUOTA) {
                        Toast.makeText(this, "请输入 0 到 ${PassStore.QUOTA} 之间的数字", Toast.LENGTH_SHORT).show()
                    } else {
                        PassStore.setUsed(this, PassStore.QUOTA - n)
                        afterDataChanged()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        })

        actions.addView(secondaryButton("刷新桌面组件") {
            PassWidgetProvider.updateAll(this)
            Toast.makeText(this, "组件已刷新", Toast.LENGTH_SHORT).show()
        })

        root.addView(TextView(this).apply {
            text = "提示：长按桌面空白处 → 添加小部件 → 找到「进京证助手」，拖到桌面即可。到期前 2 天会自动提醒你续办。"
            setTextColor(0xFF7C8B83.toInt())
            textSize = 12f
            setPadding(0, dp(20), 0, 0)
        })

        addAppearanceSection(root)

        return scroll
    }

    /** 组件外观：背景不透明度 + 液面颜色 */
    private fun addAppearanceSection(root: LinearLayout) {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF1E2A24.toInt(), 18)
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        root.addView(section, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        section.addView(TextView(this).apply {
            text = "组件外观"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
        })

        val alphaLabel = TextView(this).apply {
            text = "前景（液面）不透明度 ${PassStore.fillAlpha(this@MainActivity) * 100 / 255}%"
            setTextColor(0xFF9AA79F.toInt())
            textSize = 13f
            setPadding(0, dp(10), 0, 0)
        }
        section.addView(alphaLabel)

        val seek = android.widget.SeekBar(this).apply {
            max = 100
            progress = PassStore.fillAlpha(this@MainActivity) * 100 / 255
            setPadding(0, dp(6), 0, 0)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    alphaLabel.text = "前景（液面）不透明度 $progress%"
                }

                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                    val percent = sb?.progress ?: 90
                    PassStore.setWidgetStyle(
                        this@MainActivity,
                        percent * 255 / 100,
                        PassStore.fillColor(this@MainActivity)
                    )
                    PassWidgetProvider.updateAll(this@MainActivity)
                    previewView?.setImageBitmap(WidgetRenderer.render(this@MainActivity, 300, 600))
                }
            })
        }
        section.addView(seek)

        section.addView(TextView(this).apply {
            text = "液面颜色（按住色板取色）"
            setTextColor(0xFF9AA79F.toInt())
            textSize = 13f
            setPadding(0, dp(14), 0, dp(8))
        })

        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(PassStore.fillColor(this@MainActivity))
                setStroke(dp(2), Color.WHITE)
            }
        }
        val previewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, dp(10))
        }
        previewRow.addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) })
        previewRow.addView(TextView(this).apply {
            text = "当前颜色，下面色板任意位置按住即可换色"
            setTextColor(0xFFCED8D1.toInt())
            textSize = 12f
        })
        section.addView(previewRow)

        val palette = PaletteView(this).apply {
            setColor(PassStore.fillColor(this@MainActivity))
            onColorChanging = { color ->
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(2), Color.WHITE)
                }
            }
            onColorPicked = { color ->
                PassStore.setWidgetStyle(
                    this@MainActivity,
                    PassStore.fillAlpha(this@MainActivity),
                    color
                )
                PassWidgetProvider.updateAll(this@MainActivity)
                previewView?.setImageBitmap(WidgetRenderer.render(this@MainActivity, 300, 600))
            }
        }
        section.addView(palette, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(110)))

        section.addView(TextView(this).apply {
            text = "组件效果预览"
            setTextColor(0xFF9AA79F.toInt())
            textSize = 13f
            setPadding(0, dp(16), 0, dp(8))
        })

        val preview = android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        previewView = preview
        section.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(260)
        ))
    }

    private fun row(parent: LinearLayout, label: String, value: String): TextView {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        line.addView(TextView(this).apply {
            text = label
            setTextColor(0xFF9AA79F.toInt())
            textSize = 14f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueView = TextView(this).apply {
            text = value
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }
        line.addView(valueView)
        parent.addView(line)
        return valueView
    }

    private fun primaryButton(text: String, color: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            background = rounded(color, 12)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
            ).apply { topMargin = dp(12) }
        }

    private fun secondaryButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            background = rounded(0xFF232F29.toInt(), 12)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { topMargin = dp(10) }
        }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    // ---------- 数据刷新 ----------

    private fun refresh() {
        refreshPermissionText()
        previewView?.setImageBitmap(WidgetRenderer.render(this, 300, 600))
        val today = LocalDate.now()
        val until = PassStore.validUntil(this)

        if (until == null) {
            percentText.text = "--"
            stateText.text = "还没录入有效期"
            stateText.setTextColor(0xFFE0A93B.toInt())
            untilText.text = "—"
            remainText.text = "—"
        } else if (today.isAfter(until)) {
            percentText.text = "0.0%"
            stateText.text = "已过期，记得续办"
            stateText.setTextColor(0xFFE0563F.toInt())
            untilText.text = dateLabel(until)
            remainText.text = "0.0 天"
        } else {
            val pct = PassStore.percentFloat(this, today)
            percentText.text = String.format("%.1f%%", pct)
            stateText.text = if (PassStore.remainingDays(this, today) <= 2) "快到期了，尽早续办" else "有效期内"
            stateText.setTextColor(
                if (PassStore.remainingDays(this, today) <= 2) 0xFFE0A93B.toInt() else 0xFF61D095.toInt()
            )
            untilText.text = dateLabel(until)
            remainText.text = String.format(
                "%.1f 天（共 %d 天）",
                PassStore.remainingDaysFloat(this),
                PassStore.totalDays(this)
            )
        }

        quotaText.text = "${PassStore.remainingQuota(this)} / ${PassStore.QUOTA}"

        // 到期后一天起，用剩余次数 × 7 天连续续办，能覆盖到哪天
        val left = PassStore.remainingQuota(this)
        val base = if (until != null && until.isAfter(today)) until.plusDays(1) else today
        val coverTo = base.plusDays((left * 7).toLong())
        forecastText.text = if (left > 0) coverTo.toString() else "额度已用完"
    }

    private fun afterDataChanged() {
        refresh()
        PassWidgetProvider.updateAll(this)
        ReminderScheduler.reschedule(this)
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    // ---------- 相册权限 ----------

    private fun photoPermissionName(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPhotoPermission(): Boolean =
        checkSelfPermission(photoPermissionName()) == PackageManager.PERMISSION_GRANTED

    private fun requestPhotoPermission() {
        if (!hasPhotoPermission()) requestPermissions(arrayOf(photoPermissionName()), photoPermRequest)
    }

    private fun refreshPermissionText() {
        if (hasPhotoPermission()) {
            permText.text = "相册权限：已授权（可直接读截图）"
            permText.setTextColor(0xFF61D095.toInt())
        } else {
            permText.text = "相册权限：未授权，识别会失败 —— 点这里授权"
            permText.setTextColor(0xFFE0A93B.toInt())
        }
    }

    /** 相册里最新一张文件名带"截图/Screenshot"的图 */
    private fun latestScreenshot(): Uri? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        var found: Uri? = null
        runCatching {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    if (name.contains("Screenshot", true) || name.contains("截屏") || name.contains("截图")) {
                        found = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idCol)
                        )
                        break
                    }
                }
            }
        }
        return found
    }

    private fun openShare(uri: Uri) {
        startActivity(Intent(this, ShareReceiverActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun dateLabel(date: LocalDate): String {
        val week = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1]
        return "${date.year}/${date.monthValue}/${date.dayOfMonth}（$week）"
    }
}
