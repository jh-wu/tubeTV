package com.tubetv.app.data.model

/** Anything shown in a grid or row, identified by its YouTube URL. */
interface Keyed {
    val key: String
}

/** A video as it appears in a row, grid or search result. */
data class VideoSummary(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val channelName: String?,
    val channelUrl: String?,
    /** Length in seconds; negative when unknown. */
    val durationSec: Long = -1,
    /** Negative when unknown. */
    val viewCount: Long = -1,
    /** How YouTube words the upload date, e.g. "3 days ago". */
    val uploaded: String? = null,
    /** Upload time in epoch milliseconds, used to merge several channels' videos. */
    val uploadedAtMs: Long? = null,
    val isLive: Boolean = false,
) : Keyed {
    override val key get() = url
}

/** A channel as it appears in a search result, favourites or browsing history. */
data class ChannelSummary(
    val url: String,
    val name: String,
    val avatarUrl: String?,
    /** Negative when unknown. */
    val subscriberCount: Long = -1,
) : Keyed {
    override val key get() = url
}

data class ChannelDetail(
    val url: String,
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val subscriberCount: Long,
    val description: String?,
) {
    val summary get() = ChannelSummary(url, name, avatarUrl, subscriberCount)
}

data class VideoDetail(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val description: String?,
    val channel: ChannelSummary?,
    val durationSec: Long,
    val viewCount: Long,
    val uploaded: String?,
    val isLive: Boolean,
    val related: List<VideoSummary>,
)

/** One page of a list. [next] is the opaque position of the following page, null at the end. */
data class Page<T>(val items: List<T>, val next: Any?) {
    val hasMore get() = next != null
}

/** Everything the player needs for one video. */
data class Playback(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val channelName: String?,
    val channelUrl: String?,
    val isLive: Boolean,
    /** Ways to play the video, best first; the player falls back down the list. */
    val sources: List<PlaySource>,
    /** Subtitles that can be turned on while playing. */
    val subtitles: List<SubtitleOption> = emptyList(),
    /** The video's audio languages (dubs), when it has more than one; the first is the original. */
    val audioOptions: List<AudioOption> = emptyList(),
)

data class SubtitleOption(val label: String, val language: String?, val url: String)

/** One audio language, played with [PlaySource.Merged]'s video in place of its default audio. */
data class AudioOption(val label: String, val url: String)

sealed interface PlaySource {
    /** Short description for error messages, e.g. "HLS" or "1080p". */
    val label: String

    /** Adaptive stream: YouTube picks the quality to suit the connection. */
    data class Hls(val url: String) : PlaySource {
        override val label get() = "HLS"
    }

    data class Dash(val url: String) : PlaySource {
        override val label get() = "DASH"
    }

    /** Separate video and audio files, played together. */
    data class Merged(val videoUrl: String, val audioUrl: String, override val label: String) : PlaySource

    /** One file with both video and audio (YouTube offers these only at low resolutions). */
    data class Progressive(val url: String, override val label: String) : PlaySource
}
