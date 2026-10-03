package com.tubetv.app.data.youtube

import com.tubetv.app.data.model.ChannelSummary
import com.tubetv.app.data.model.PlaySource
import com.tubetv.app.data.model.VideoSummary
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

/** Turns NewPipeExtractor's types into the app's models. Kept free of network calls so it can be unit tested. */
internal object Conversions {

    /** The tallest video the app asks for when it has to pick one file; TVs decode 1080p everywhere. */
    const val MAX_HEIGHT = 1080

    /** The image closest to [targetHeight] pixels tall, or the last (usually largest) when sizes are unknown. */
    fun List<Image>?.pick(targetHeight: Int): String? {
        if (this.isNullOrEmpty()) return null
        val sized = filter { it.height > 0 }
        val best = sized.minByOrNull { abs(it.height - targetHeight) } ?: last()
        return best.url.ifBlank { null }
    }

    fun StreamInfoItem.toSummary(fallbackChannel: ChannelSummary? = null) = VideoSummary(
        url = url,
        title = name.orEmpty(),
        thumbnailUrl = thumbnails.pick(360),
        channelName = uploaderName?.ifBlank { null } ?: fallbackChannel?.name,
        channelUrl = uploaderUrl?.ifBlank { null }?.let(::channelHome) ?: fallbackChannel?.url,
        durationSec = duration,
        viewCount = viewCount,
        uploaded = textualUploadDate?.ifBlank { null },
        uploadedAtMs = runCatching { uploadDate?.instant?.toEpochMilli() }.getOrNull(),
        isLive = streamType.isLive(),
    )

    fun ChannelInfoItem.toSummary() = ChannelSummary(
        url = url,
        name = name.orEmpty(),
        avatarUrl = thumbnails.pick(176)?.let(::absolute),
        subscriberCount = subscriberCount,
    )

    fun StreamType?.isLive() = this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM

    /** Videos listed on a channel's tab name the tab as their channel, e.g. ".../videos"; this is the channel itself. */
    fun channelHome(url: String) = url.replace(Regex("/(videos|shorts|streams|featured)/?$"), "")

    /** YouTube sometimes gives avatar URLs without a scheme. */
    fun absolute(url: String) = if (url.startsWith("//")) "https:$url" else url

    /**
     * Ways to play a video, best first: the HLS manifest (adaptive, the most reliable without
     * a signed-in session), DASH, then a separate video and audio file, then a single file.
     */
    fun playSources(
        hlsUrl: String?,
        dashUrl: String?,
        videoOnly: List<VideoStream>,
        audio: List<AudioStream>,
        muxed: List<VideoStream>,
    ): List<PlaySource> {
        val out = mutableListOf<PlaySource>()
        if (!hlsUrl.isNullOrBlank()) out += PlaySource.Hls(hlsUrl)
        if (!dashUrl.isNullOrBlank()) out += PlaySource.Dash(dashUrl)
        val video = videoOnly.asSequence()
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height in 1..MAX_HEIGHT }
            .sortedWith(
                compareByDescending<VideoStream> { it.height }
                    // H.264 in MP4 decodes in hardware on every TV; VP9 on most.
                    .thenBy { if (it.format == MediaFormat.MPEG_4) 0 else 1 }
                    .thenByDescending { it.fps },
            )
            .firstOrNull()
        val sound = audio.asSequence()
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            // Dubbed and described tracks exist on some videos; the original comes first.
            .sortedWith(
                compareBy<AudioStream> { if (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL) 0 else 1 }
                    .thenBy { if (it.format == MediaFormat.M4A) 0 else 1 }
                    .thenByDescending { it.averageBitrate },
            )
            .firstOrNull()
        if (video != null && sound != null) {
            out += PlaySource.Merged(video.content, sound.content, "${video.height}p")
        }
        muxed.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.height }
            ?.let { out += PlaySource.Progressive(it.content, "${it.height}p") }
        return out
    }
}
