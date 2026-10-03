package com.tubetv.app.data

import com.tubetv.app.data.model.ChannelDetail
import com.tubetv.app.data.model.ChannelSummary
import com.tubetv.app.data.model.Page
import com.tubetv.app.data.model.Playback
import com.tubetv.app.data.model.VideoDetail
import com.tubetv.app.data.model.VideoSummary

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
    suspend fun video(url: String): VideoDetail
    suspend fun playback(url: String): Playback

    /** Drops anything cached for the video, so the next [playback] asks for fresh stream links. */
    fun forget(url: String) {}
}
