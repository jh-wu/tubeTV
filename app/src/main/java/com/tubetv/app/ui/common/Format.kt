package com.tubetv.app.ui.common

import java.util.Locale

/** Counts the way YouTube shows them in Chinese: 9,999 then 1.2万, 3.4亿. */
fun formatCount(n: Long): String = when {
    n < 10_000 -> n.toString()
    n < 100_000_000 -> trimmed(n / 10_000.0) + "万"
    else -> trimmed(n / 100_000_000.0) + "亿"
}

private fun trimmed(v: Double): String =
    if (v >= 100) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v).removeSuffix(".0")

fun formatViews(n: Long): String? = if (n < 0) null else "${formatCount(n)}次观看"

fun formatSubscribers(n: Long): String? = if (n < 0) null else "${formatCount(n)}位订阅者"

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

/** "频道名 · 12万次观看 · 3天前", leaving out whatever is unknown. */
fun videoSubtitle(channel: String?, views: Long, uploaded: String?, showChannel: Boolean = true): String =
    listOfNotNull(channel?.takeIf { showChannel }, formatViews(views), uploaded).joinToString(" · ")
