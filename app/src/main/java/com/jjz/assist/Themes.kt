package com.jjz.assist

/**
 * 预置主题 = 水面以下（液面）的单色，取自用户配色图里每组的实色成员。
 * 水面以上不做配色：统一中性半透明黑纱（不透明度 = 液面的 1/4），
 * 壁纸换了对应区域跟着变（用户口径 v1.4）。字体统一纯白。
 */
data class Theme(val id: String, val name: String, val liquid: Int)

object Themes {
    val ALL = listOf(
        Theme("jjz",     "证件红",   0xFFB3261E.toInt()),
        Theme("shilv",   "玉子黄",   0xFFFFBF51.toInt()),
        Theme("qingjin", "朱颜酡",   0xFFF4A487.toInt()),
        Theme("jicui",   "酪黄",     0xFFF2D791.toInt()),
        Theme("huaqing", "佛手黄",   0xFFE9C30A.toInt()),
        Theme("yuanshan","十样锦",   0xFFFFAFA8.toInt()),
        Theme("shenhai", "雾霾粉",   0xFFF49BB7.toInt()),
        Theme("molan",   "珊瑚橘",   0xFFF47A5C.toInt()),
        Theme("bolv",    "薄荷青",   0xFF65D2C5.toInt()),
        Theme("pubu",    "奢金",     0xFFE3C79F.toInt()),
        Theme("shufu",   "舒服红",   0xFFFF5757.toInt())
    )

    fun byId(id: String): Theme? = ALL.firstOrNull { it.id == id }
}
