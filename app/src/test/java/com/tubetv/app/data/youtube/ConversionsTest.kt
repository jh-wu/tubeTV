package com.tubetv.app.data.youtube

import com.tubetv.app.data.model.PlaySource
import com.tubetv.app.data.youtube.Conversions.pick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.Image

class ConversionsTest {

    private fun image(url: String, height: Int) = Image(url, height, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.fromHeight(height))

    @Test fun picksImageClosestToTarget() {
        val images = listOf(image("s", 90), image("m", 360), image("l", 720))
        assertEquals("m", images.pick(400))
        assertEquals("l", images.pick(1000))
        assertNull(emptyList<Image>().pick(360))
    }

    @Test fun picksLastImageWhenSizesUnknown() {
        assertEquals("b", listOf(image("a", Image.HEIGHT_UNKNOWN), image("b", Image.HEIGHT_UNKNOWN)).pick(360))
    }

    @Test fun hlsComesFirst() {
        val sources = Conversions.playSources("https://h/index.m3u8", "", emptyList(), emptyList(), emptyList())
        assertEquals(listOf(PlaySource.Hls("https://h/index.m3u8")), sources)
    }
}
