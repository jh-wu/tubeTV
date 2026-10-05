package com.tubetv.app.data

import com.tubetv.app.data.model.ChannelDetail
import com.tubetv.app.data.model.ChannelSummary
import com.tubetv.app.data.model.Page
import com.tubetv.app.data.model.Playback
import com.tubetv.app.data.model.VideoDetail
import com.tubetv.app.data.model.VideoSummary

/** YouTube's public lists that need no sign-in, with the tab titles the home page shows them under. */
object Kiosks {
    const val LIVE = "live"
    const val MUSIC = "trending_music"
    const val GAMING = "trending_gaming"
    const val MOVIES = "trending_movies_and_shows"
    const val PODCASTS = "trending_podcasts_episodes"
    val home = listOf(LIVE to "直播", MUSIC to "音乐", GAMING to "游戏", MOVIES to "电影", PODCASTS to "播客")
    fun title(id: String) = home.first { it.first == id }.second
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
