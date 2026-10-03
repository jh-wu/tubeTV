package com.tubetv.app.data.youtube

import com.tubetv.app.data.model.PlaySource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.util.Locale

/**
 * Runs the real YouTube client. Skipped unless TUBETV_LIVE=1, so normal builds never
 * depend on YouTube; CI's probe job turns it on.
 */
class LiveYouTubeTest {

    private val http = OkHttpClient()
    private val source = NewPipeSource(http, Locale.SIMPLIFIED_CHINESE)

    @Test fun searchChannelVideosAndPlay() = runBlocking {
        assumeTrue(System.getenv("TUBETV_LIVE") == "1")

        val channels = source.searchChannels("NASA", null)
        println("channels: ${channels.items.take(5).map { "${it.name} ${it.url} ${it.subscriberCount}" }}")
        assertTrue("no channels", channels.items.isNotEmpty())

        val channel = source.channel(channels.items.first().url)
        println("channel: ${channel.name} ${channel.url} subs=${channel.subscriberCount} avatar=${channel.avatarUrl}")

        val videos = source.channelVideos(channel.url, null)
        println("videos: ${videos.items.size}, more=${videos.hasMore}, first=${videos.items.firstOrNull()}")
        assertTrue("no channel videos", videos.items.isNotEmpty())
        if (videos.hasMore) println("page 2: ${source.channelVideos(channel.url, videos.next).items.size}")

        val latest = source.latestVideos(channel.url)
        println("latest: ${latest.size}, first=${latest.firstOrNull()}")

        val found = source.searchVideos("lofi hip hop", null)
        println("video search: ${found.items.size}, first=${found.items.firstOrNull()?.title}")
        assertTrue("no videos found", found.items.isNotEmpty())

        for ((id, title) in com.tubetv.app.data.Kiosks.home) {
            val list = runCatching { source.kiosk(id) }
            println("kiosk $title: ${list.getOrNull()?.size} ${list.exceptionOrNull() ?: list.getOrNull()?.firstOrNull()?.title}")
        }
        assertTrue("no home rows", com.tubetv.app.data.Kiosks.home.any { (id, _) -> runCatching { source.kiosk(id) }.getOrNull().orEmpty().isNotEmpty() })

        val url = videos.items.first { !it.isLive }.url
        val detail = try {
            source.video(url)
        } catch (e: SignInConfirmNotBotException) {
            // YouTube often asks data-centre addresses such as CI runners to sign in; homes rarely see this.
            println("video page: YouTube asked this IP to sign in (${e.message}); playback not checked")
            return@runBlocking
        }
        println("detail: ${detail.title} / ${detail.channel} / related=${detail.related.size}")

        val playback = source.playback(url)
        println("sources: ${playback.sources.map { it.label }}")
        assertTrue("nothing to play", playback.sources.isNotEmpty())

        // Can the player actually fetch the first source?
        val player = http.newBuilder().addInterceptor(StreamHeaders).build()
        when (val first = playback.sources.first()) {
            is PlaySource.Hls -> {
                val master = get(player, first.url)
                println("HLS master: ${master.first} ${master.second.take(200)}")
                val variant = master.second.lines().firstOrNull { it.startsWith("http") }
                if (variant != null) {
                    val media = get(player, variant)
                    println("HLS variant: ${media.first}")
                    val segment = media.second.lines().firstOrNull { it.startsWith("http") }
                    if (segment != null) println("HLS segment: ${get(player, segment, range = true).first}")
                }
                assertTrue("HLS master HTTP ${master.first}", master.first == 200)
            }
            is PlaySource.Merged -> println("video: ${get(player, first.videoUrl, range = true).first}, audio: ${get(player, first.audioUrl, range = true).first}")
            is PlaySource.Progressive -> println("file: ${get(player, first.url, range = true).first}")
            is PlaySource.Dash -> println("DASH: ${get(player, first.url).first}")
        }
    }

    private fun get(client: OkHttpClient, url: String, range: Boolean = false): Pair<Int, String> {
        val req = Request.Builder().url(url).apply { if (range) header("Range", "bytes=0-65535") }.build()
        client.newCall(req).execute().use { r ->
            return r.code to if (range) "" else r.body?.string().orEmpty()
        }
    }
}
