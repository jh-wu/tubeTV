package com.tubetv.app.ui

import com.tubetv.app.data.model.VideoSummary
import com.tubetv.app.ui.common.formatCount
import com.tubetv.app.ui.common.formatTime
import com.tubetv.app.ui.common.timeAgo
import com.tubetv.app.ui.common.uploadedText
import com.tubetv.app.ui.common.videoMeta
import com.tubetv.app.ui.home.interleave
import com.tubetv.app.ui.home.mergeLatest
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test fun countsInChinese() {
        assertEquals("9999", formatCount(9_999))
        assertEquals("1万", formatCount(10_000))
        assertEquals("1.2万", formatCount(12_345))
        assertEquals("123万", formatCount(1_234_567))
        assertEquals("3.4亿", formatCount(340_000_000))
    }

    @Test fun times() {
        assertEquals("0:05", formatTime(5_000))
        assertEquals("12:34", formatTime((12 * 60 + 34) * 1000L))
        assertEquals("1:02:03", formatTime((3600 + 2 * 60 + 3) * 1000L))
    }

    @Test fun metaLeavesOutUnknowns() {
        assertEquals("3天前 · 1.5万次观看", videoMeta(15_000, "3天前"))
        assertEquals("3天前", videoMeta(-1, "3天前"))
    }

    @Test fun timeAgoLikeYouTube() {
        val now = 1_000_000_000_000L
        val hour = 3_600_000L
        assertEquals("刚刚", timeAgo(now - 1_000, now))
        assertEquals("5分钟前", timeAgo(now - 5 * 60_000, now))
        assertEquals("6小时前", timeAgo(now - 6 * hour, now))
        assertEquals("13天前", timeAgo(now - 13 * 24 * hour, now))
        assertEquals("2周前", timeAgo(now - 14 * 24 * hour, now))
        assertEquals("1个月前", timeAgo(now - 31 * 24 * hour, now))
        assertEquals("2年前", timeAgo(now - 800 * 24 * hour, now))
        assertEquals("昨天", uploadedText(null, "昨天", now))
    }

    @Test fun latestIsNewestFirstWithoutDuplicates() {
        fun v(id: String, at: Long?) = VideoSummary("https://www.youtube.com/watch?v=$id", id, null, null, null, uploadedAtMs = at)
        val merged = mergeLatest(listOf(listOf(v("a", 10), v("b", null)), listOf(v("c", 30), v("a", 10))))
        assertEquals(listOf("c", "a", "b"), merged.map { it.title })
    }

    @Test fun recommendationsTakeTurnsBetweenSources() {
        fun v(id: String) = VideoSummary("https://www.youtube.com/watch?v=$id", id, null, null, null)
        val mixed = interleave(listOf(listOf(v("a1"), v("a2"), v("a3")), listOf(v("b1"), v("a2")), emptyList()))
        assertEquals(listOf("a1", "b1", "a2", "a3"), mixed.map { it.title })
    }
}
