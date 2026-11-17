package com.jjz.assist

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 本地存储：进京证有效期 + 年度办理次数。
 * 只落在本机 SharedPreferences，不上传任何东西。
 */
object PassStore {

    private const val NAME = "jjz_store"
    private const val K_UNTIL = "valid_until"
    private const val K_START = "valid_start"
    private const val K_TOTAL = "total_days"
    private const val K_USED = "used_count"
    private const val K_YEAR = "used_year"
    private const val K_BG_ALPHA = "bg_alpha"
    private const val K_FILL_COLOR = "fill_color"
    private const val K_THEME = "theme_id"
    private const val K_C_LIQ = "custom_liquid"

    /** 六环内进京证每个自然年最多 12 次 */
    const val QUOTA = 12

    private fun p(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun validUntil(c: Context): LocalDate? =
        p(c).getString(K_UNTIL, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun validStart(c: Context): LocalDate? =
        p(c).getString(K_START, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** 单次有效天数，进京证上限 7 天 */
    fun totalDays(c: Context): Int = p(c).getInt(K_TOTAL, 7).coerceIn(1, 7)

    fun savePass(c: Context, start: LocalDate, end: LocalDate, countOnce: Boolean) {
        val total = (ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceIn(1, 7)
        // v1.8：起止日与已存记录完全相同 = 同一张证重复识别，不重复计次
        //（额度消耗过快的根因：每次识别存证都 +1，重扫同一张证也扣）
        val same = validStart(c) == start && validUntil(c) == end
        p(c).edit()
            .putString(K_START, start.toString())
            .putString(K_UNTIL, end.toString())
            .putInt(K_TOTAL, total)
            .apply()
        if (countOnce && !same) markUsed(c)
    }

    /** 改到期日：进京证默认 7 天，起算日按 7 天倒推，避免把总天数算成 3 天导致 100% */
    fun updateEnd(c: Context, end: LocalDate) {
        savePass(c, end.minusDays(6), end, false)
    }

    /** 改生效日：结束日 = 生效日 + 6 天（到当天 24 点） */
    fun updateStart(c: Context, start: LocalDate) {
        savePass(c, start, start.plusDays(6), false)
    }

    fun remainingDays(c: Context, today: LocalDate = LocalDate.now()): Int {
        val u = validUntil(c) ?: return 0
        if (today.isAfter(u)) return 0
        return (ChronoUnit.DAYS.between(today, u).toInt() + 1)
    }

    /** 漏斗填充百分比 = 剩余天数 / 单次总天数 */
    fun percent(c: Context, today: LocalDate = LocalDate.now()): Int =
        percentFloat(c, today).toInt()

    /** 剩余天数（带小数）：从此刻算到到期日 24:00，按 0 点-24 点的完整天计算 */
    private fun remainingDaysRaw(c: Context, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): Double {
        val u = validUntil(c) ?: return 0.0
        val endExclusive = u.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
        val seconds = java.time.Duration.between(now, endExclusive).seconds
        return (seconds / 86400.0).coerceAtLeast(0.0)
    }

    fun remainingDaysFloat(c: Context, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): Float =
        ((Math.round(remainingDaysRaw(c, now) * 10.0) / 10.0)).toFloat()

    /** v1.9：剩余小时（一位小数），不足 1 天时用 */
    fun remainingHoursFloat(c: Context): Float =
        ((Math.round(remainingDaysRaw(c) * 24.0 * 10.0) / 10.0)).toFloat()

    /** v1.9：剩余分钟（整数），不足 1 小时时用 */
    fun remainingMinutesInt(c: Context): Int =
        Math.round(remainingDaysRaw(c) * 24.0 * 60.0).toInt().coerceAtLeast(0)

    /** 精确到一位小数的进度，例如 58.3 */
    fun percentFloat(c: Context, today: LocalDate = LocalDate.now()): Float {
        val u = validUntil(c) ?: return 0f
        if (today.isAfter(u)) return 0f
        return (remainingDaysRaw(c) * 100.0 / totalDays(c)).toFloat().coerceIn(0f, 100f)
    }

    // ---- 组件外观 ----

    /** 背景固定 10% 不透明度 */
    const val BG_ALPHA = 26

    fun bgAlpha(c: Context): Int = BG_ALPHA

    /** 前景（液面）不透明度，滑杆可调 */
    fun fillAlpha(c: Context): Int = p(c).getInt(K_BG_ALPHA, 235).coerceIn(0, 255)

    fun fillColor(c: Context): Int = p(c).getInt(K_FILL_COLOR, 0xFFD63A2F.toInt())

    fun setWidgetStyle(c: Context, alpha: Int, color: Int) {
        p(c).edit()
            .putInt(K_BG_ALPHA, alpha.coerceIn(0, 255))
            .putInt(K_FILL_COLOR, color)
            .apply()
    }

    // ---- 主题 ----

    fun themeId(c: Context): String = p(c).getString(K_THEME, "jjz") ?: "jjz"

    fun setThemeId(c: Context, id: String) {
        p(c).edit().putString(K_THEME, id).apply()
    }

    fun customLiquid(c: Context): Int = p(c).getInt(K_C_LIQ, 0xFFB3261E.toInt())

    /** 选自定义色即切到 custom 主题 */
    fun setCustomTheme(c: Context, liquid: Int) {
        p(c).edit()
            .putInt(K_C_LIQ, liquid)
            .putString(K_THEME, "custom")
            .apply()
    }

    fun setFillAlpha(c: Context, alpha: Int) {
        p(c).edit().putInt(K_BG_ALPHA, alpha.coerceIn(0, 255)).apply()
    }

    /** 当前生效主题：预置或自定义三色 */
    fun theme(c: Context): Theme {
        val id = themeId(c)
        if (id == "custom") return Theme("custom", "自定义", customLiquid(c))
        return Themes.byId(id) ?: Themes.ALL[0]
    }

    fun isExpired(c: Context, today: LocalDate = LocalDate.now()): Boolean {
        val u = validUntil(c) ?: return true
        return today.isAfter(u)
    }

    // ---- 年度次数 ----

    private fun rollYear(c: Context) {
        val year = LocalDate.now().year
        val saved = p(c).getInt(K_YEAR, 0)
        if (saved != year) {
            p(c).edit().putInt(K_YEAR, year).putInt(K_USED, 0).apply()
        }
    }

    fun usedThisYear(c: Context): Int {
        rollYear(c)
        return p(c).getInt(K_USED, 0)
    }

    fun remainingQuota(c: Context): Int = (QUOTA - usedThisYear(c)).coerceAtLeast(0)

    fun markUsed(c: Context) {
        rollYear(c)
        p(c).edit().putInt(K_USED, usedThisYear(c) + 1).apply()
    }

    fun setUsed(c: Context, used: Int) {
        rollYear(c)
        p(c).edit().putInt(K_USED, used.coerceIn(0, QUOTA)).apply()
    }
}
