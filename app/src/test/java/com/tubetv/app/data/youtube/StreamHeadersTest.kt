package com.tubetv.app.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHeadersTest {

    @Test fun readsClientFromQueryOrPath() {
        assertEquals("ANDROID", StreamHeaders.clientOf("https://rr1---sn.googlevideo.com/videoplayback?expire=1&c=ANDROID&itag=18"))
        assertEquals("IOS", StreamHeaders.clientOf("https://rr1---sn.googlevideo.com/videoplayback?itag=18&c=IOS"))
        assertEquals("VISIONOS", StreamHeaders.clientOf("https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/1/c/VISIONOS/id/x/file/index.m3u8"))
        assertEquals("ANDROID_VR", StreamHeaders.clientOf("https://rr1---sn.googlevideo.com/videoplayback?c=ANDROID_VR&x=1"))
    }

    @Test fun userAgentOnlyForKnownClientsOnVideoServers() {
        assertTrue(StreamHeaders.userAgentFor("https://rr1---sn.googlevideo.com/videoplayback?c=ANDROID&x=1")!!.startsWith("com.google.android.youtube/"))
        assertTrue(StreamHeaders.userAgentFor("https://manifest.googlevideo.com/api/manifest/hls_playlist/c/VISIONOS/file/index.m3u8")!!.startsWith("com.google.visionos.youtube/"))
        assertNull(StreamHeaders.userAgentFor("https://rr1---sn.googlevideo.com/videoplayback?c=WEB&x=1"))
        assertNull(StreamHeaders.userAgentFor("https://example.com/videoplayback?c=ANDROID&x=1"))
    }
}
