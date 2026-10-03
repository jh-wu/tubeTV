package com.tubetv.app.data

import com.tubetv.app.data.model.ChannelDetail
import com.tubetv.app.data.model.ChannelSummary
import com.tubetv.app.data.model.Page
import com.tubetv.app.data.model.Playback
import com.tubetv.app.data.model.VideoDetail
import com.tubetv.app.data.model.VideoSummary

/** YouTube's public lists that need no sign-in, with the titles the home page shows them under. */
object Kiosks {
    val home = listOf(
        "live" to "正在直播",
        "trending_music" to "热门音乐",
        "trending_gaming" to "热门游戏",
        "trending_movies_and_shows" to "电影与节目",
        "trending_podcasts_episodes" to "热门播客",
    )
}

/**
 * Everything the UI needs from YouTube. The UI only talks to this interface,
 * so the extraction library can change without touching screens.
 * A null page position means the first page.
 */
interface YouTubeSource {
    suspend fun searchVideos(query: String, page: Any?): Page<VideoSummary>
    suspend fun searchChannels(query: String, page: Any?): Page<ChannelSummary>
    suspend fun channel(url: String): ChannelDetail
    suspend fun channelVideos(url: String, page: Any?): Page<VideoSummary>

    /** A channel's most recent uploads, newest first. */
    suspend fun latestVideos(channelUrl: String): List<VideoSummary>
    /** One of YouTube's public lists, e.g. live now or trending music (see [Kiosks]). */
    suspend fun kiosk(id: String): List<VideoSummary>

    suspend fun video(url: String): VideoDetail
    suspend fun playback(url: String): Playback

    /** Drops anything cached for the video, so the next [playback] asks for fresh stream links. */
    fun forget(url: String) {}
}
