package com.tubetv.app.data.youtube

/**
 * YouTube writes counts in Chinese as "93万次观看" or "1510萬位訂閱者". NewPipe only knows K/M/B,
 * so it keeps the digits and drops the 万, reading 930,000 views as 93. Writing the counts out
 * in full before NewPipe reads the page fixes that.
 */
object ChineseCounts {
    private val count = Regex(
        """(\d+(?:\.\d+)?)\s*([万萬亿億])(?=\s*(?:次观看|次觀看|次播放|位订阅者|位訂閱者|名订阅者|名訂閱者|人正在观看|人正在觀看|人观看|人觀看|人在看))""",
    )

    fun expand(text: String): String {
        if (text.none { it == '万' || it == '萬' || it == '亿' || it == '億' }) return text
        return count.replace(text) { m ->
            val unit = if (m.groupValues[2] in setOf("万", "萬")) 10_000.toBigDecimal() else 100_000_000.toBigDecimal()
            m.groupValues[1].toBigDecimal().multiply(unit).toBigInteger().toString()
        }
    }
}
