package com.tubetv.app.ui

import com.tubetv.app.data.model.VideoSummary
import com.tubetv.app.ui.common.formatCount
import com.tubetv.app.ui.common.formatTime
import com.tubetv.app.ui.common.videoSubtitle
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

    @Test fun subtitleLeavesOutUnknowns() {
        assertEquals("频道 · 1.5万次观看 · 3天前", videoSubtitle("频道", 15_000, "3天前"))
        assertEquals("3天前", videoSubtitle("频道", -1, "3天前", showChannel = false))
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
