package com.jjz.assist

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 从进京证截图的 OCR 文本里抽有效期。
 * 先做归一化（去空白、全角转半角），再按多种写法匹配：
 *  - 2026年11月17日 / 2026-11-17 / 2026.11.17 / 20261117
 *  - 11月17日 / 11月17号 / 11月17（无年份时按当前年份推断）
 */
object OcrParser {

    private val YEAR_MONTH_DAY = Regex("(20\\d{2})\\s*[年./\\-]\\s*(\\d{1,2})\\s*[月./\\-]\\s*(\\d{1,2})\\s*[日号]?")
    private val COMPACT = Regex("(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])")
    private val MONTH_DAY = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?")
    private val SLASH_MONTH_DAY = Regex("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?![\\d/])")
    private val UNTIL_KEYS = listOf("有效期至", "有效期止", "有效期限至", "到期日", "至", "到")

    data class Parsed(
        val start: LocalDate,
        val end: LocalDate,
        val totalDays: Int,
        val raw: String
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): Parsed? {
        if (text.isBlank()) return null
        val normalized = normalize(text)
        val dates = extractDates(normalized, today).distinct().sorted()
        if (dates.isEmpty()) return null

        var end: LocalDate? = null
        for (key in UNTIL_KEYS) {
            val idx = normalized.indexOf(key)
            if (idx >= 0) {
                val tail = normalized.substring(idx + key.length, minOf(normalized.length, idx + key.length + 14))
                val hit = extractDates(tail, today).firstOrNull()
                if (hit != null) {
                    end = hit
                    break
                }
            }
        }
        if (end == null) end = dates.last()

        var safeEnd = end!!
        if (safeEnd.isBefore(today.minusDays(45)) || safeEnd.isAfter(today.plusDays(60))) {
            safeEnd = dates.lastOrNull {
                !it.isBefore(today.minusDays(45)) && !it.isAfter(today.plusDays(60))
            } ?: return null
        }

        // 生效日期：到期日之前 14 天内最早的那个日期
        val start = dates.filter { it.isBefore(safeEnd) && it.isAfter(safeEnd.minusDays(14)) }.minOrNull()
            ?: safeEnd.minusDays(6)

        val total = (ChronoUnit.DAYS.between(start, safeEnd).toInt() + 1).coerceIn(1, 7)
        return Parsed(start, safeEnd, total, text)
    }

    /** 去掉所有空白、全角数字转半角、各种连字符统一成 -，避免 OCR 里的空格把日期拆开 */
    private fun normalize(src: String): String {
        val sb = StringBuilder(src.length)
        for (ch in src) {
            when {
                ch.isWhitespace() -> {}
                ch.code in 0xFF10..0xFF19 -> sb.append(('0' + (ch.code - 0xFF10)))
                ch == '\u3000' -> {}
                ch == '－' || ch == '—' || ch == '–' || ch == '~' -> sb.append('-')
                ch == '：' -> sb.append(':')
                ch == '，' -> sb.append(',')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun extractDates(text: String, today: LocalDate): List<LocalDate> {
        val out = mutableListOf<LocalDate>()

        YEAR_MONTH_DAY.findAll(text).forEach { m ->
            toDate(m.groupValues[1].toIntOrNull(), m.groupValues[2].toIntOrNull(), m.groupValues[3].toIntOrNull())
                ?.let { out.add(it) }
        }
        COMPACT.findAll(text).forEach { m ->
            toDate(m.groupValues[1].toIntOrNull(), m.groupValues[2].toIntOrNull(), m.groupValues[3].toIntOrNull())
                ?.let { out.add(it) }
        }
        MONTH_DAY.findAll(text).forEach { m ->
            inferYear(m.groupValues[1].toIntOrNull(), m.groupValues[2].toIntOrNull(), today)?.let { out.add(it) }
        }
        if (out.isEmpty()) {
            SLASH_MONTH_DAY.findAll(text).forEach { m ->
                inferYear(m.groupValues[1].toIntOrNull(), m.groupValues[2].toIntOrNull(), today)?.let { out.add(it) }
            }
        }
        return out
    }

    private fun toDate(y: Int?, m: Int?, d: Int?): LocalDate? {
        if (y == null || m == null || d == null) return null
        return runCatching { LocalDate.of(y, m, d) }.getOrNull()
    }

    /** 只有月日时推断年份：推出来比今天早太多，就当作明年 */
    private fun inferYear(m: Int?, d: Int?, today: LocalDate): LocalDate? {
        if (m == null || d == null) return null
        val candidate = runCatching { LocalDate.of(today.year, m, d) }.getOrNull() ?: return null
        return if (candidate.isBefore(today.minusDays(150))) candidate.plusYears(1) else candidate
    }
}
