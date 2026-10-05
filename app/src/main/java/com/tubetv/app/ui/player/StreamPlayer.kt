package com.tubetv.app.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.tubetv.app.data.model.PlaySource
import okhttp3.OkHttpClient

/** How the app turns a [PlaySource] into something ExoPlayer can play. */
@OptIn(UnstableApi::class)
object StreamPlayer {

    /**
     * A player that starts in high quality and never makes a quality switch the TV's decoder can't
     * do in place. Such a switch restarts the decoder, which shows black for a few seconds.
     */
    fun create(context: Context, http: OkHttpClient): ExoPlayer {
        val tracks = DefaultTrackSelector(
            context,
            DefaultTrackSelector.Parameters.Builder(context)
                .setAllowVideoNonSeamlessAdaptiveness(false)
                .setAllowVideoMixedMimeTypeAdaptiveness(false)
                .setAllowVideoMixedDecoderSupportAdaptiveness(false)
                .build(),
        )
        // The default first guess at the connection speed picks a low quality and steps up a few seconds in.
        val bandwidth = DefaultBandwidthMeter.Builder(context).setInitialBitrateEstimate(INITIAL_BITRATE).build()
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(OkHttpDataSource.Factory(http)))
            .setTrackSelector(tracks)
            .setBandwidthMeter(bandwidth)
            .build()
    }

    /** Enough for 1080p on YouTube; a slower line still steps down, without a black screen. */
    private const val INITIAL_BITRATE = 10_000_000L

    fun mediaSource(context: Context, http: OkHttpClient, source: PlaySource, title: String?): MediaSource {
        val data = OkHttpDataSource.Factory(http)
        val metadata = MediaMetadata.Builder().setTitle(title).build()
        fun item(url: String, mime: String? = null) =
            MediaItem.Builder().setUri(url).setMimeType(mime).setMediaMetadata(metadata).build()
        val factory = DefaultMediaSourceFactory(context).setDataSourceFactory(data)
        return when (source) {
            is PlaySource.Hls -> factory.createMediaSource(item(source.url, MimeTypes.APPLICATION_M3U8))
            is PlaySource.Dash -> factory.createMediaSource(item(source.url, MimeTypes.APPLICATION_MPD))
            is PlaySource.Progressive -> ProgressiveMediaSource.Factory(data).createMediaSource(item(source.url))
            is PlaySource.Merged -> MergingMediaSource(
                ProgressiveMediaSource.Factory(data).createMediaSource(item(source.videoUrl)),
                ProgressiveMediaSource.Factory(data).createMediaSource(item(source.audioUrl)),
            )
        }
    }

    fun httpStatus(e: Throwable): Int? = generateSequence(e) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode

    /** The error with its cause chain, so a TV message says more than "Source error". */
    fun describe(e: Throwable): String = generateSequence(e) { it.cause }.take(4)
        .joinToString(" ← ") { t ->
            val code = (t as? PlaybackException)?.errorCodeName?.let { "[$it] " }.orEmpty()
            "$code${t.javaClass.simpleName}: ${t.message}"
        }
}
