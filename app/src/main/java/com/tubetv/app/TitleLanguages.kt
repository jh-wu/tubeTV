package com.tubetv.app

/** The languages 设置 offers for video titles: a language tag (null follows the TV) and its name. */
object TitleLanguages {
    val all: List<Pair<String?, String>> = listOf(
        null to "跟随系统",
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "ko" to "한국어",
    )

    fun name(tag: String?) = all.firstOrNull { it.first == tag }?.second ?: tag ?: "跟随系统"
}
