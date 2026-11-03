package com.tubetv.app.ui.common

import com.tubetv.app.Strings
import com.tubetv.app.UiLanguage
import java.util.Locale

/**
 * Counts the way YouTube shows them in the app's language: 9,999 then 1.2万 and 3.4亿 in Simplified
 * Chinese (萬/億 in Traditional, 万/億 in Japanese, 만/억 in Korean), 1.2K, 3.4M and 5.6B in English.
 */
fun formatCount(n: Long): String {
    val (tenThousand, hundredMillion) = when (Strings.language) {
        UiLanguage.SimplifiedChinese -> "万" to "亿"
        UiLanguage.TraditionalChinese -> "萬" to "億"
        UiLanguage.Japanese -> "万" to "億"
        UiLanguage.Korean -> "만" to "억"
        UiLanguage.English -> return when {
            n < 1_000 -> n.toString()
            n < 1_000_000 -> trimmed(n / 1_000.0) + "K"
            n < 1_000_000_000 -> trimmed(n / 1_000_000.0) + "M"
            else -> trimmed(n / 1_000_000_000.0) + "B"
        }
    }
    return when {
        n < 10_000 -> n.toString()
        n < 100_000_000 -> trimmed(n / 10_000.0) + tenThousand
        else -> trimmed(n / 100_000_000.0) + hundredMillion
    }
}

private fun trimmed(v: Double): String =
    if (v >= 100) v.toLong().toString() else String.format(Locale.ROOT, "%.1f", v).removeSuffix(".0")

fun formatViews(n: Long): String? = if (n < 0) null else Strings.views(formatCount(n))

fun formatSubscribers(n: Long): String? = if (n < 0) null else Strings.subscribers(formatCount(n))

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

/**
 * How long ago [ms] was, the way YouTube says it ("3天前", "2 weeks ago"). Every list says it
 * this way, whether its dates came from YouTube's pages or from a channel's feed (exact times).
 */
fun timeAgo(ms: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - ms).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> Strings.justNow
        minutes < 60 -> Strings.minutesAgo(minutes)
        hours < 24 -> Strings.hoursAgo(hours)
        days < 14 -> Strings.daysAgo(days)
        days < 30 -> Strings.weeksAgo(days / 7)
        days < 365 -> Strings.monthsAgo((days / 30).coerceAtLeast(1))
        else -> Strings.yearsAgo(days / 365)
    }
}

/** When a video came out: worked out from its time when known, else YouTube's own words. */
fun uploadedText(uploadedAtMs: Long?, uploaded: String?, now: Long = System.currentTimeMillis()): String? =
    uploadedAtMs?.let { timeAgo(it, now) } ?: uploaded

/** "3天前 · 12万次观看", the line under a video's channel, leaving out whatever is unknown. */
fun videoMeta(views: Long, uploaded: String?): String =
    listOfNotNull(uploaded, formatViews(views)).joinToString(" · ")
