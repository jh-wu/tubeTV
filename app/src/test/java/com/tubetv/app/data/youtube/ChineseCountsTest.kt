package com.tubetv.app.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseCountsTest {
    @Test fun expandsCounts() {
        assertEquals("930000次观看", ChineseCounts.expand("93万次观看"))
        assertEquals("15100000位訂閱者", ChineseCounts.expand("1510萬位訂閱者"))
        assertEquals("12000 次觀看", ChineseCounts.expand("1.2萬 次觀看"))
        assertEquals("150000000次播放", ChineseCounts.expand("1.5亿次播放"))
        assertEquals("""{"simpleText":"23000人正在观看"}""", ChineseCounts.expand("""{"simpleText":"2.3万人正在观看"}"""))
    }

    @Test fun leavesTitlesAlone() {
        assertEquals("挑战3万元生活", ChineseCounts.expand("挑战3万元生活"))
        assertEquals("1,234次观看", ChineseCounts.expand("1,234次观看"))
    }
}
