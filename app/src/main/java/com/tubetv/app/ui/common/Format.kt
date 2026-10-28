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

/**
 * How long ago [ms] was, the way YouTube says it in Chinese ("3天前", "2周前"). Every list says it
 * this way, whether its dates came from YouTube's pages or from a channel's feed (exact times).
 */
fun timeAgo(ms: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - ms).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 14 -> "${days}天前"
        days < 30 -> "${days / 7}周前"
        days < 365 -> "${(days / 30).coerceAtLeast(1)}个月前"
        else -> "${days / 365}年前"
    }
}

/** When a video came out: worked out from its time when known, else YouTube's own words. */
fun uploadedText(uploadedAtMs: Long?, uploaded: String?, now: Long = System.currentTimeMillis()): String? =
    uploadedAtMs?.let { timeAgo(it, now) } ?: uploaded

/** "3天前 · 12万次观看", the line under a video's channel, leaving out whatever is unknown. */
fun videoMeta(views: Long, uploaded: String?): String =
    listOfNotNull(uploaded, formatViews(views)).joinToString(" · ")
